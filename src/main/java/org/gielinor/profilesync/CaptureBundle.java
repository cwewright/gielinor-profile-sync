package org.gielinor.profilesync;

import com.google.gson.Gson;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

final class CaptureBundle
{
	private static final double MIN_GOOD_HEIGHT_RATIO = 0.16;
	private static final double MIN_USABLE_HEIGHT_RATIO = 0.10;

	private CaptureBundle()
	{
	}

	static Rectangle buildSafeCaptureCrop(
		int viewportX,
		int viewportY,
		int viewportWidth,
		int viewportHeight,
		int canvasWidth,
		int canvasHeight,
		boolean resized
	)
	{
		Rectangle canvas = new Rectangle(0, 0, Math.max(1, canvasWidth), Math.max(1, canvasHeight));
		Rectangle viewport = viewportWidth > 0 && viewportHeight > 0
			? new Rectangle(viewportX, viewportY, viewportWidth, viewportHeight).intersection(canvas)
			: canvas;
		if (viewport.width <= 0 || viewport.height <= 0)
		{
			viewport = canvas;
		}
		if (!resized)
		{
			return viewport;
		}

		// In resizable mode, chat, minimap and action tabs overlay the scene. Keeping the
		// central 64% x 78% excludes the standard UI while retaining useful scenery.
		Rectangle centralScene = new Rectangle(
			viewport.x + (int) Math.round(viewport.width * 0.18),
			viewport.y + (int) Math.round(viewport.height * 0.04),
			Math.max(1, (int) Math.round(viewport.width * 0.64)),
			Math.max(1, (int) Math.round(viewport.height * 0.78))
		);
		return centralScene.intersection(viewport).intersection(canvas);
	}

	static Map<String, Object> buildFraming(Rectangle playerBounds, int imageWidth, int imageHeight)
	{
		Map<String, Object> framing = new LinkedHashMap<>();
		List<String> suggestions = new ArrayList<>();
		boolean measurable = playerBounds != null && playerBounds.width > 0 && playerBounds.height > 0
			&& imageWidth > 0 && imageHeight > 0;
		framing.put("measurable", measurable);

		if (!measurable)
		{
			framing.put("quality", "missing");
			suggestions.add("Move the character into the framing guide before capturing.");
			framing.put("suggestions", suggestions);
			return framing;
		}

		double x = playerBounds.getX() / imageWidth;
		double y = playerBounds.getY() / imageHeight;
		double width = playerBounds.getWidth() / imageWidth;
		double height = playerBounds.getHeight() / imageHeight;
		double centerX = playerBounds.getCenterX() / imageWidth;
		double centerY = playerBounds.getCenterY() / imageHeight;
		boolean fullyVisible = playerBounds.x >= 0 && playerBounds.y >= 0
			&& playerBounds.x + playerBounds.width <= imageWidth
			&& playerBounds.y + playerBounds.height <= imageHeight;
		boolean centered = centerX >= 0.20 && centerX <= 0.80 && centerY >= 0.15 && centerY <= 0.88;
		boolean goodScale = height >= MIN_GOOD_HEIGHT_RATIO;

		Map<String, Object> pixels = new LinkedHashMap<>();
		pixels.put("x", playerBounds.x);
		pixels.put("y", playerBounds.y);
		pixels.put("width", playerBounds.width);
		pixels.put("height", playerBounds.height);
		framing.put("pixels", pixels);

		Map<String, Object> normalized = new LinkedHashMap<>();
		normalized.put("x", round(x));
		normalized.put("y", round(y));
		normalized.put("width", round(width));
		normalized.put("height", round(height));
		normalized.put("centerX", round(centerX));
		normalized.put("centerY", round(centerY));
		framing.put("normalized", normalized);
		framing.put("fullyVisible", fullyVisible);
		framing.put("centered", centered);
		framing.put("goodScale", goodScale);

		if (!fullyVisible)
		{
			suggestions.add("Keep the entire character inside the framing guide.");
		}
		if (!centered)
		{
			suggestions.add("Center the character inside the framing guide.");
		}
		if (!goodScale)
		{
			suggestions.add("Zoom in until the character fills more of the frame.");
		}

		String quality = fullyVisible && centered && goodScale
			? "good"
			: fullyVisible && height >= MIN_USABLE_HEIGHT_RATIO ? "usable" : "poor";
		framing.put("quality", quality);
		framing.put("suggestions", suggestions);
		return framing;
	}

