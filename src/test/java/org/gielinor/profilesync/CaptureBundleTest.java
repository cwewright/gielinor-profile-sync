package org.gielinor.profilesync;

import com.google.gson.Gson;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CaptureBundleTest
{
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void buildsCentralResizableCropThatExcludesStandardUiEdges()
	{
		Rectangle crop = CaptureBundle.buildSafeCaptureCrop(0, 0, 1600, 1000, 1600, 1000, true);

		assertEquals(new Rectangle(288, 40, 1024, 780), crop);
		assertTrue(crop.getMaxX() < 1400);
		assertTrue(crop.getMaxY() < 850);
	}

	@Test
	public void preservesTheWorldViewportInFixedMode()
	{
		Rectangle crop = CaptureBundle.buildSafeCaptureCrop(4, 4, 512, 334, 765, 503, false);

		assertEquals(new Rectangle(4, 4, 512, 334), crop);
	}

	@Test
	public void ratesCenteredVisibleCharacterAsGood()
	{
		Map<String, Object> framing = CaptureBundle.buildFraming(new Rectangle(400, 150, 200, 300), 1000, 800);

		assertEquals("good", framing.get("quality"));
		assertEquals(Boolean.TRUE, framing.get("fullyVisible"));
		assertEquals(Boolean.TRUE, framing.get("centered"));
		assertEquals(Boolean.TRUE, framing.get("goodScale"));
	}

	@Test
	public void rejectsClippedOrMissingCharacterFraming()
	{
		Map<String, Object> clipped = CaptureBundle.buildFraming(new Rectangle(-20, 40, 80, 90), 800, 600);
		Map<String, Object> missing = CaptureBundle.buildFraming(null, 800, 600);

		assertEquals("poor", clipped.get("quality"));
		assertEquals(Boolean.FALSE, clipped.get("fullyVisible"));
		assertEquals("missing", missing.get("quality"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void writesImageBeforeReadyManifestWithIntegrityMetadata() throws Exception
	{
		Path pending = temporaryFolder.newFolder("pending").toPath();
		BufferedImage image = testImage();
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("captureId", "test-capture");

		CaptureBundle.write(pending, "test-capture", image, new Rectangle(5, 4, 70, 60), metadata, new Gson());

		Path imageFile = pending.resolve("test-capture.png");
		Path manifestFile = pending.resolve("test-capture.json");
		assertTrue(Files.isRegularFile(imageFile));
		assertTrue(Files.isRegularFile(manifestFile));
		assertFalse(Files.exists(pending.resolve("test-capture.png.tmp")));
		assertFalse(Files.exists(pending.resolve("test-capture.json.tmp")));

		Map<String, Object> parsed = new Gson().fromJson(Files.readString(manifestFile), Map.class);
		Map<String, Object> imageMetadata = (Map<String, Object>) parsed.get("image");
		assertEquals("test-capture.png", imageMetadata.get("fileName"));
		assertEquals("image/png", imageMetadata.get("contentType"));
		assertEquals(70.0, imageMetadata.get("width"));
		assertEquals(60.0, imageMetadata.get("height"));
		assertEquals(64, ((String) imageMetadata.get("sha256")).length());
		assertEquals(16, ((String) imageMetadata.get("differenceHash")).length());
	}

	@Test
	public void prunesOldestCompletedPairsToRetentionLimit() throws Exception
	{
		Path pending = temporaryFolder.newFolder("retention").toPath();
		BufferedImage image = testImage();
		for (int index = 0; index < 3; index++)
		{
			String id = "capture-" + index;
			Map<String, Object> metadata = new LinkedHashMap<>();
			metadata.put("captureId", id);
			CaptureBundle.write(pending, id, image, null, metadata, new Gson());
			Files.setLastModifiedTime(pending.resolve(id + ".json"), FileTime.fromMillis(1000L + index));
		}

		CaptureBundle.prune(pending, 2);

		assertFalse(Files.exists(pending.resolve("capture-0.json")));
		assertFalse(Files.exists(pending.resolve("capture-0.png")));
		assertTrue(Files.exists(pending.resolve("capture-1.json")));
		assertTrue(Files.exists(pending.resolve("capture-2.json")));
	}

	private BufferedImage testImage()
	{
		BufferedImage image = new BufferedImage(90, 80, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				image.setRGB(x, y, new Color(x * 2, y * 2, 80).getRGB());
			}
		}
		return image;
	}
}
