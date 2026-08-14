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
	@SuppressWarnings("unchecked")
	public void keepsLogicalCoordinatesAtOneHundredPercentScaling()
	{
		Map<String, Object> metadata = metadataWithCamera();
		Rectangle crop = CaptureBundle.prepareFrameMetadata(
			metadata,
			new Rectangle(100, 80, 800, 600),
			new Rectangle(440, 250, 120, 240),
			1000,
			800,
			1000,
			800
		);

		assertEquals(new Rectangle(100, 80, 800, 600), crop);
		Map<String, Object> camera = (Map<String, Object>) metadata.get("camera");
		assertEquals(512, camera.get("yaw"));
		assertEquals(1000, camera.get("canvasWidth"));
		assertEquals(800, camera.get("canvasHeight"));
		assertEquals(800, camera.get("captureWidth"));
		assertEquals(600, camera.get("captureHeight"));
		assertEquals(1.0, camera.get("pixelScaleX"));
		assertEquals(1.0, camera.get("pixelScaleY"));

		Map<String, Object> framing = (Map<String, Object>) metadata.get("framing");
		Map<String, Object> pixels = (Map<String, Object>) framing.get("pixels");
		assertEquals(340, pixels.get("x"));
		assertEquals(170, pixels.get("y"));
		assertEquals(120, pixels.get("width"));
		assertEquals(240, pixels.get("height"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void scalesCropAndMetadataToOneHundredFiftyPercentPixels() throws Exception
	{
		Path pending = temporaryFolder.newFolder("hidpi-150").toPath();
		Map<String, Object> metadata = metadataWithCamera();
		Rectangle crop = CaptureBundle.prepareFrameMetadata(
			metadata,
			new Rectangle(100, 80, 800, 600),
			new Rectangle(440, 250, 120, 240),
			1000,
			800,
			1500,
			1200
		);

		assertEquals(new Rectangle(150, 120, 1200, 900), crop);
		CaptureBundle.write(pending, "hidpi-capture", new BufferedImage(1500, 1200, BufferedImage.TYPE_INT_RGB), crop, metadata, new Gson());

		Map<String, Object> parsed = new Gson().fromJson(
			Files.readString(pending.resolve("hidpi-capture.json")),
			Map.class
		);
		Map<String, Object> image = (Map<String, Object>) parsed.get("image");
		Map<String, Object> camera = (Map<String, Object>) parsed.get("camera");
		Map<String, Object> framing = (Map<String, Object>) parsed.get("framing");
		Map<String, Object> pixels = (Map<String, Object>) framing.get("pixels");
		assertEquals(1200.0, image.get("width"));
		assertEquals(900.0, image.get("height"));
		assertEquals(image.get("width"), camera.get("captureWidth"));
		assertEquals(image.get("height"), camera.get("captureHeight"));
		assertEquals(1.5, camera.get("pixelScaleX"));
		assertEquals(1.5, camera.get("pixelScaleY"));
		assertEquals(510.0, pixels.get("x"));
		assertEquals(255.0, pixels.get("y"));
		assertEquals(180.0, pixels.get("width"));
		assertEquals(360.0, pixels.get("height"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void scalesHorizontalAndVerticalEdgesIndependently()
	{
		Map<String, Object> metadata = metadataWithCamera();
		Rectangle crop = CaptureBundle.prepareFrameMetadata(
			metadata,
			new Rectangle(101, 81, 799, 599),
			new Rectangle(441, 251, 119, 239),
			1000,
			800,
			2000,
			1200
		);

		assertEquals(new Rectangle(202, 122, 1598, 898), crop);
		Map<String, Object> camera = (Map<String, Object>) metadata.get("camera");
		assertEquals(2.0, camera.get("pixelScaleX"));
		assertEquals(1.5, camera.get("pixelScaleY"));
		Map<String, Object> framing = (Map<String, Object>) metadata.get("framing");
		Map<String, Object> pixels = (Map<String, Object>) framing.get("pixels");
		assertEquals(680, pixels.get("x"));
		assertEquals(255, pixels.get("y"));
		assertEquals(238, pixels.get("width"));
		assertEquals(358, pixels.get("height"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void clampsCropButPreservesOutOfFramePlayerBounds()
	{
		Map<String, Object> metadata = metadataWithCamera();
		Rectangle crop = CaptureBundle.prepareFrameMetadata(
			metadata,
			new Rectangle(-100, -50, 1200, 900),
			new Rectangle(-20, 100, 1040, 300),
			1000,
			800,
			1500,
			1200
		);

		assertEquals(new Rectangle(0, 0, 1500, 1200), crop);
		Map<String, Object> framing = (Map<String, Object>) metadata.get("framing");
		Map<String, Object> pixels = (Map<String, Object>) framing.get("pixels");
		assertEquals(-30, pixels.get("x"));
		assertEquals(150, pixels.get("y"));
		assertEquals(1560, pixels.get("width"));
		assertEquals(450, pixels.get("height"));
		assertEquals(Boolean.FALSE, framing.get("fullyVisible"));
		assertEquals("poor", framing.get("quality"));
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

	@Test
	public void diversePruningPreservesTwoBanksAndNewestCapturePerSkill() throws Exception
	{
		Path captureRoot = temporaryFolder.newFolder("diverse-root").toPath();
		Path pending = Files.createDirectories(captureRoot.resolve("pending"));
		BufferedImage image = testImage();
		writeTaggedCapture(pending, image, "bank-old", "bank", null, 1_000L);
		writeTaggedCapture(pending, image, "attack-old", "adventure", "Attack", 2_000L);
		writeTaggedCapture(pending, image, "bank-new", "bank", null, 3_000L);
		writeTaggedCapture(pending, image, "fishing", "adventure", "Fishing", 4_000L);
		writeTaggedCapture(pending, image, "attack-new", "adventure", "Attack", 5_000L);
		writeTaggedCapture(pending, image, "untagged-newest", "adventure", null, 6_000L);

		Gson gson = new Gson();
		CaptureBundle.pruneDiverse(pending, 4, gson);

		assertTrue(Files.exists(pending.resolve("bank-old.json")));
		assertTrue(Files.exists(pending.resolve("bank-new.json")));
		assertTrue(Files.exists(pending.resolve("fishing.json")));
		assertTrue(Files.exists(pending.resolve("attack-new.json")));
		assertFalse(Files.exists(pending.resolve("attack-old.json")));
		assertFalse(Files.exists(pending.resolve("untagged-newest.json")));
		assertEquals(2, CaptureBundle.countSceneTag(captureRoot, "bank", gson));
	}

	private void writeTaggedCapture(
		Path pending,
		BufferedImage image,
		String id,
		String sceneTag,
		String skillTag,
		long modifiedAt
	) throws Exception
	{
		Map<String, Object> context = new LinkedHashMap<>();
		context.put("sceneTag", sceneTag);
		if (skillTag != null)
		{
			context.put("skillTag", skillTag);
		}
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("captureId", id);
		metadata.put("context", context);
		CaptureBundle.write(pending, id, image, null, metadata, new Gson());
		Files.setLastModifiedTime(pending.resolve(id + ".json"), FileTime.fromMillis(modifiedAt));
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

	private Map<String, Object> metadataWithCamera()
	{
		Map<String, Object> camera = new LinkedHashMap<>();
		camera.put("yaw", 512);
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("camera", camera);
		return metadata;
	}
}