	static void write(
		Path pendingDirectory,
		String captureId,
		Image image,
		Rectangle captureCrop,
		Map<String, Object> metadata,
		Gson gson
	)
		throws IOException
	{
		Files.createDirectories(pendingDirectory);

		BufferedImage bufferedImage = cropToBounds(toBufferedImage(image), captureCrop);
		byte[] pngBytes = encodePng(bufferedImage);
		String imageFileName = captureId + ".png";
		String metadataFileName = captureId + ".json";

		Map<String, Object> imageMetadata = new LinkedHashMap<>();
		imageMetadata.put("fileName", imageFileName);
		imageMetadata.put("contentType", "image/png");
		imageMetadata.put("width", bufferedImage.getWidth());
		imageMetadata.put("height", bufferedImage.getHeight());
		imageMetadata.put("byteLength", pngBytes.length);
		imageMetadata.put("sha256", sha256Hex(pngBytes));
		imageMetadata.put("differenceHash", differenceHash(bufferedImage));
		metadata.put("image", imageMetadata);

		Path imagePath = pendingDirectory.resolve(imageFileName);
		Path metadataPath = pendingDirectory.resolve(metadataFileName);
		Path imageTemp = pendingDirectory.resolve(imageFileName + ".tmp");
		Path metadataTemp = pendingDirectory.resolve(metadataFileName + ".tmp");

		Files.write(imageTemp, pngBytes);
		Files.write(metadataTemp, gson.toJson(metadata).getBytes(StandardCharsets.UTF_8));
		moveIntoPlace(imageTemp, imagePath);
		// The JSON manifest moves last and acts as the ready marker for a future sync tool.
		moveIntoPlace(metadataTemp, metadataPath);
	}

	static void prune(Path pendingDirectory, int retention) throws IOException
	{
		if (!Files.isDirectory(pendingDirectory))
		{
			return;
		}

		int limit = Math.max(1, retention);
		List<Path> manifests;
		try (Stream<Path> paths = Files.list(pendingDirectory))
		{
			manifests = paths
				.filter(path -> path.getFileName().toString().endsWith(".json"))
				.sorted(Comparator.comparingLong(CaptureBundle::lastModified).reversed())
				.collect(Collectors.toList());
		}

		for (int index = limit; index < manifests.size(); index++)
		{
			Path manifest = manifests.get(index);
			String fileName = manifest.getFileName().toString();
			String stem = fileName.substring(0, fileName.length() - ".json".length());
			Files.deleteIfExists(manifest);
			Files.deleteIfExists(pendingDirectory.resolve(stem + ".png"));
		}
	}

	@SuppressWarnings("unchecked")
	static void pruneDiverse(Path pendingDirectory, int retention, Gson gson) throws IOException
	{
		if (!Files.isDirectory(pendingDirectory))
		{
			return;
		}

		List<Path> manifests;
		try (Stream<Path> paths = Files.list(pendingDirectory))
		{
			manifests = paths
				.filter(path -> path.getFileName().toString().endsWith(".json"))
				.sorted(Comparator.comparingLong(CaptureBundle::lastModified).reversed())
				.collect(Collectors.toList());
		}

		Set<Path> protectedManifests = new LinkedHashSet<>();
		Set<String> protectedSkills = new LinkedHashSet<>();
		int protectedBankScenes = 0;
		for (Path manifest : manifests)
		{
			try
			{
				Map<String, Object> parsed = gson.fromJson(Files.readString(manifest), Map.class);
				Object rawContext = parsed == null ? null : parsed.get("context");
				if (!(rawContext instanceof Map))
				{
					continue;
				}
				Map<String, Object> context = (Map<String, Object>) rawContext;
				String sceneTag = context.get("sceneTag") instanceof String ? (String) context.get("sceneTag") : null;
				String skillTag = context.get("skillTag") instanceof String ? (String) context.get("skillTag") : null;
				if ("bank".equals(sceneTag) && protectedBankScenes < 2)
				{
					protectedManifests.add(manifest);
					protectedBankScenes++;
				}
				if (skillTag != null && protectedSkills.add(skillTag))
				{
					protectedManifests.add(manifest);
				}
			}
			catch (IOException | RuntimeException ignored)
			{
				// A malformed manifest is never selected as a diversity keeper, but the
				// normal newest-first retention pass can still keep it for inspection.
			}
		}

		int limit = Math.max(Math.max(1, retention), protectedManifests.size());
		Set<Path> keep = new LinkedHashSet<>(protectedManifests);
		for (Path manifest : manifests)
		{
			if (keep.size() >= limit)
			{
				break;
			}
			keep.add(manifest);
		}
		for (Path manifest : manifests)
		{
			if (keep.contains(manifest))
			{
				continue;
			}
			String fileName = manifest.getFileName().toString();
			String stem = fileName.substring(0, fileName.length() - ".json".length());
			Files.deleteIfExists(manifest);
			Files.deleteIfExists(pendingDirectory.resolve(stem + ".png"));
		}
	}

