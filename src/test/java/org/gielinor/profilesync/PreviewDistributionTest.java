package org.gielinor.profilesync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PreviewDistributionTest
{
	@Test
	public void buildsAOneJarVerifiedDownloadBootstrap() throws Exception
	{
		Path zipPath = Paths.get(requiredProperty("gielinor.preview.bootstrap.zip"));
		String root = requiredProperty("gielinor.preview.bootstrap.root") + "/";
		String applicationJarName = requiredProperty("gielinor.preview.jar");
		Set<String> expectedFiles = new HashSet<>(Arrays.asList(
			root + "Preview/lib/" + applicationJarName,
			root + "Preview/PREVIEW-MANIFEST.json",
			root + "Preview/BOOTSTRAP-NOTICES.txt",
			root + "Preview/legal/GIELINOR-PROFILE-SYNC-LICENSE.txt"
		));

		try (ZipFile zip = new ZipFile(zipPath.toFile()))
		{
			Set<String> files = zip.stream()
				.filter(entry -> !entry.isDirectory())
				.map(ZipEntry::getName)
				.collect(Collectors.toSet());
			assertEquals(expectedFiles, files);
			assertEquals(1, files.stream().filter(name -> name.endsWith(".jar")).count());

			JsonObject manifest = new JsonParser()
				.parse(readUtf8(zip, root + "Preview/PREVIEW-MANIFEST.json"))
				.getAsJsonObject();
			assertEquals("verified-download", manifest.get("distributionMode").getAsString());
			assertEquals(applicationJarName, manifest.getAsJsonObject("applicationJar").get("file").getAsString());

			JsonArray dependencies = manifest.getAsJsonArray("dependencies");
			assertTrue(dependencies.size() > 20);
			Set<String> dependencyFiles = new HashSet<>();
			for (JsonElement element : dependencies)
			{
				JsonObject dependency = element.getAsJsonObject();
				String[] coordinate = dependency.get("coordinate").getAsString().split(":", -1);
				assertEquals(3, coordinate.length);
				String file = dependency.get("file").getAsString();
				String repository = coordinate[0].startsWith("net.runelite")
					? "https://repo.runelite.net"
					: "https://repo1.maven.org/maven2";
				String expectedUrl = repository + "/" + coordinate[0].replace('.', '/') + "/"
					+ coordinate[1] + "/" + coordinate[2] + "/" + file;
				assertEquals(expectedUrl, dependency.get("downloadUrl").getAsString());
				assertTrue(dependency.get("sha256").getAsString().matches("[0-9a-f]{64}"));
				assertTrue(dependency.get("sizeBytes").getAsLong() > 0);
				assertTrue(dependencyFiles.add(file));
				assertFalse(files.contains(root + "Preview/lib/" + file));
			}
		}
	}

	@Test
	public void buildsAThinPinnedRuntimeDistribution() throws Exception
	{
		Path zipPath = Paths.get(requiredProperty("gielinor.preview.zip"));
		String root = requiredProperty("gielinor.preview.root") + "/";
		String applicationJarName = requiredProperty("gielinor.preview.jar");
		String libRoot = root + "Preview/lib/";

		try (ZipFile zip = new ZipFile(zipPath.toFile()))
		{
			Set<String> entries = zip.stream().map(ZipEntry::getName).collect(Collectors.toSet());
			assertTrue(entries.contains(root + "Preview/PREVIEW-MANIFEST.json"));
			assertTrue(entries.contains(root + "Preview/THIRD-PARTY-NOTICES.txt"));
			assertTrue(entries.contains(root + "Preview/legal/GIELINOR-PROFILE-SYNC-LICENSE.txt"));
			assertTrue(entries.contains(root + "Launch-GielinorProfileSyncPreview.ps1"));

			Set<String> runtimeJars = entries.stream()
				.filter(name -> name.startsWith(libRoot) && name.endsWith(".jar"))
				.map(name -> name.substring(libRoot.length()))
				.collect(Collectors.toSet());
			assertTrue(runtimeJars.size() > 20);
			assertTrue(runtimeJars.contains(applicationJarName));
			assertTrue(runtimeJars.contains("client-" + requiredProperty("gielinor.preview.runelite") + ".jar"));
			assertFalse(runtimeJars.stream().anyMatch(name -> name.toLowerCase().contains("junit")));
			assertFalse(runtimeJars.stream().anyMatch(name -> name.toLowerCase().contains("hamcrest")));
			assertFalse(runtimeJars.stream().anyMatch(name -> name.contains("natives-linux")));
			assertFalse(runtimeJars.stream().anyMatch(name -> name.contains("natives-macos")));
			assertFalse(runtimeJars.stream().anyMatch(name -> name.contains("windows-x86")));
			assertFalse(runtimeJars.stream().anyMatch(name -> name.contains("windows-arm64")));

			ZipEntry applicationJar = zip.getEntry(libRoot + applicationJarName);
			assertNotNull(applicationJar);
			try (InputStream input = zip.getInputStream(applicationJar);
				 JarInputStream jar = new JarInputStream(input))
			{
				Manifest manifest = jar.getManifest();
				assertNotNull(manifest);
				Attributes attributes = manifest.getMainAttributes();
				assertEquals("org.gielinor.profilesync.GielinorProfileSyncPreview", attributes.getValue("Main-Class"));
				assertEquals(requiredProperty("gielinor.preview.runelite"), attributes.getValue("RuneLite-Version"));
				assertEquals("11", attributes.getValue("Build-Java-Version"));

				Set<String> manifestClasspath = new HashSet<>(Arrays.asList(attributes.getValue("Class-Path").split(" ")));
				Set<String> dependencyJars = new HashSet<>(runtimeJars);
				dependencyJars.remove(applicationJarName);
				assertEquals(dependencyJars, manifestClasspath);

				Set<String> applicationClasses = new HashSet<>();
				JarEntry entry;
				while ((entry = jar.getNextJarEntry()) != null)
				{
					applicationClasses.add(entry.getName());
				}
				assertTrue(applicationClasses.contains("org/gielinor/profilesync/GielinorProfileSyncPreview.class"));
				assertTrue(applicationClasses.contains("org/gielinor/profilesync/GielinorProfileSyncPlugin.class"));
				assertFalse(applicationClasses.stream().anyMatch(name -> name.endsWith("Test.class")));
				assertFalse(applicationClasses.stream().anyMatch(name -> name.startsWith("org/junit/")));
				assertFalse(applicationClasses.stream().anyMatch(name -> name.startsWith("org/hamcrest/")));
				assertFalse(applicationClasses.stream().anyMatch(name -> name.startsWith("net/runelite/")));
				assertFalse(applicationClasses.contains("META-INF/services/net.runelite.client.plugins.Plugin"));
			}

			String previewManifest = readUtf8(zip, root + "Preview/PREVIEW-MANIFEST.json");
			assertTrue(previewManifest.contains("\"javaRuntimeMajor\": 11"));
			assertTrue(previewManifest.contains("\"runeLiteVersion\": \"" + requiredProperty("gielinor.preview.runelite") + "\""));
			assertTrue(previewManifest.contains("\"gielinor-profile-sync-preview\""));
			assertFalse(previewManifest.contains("junit:junit"));
			assertFalse(previewManifest.contains("org.hamcrest:"));
		}
	}

	private static String requiredProperty(String name)
	{
		String value = System.getProperty(name);
		assertNotNull("Missing test property " + name, value);
		return value;
	}

	private static String readUtf8(ZipFile zip, String name) throws IOException
	{
		ZipEntry entry = zip.getEntry(name);
		assertNotNull(entry);
		try (InputStream input = zip.getInputStream(entry))
		{
			byte[] buffer = new byte[8192];
			StringBuilder text = new StringBuilder();
			int count;
			while ((count = input.read(buffer)) != -1)
			{
				text.append(new String(buffer, 0, count, StandardCharsets.UTF_8));
			}
			return text.toString();
		}
	}
}