	@SuppressWarnings("unchecked")
	static int countSceneTag(Path captureRoot, String expectedSceneTag, Gson gson) throws IOException
	{
		if (!Files.isDirectory(captureRoot))
		{
			return 0;
		}
		int count = 0;
		try (Stream<Path> paths = Files.walk(captureRoot, 2))
		{
			for (Path manifest : paths.filter(path -> path.getFileName().toString().endsWith(".json")).collect(Collectors.toList()))
			{
				try
				{
					Map<String, Object> parsed = gson.fromJson(Files.readString(manifest), Map.class);
					Object rawContext = parsed == null ? null : parsed.get("context");
					if (rawContext instanceof Map && expectedSceneTag.equals(((Map<String, Object>) rawContext).get("sceneTag")))
					{
						count++;
					}
				}
				catch (IOException | RuntimeException ignored)
				{
					// Ignore incomplete or user-edited manifests when warming the counter.
				}
			}
		}
		return count;
	}

	static String differenceHash(BufferedImage source)
	{
		BufferedImage scaled = new BufferedImage(9, 8, BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D graphics = scaled.createGraphics();
		try
		{
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.drawImage(source, 0, 0, 9, 8, null);
		}
		finally
		{
			graphics.dispose();
		}

		long hash = 0L;
		for (int y = 0; y < 8; y++)
		{
			for (int x = 0; x < 8; x++)
			{
				hash <<= 1;
				int left = scaled.getRaster().getSample(x, y, 0);
				int right = scaled.getRaster().getSample(x + 1, y, 0);
				if (left > right)
				{
					hash |= 1L;
				}
			}
		}
		return String.format("%016x", hash);
	}

	private static BufferedImage toBufferedImage(Image image) throws IOException
	{
		if (image instanceof BufferedImage)
		{
			return (BufferedImage) image;
		}
		int width = image.getWidth(null);
		int height = image.getHeight(null);
		if (width <= 0 || height <= 0)
		{
			throw new IOException("RuneLite returned an image with invalid dimensions.");
		}
		BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = bufferedImage.createGraphics();
		try
		{
			graphics.drawImage(image, 0, 0, null);
		}
		finally
		{
			graphics.dispose();
		}
		return bufferedImage;
	}

	private static BufferedImage cropToBounds(BufferedImage source, Rectangle requestedBounds)
	{
		Rectangle imageBounds = new Rectangle(0, 0, source.getWidth(), source.getHeight());
		Rectangle crop = requestedBounds == null ? imageBounds : imageBounds.intersection(requestedBounds);
		if (crop.width <= 0 || crop.height <= 0 || crop.equals(imageBounds))
		{
			return source;
		}

		BufferedImage cropped = new BufferedImage(crop.width, crop.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = cropped.createGraphics();
		try
		{
			graphics.drawImage(source, -crop.x, -crop.y, null);
		}
		finally
		{
			graphics.dispose();
		}
		return cropped;
	}

	private static byte[] encodePng(BufferedImage image) throws IOException
	{
		try (ByteArrayOutputStream output = new ByteArrayOutputStream())
		{
			if (!ImageIO.write(image, "png", output))
			{
				throw new IOException("No PNG image writer is available.");
			}
			return output.toByteArray();
		}
	}

	private static String sha256Hex(byte[] bytes) throws IOException
	{
		try
		{
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			StringBuilder result = new StringBuilder(64);
			for (byte value : digest.digest(bytes))
			{
				result.append(String.format("%02x", value & 0xff));
			}
			return result.toString();
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IOException("SHA-256 is unavailable.", e);
		}
	}

	private static void moveIntoPlace(Path source, Path destination) throws IOException
	{
		try
		{
			Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (AtomicMoveNotSupportedException ignored)
		{
			Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static long lastModified(Path path)
	{
		try
		{
			return Files.getLastModifiedTime(path).toMillis();
		}
		catch (IOException ignored)
		{
			return Long.MIN_VALUE;
		}
	}

	private static double round(double value)
	{
		return Math.round(value * 10000.0) / 10000.0;
	}
}
