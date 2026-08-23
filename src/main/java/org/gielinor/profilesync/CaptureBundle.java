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
	static final int MAX_STORED_CAPTURE_BYTES = 4 * 1024 * 1024;
	private static final double MIN_GOOD_HEIGHT_RATIO = 0.16;
	private static final double MIN_USABLE_HEIGHT_RATIO = 0.10;
	private static final int MAX_STORAGE_RESIZE_ATTEMPTS = 10;

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

	static Rectangle prepareFrameMetadata(
		Map<String, Object> metadata,
		Rectangle logicalCaptureCrop,
		Rectangle logicalPlayerBounds,
		int logicalCanvasWidth,
		int logicalCanvasHeight,
		int imageWidth,
		int imageHeight
	)
	{
		if (metadata == null)
		{
			throw new IllegalArgumentException("Capture metadata is required.");
		}

		Rectangle imageCaptureCrop = scaleCanvasRectangle(
			logicalCaptureCrop,
			logicalCanvasWidth,
			logicalCanvasHeight,
			imageWidth,
			imageHeight,
			true
		);
		Rectangle imagePlayerBounds = null;
		if (logicalPlayerBounds != null && logicalPlayerBounds.width > 0 && logicalPlayerBounds.height > 0)
		{
			imagePlayerBounds = scaleCanvasRectangle(
				logicalPlayerBounds,
				logicalCanvasWidth,
				logicalCanvasHeight,
				imageWidth,
				imageHeight,
				false
			);
			imagePlayerBounds.translate(-imageCaptureCrop.x, -imageCaptureCrop.y);
		}

		Map<String, Object> camera = copyStringMap(metadata.get("camera"));
		camera.put("logicalCanvasWidth", logicalCanvasWidth);
		camera.put("logicalCanvasHeight", logicalCanvasHeight);
		camera.put("logicalCaptureX", logicalCaptureCrop.x);
		camera.put("logicalCaptureY", logicalCaptureCrop.y);
		camera.put("logicalCaptureWidth", logicalCaptureCrop.width);
		camera.put("logicalCaptureHeight", logicalCaptureCrop.height);
		camera.put("canvasWidth", imageWidth);
		camera.put("canvasHeight", imageHeight);
		camera.put("captureX", imageCaptureCrop.x);
		camera.put("captureY", imageCaptureCrop.y);
		camera.put("captureWidth", imageCaptureCrop.width);
		camera.put("captureHeight", imageCaptureCrop.height);
		camera.put("pixelScaleX", round((double) imageWidth / logicalCanvasWidth));
		camera.put("pixelScaleY", round((double) imageHeight / logicalCanvasHeight));
		metadata.put("camera", camera);
		metadata.put("framing", buildFraming(imagePlayerBounds, imageCaptureCrop.width, imageCaptureCrop.height));
		return imageCaptureCrop;
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

		BufferedImage croppedImage = cropToBounds(toBufferedImage(image), captureCrop);
		EncodedCapture encodedCapture = encodeWithinStorageBudget(croppedImage);
		BufferedImage bufferedImage = encodedCapture.image;
		byte[] pngBytes = encodedCapture.pngBytes;
		updateStoredFrameMetadata(metadata, croppedImage.getWidth(), croppedImage.getHeight(), bufferedImage.getWidth(), bufferedImage.getHeight());
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

	private static EncodedCapture encodeWithinStorageBudget(BufferedImage source) throws IOException
	{
		BufferedImage current = source;
		byte[] pngBytes = encodePng(current);
		for (int attempt = 0; pngBytes.length > MAX_STORED_CAPTURE_BYTES && attempt < MAX_STORAGE_RESIZE_ATTEMPTS; attempt++)
		{
			double idealScale = Math.sqrt((double) MAX_STORED_CAPTURE_BYTES / pngBytes.length) * 0.94;
			double scale = Math.max(0.50, Math.min(0.90, idealScale));
			int width = Math.max(1, (int) Math.floor(current.getWidth() * scale));
			int height = Math.max(1, (int) Math.floor(current.getHeight() * scale));
			if (width == current.getWidth() && current.getWidth() > 1)
			{
				width--;
			}
			if (height == current.getHeight() && current.getHeight() > 1)
			{
				height--;
			}
			current = resizeImage(current, width, height);
			pngBytes = encodePng(current);
		}
		if (pngBytes.length > MAX_STORED_CAPTURE_BYTES)
		{
			throw new IOException("The character history capture could not be reduced to the private upload limit.");
		}
		return new EncodedCapture(current, pngBytes);
	}

	private static BufferedImage resizeImage(BufferedImage source, int width, int height)
	{
		int imageType = source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
		BufferedImage resized = new BufferedImage(width, height, imageType);
		Graphics2D graphics = resized.createGraphics();
		try
		{
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.drawImage(source, 0, 0, width, height, null);
		}
		finally
		{
			graphics.dispose();
		}
		return resized;
	}

	private static void updateStoredFrameMetadata(
		Map<String, Object> metadata,
		int sourceWidth,
		int sourceHeight,
		int storedWidth,
		int storedHeight
	)
	{
		Map<String, Object> camera = copyStringMap(metadata.get("camera"));
		camera.put("storedWidth", storedWidth);
		camera.put("storedHeight", storedHeight);
		camera.put("storageScaleX", round((double) storedWidth / sourceWidth));
		camera.put("storageScaleY", round((double) storedHeight / sourceHeight));
		metadata.put("camera", camera);

		if (sourceWidth == storedWidth && sourceHeight == storedHeight)
		{
			return;
		}
		Map<String, Object> framing = copyStringMap(metadata.get("framing"));
		Map<String, Object> pixels = copyStringMap(framing.get("pixels"));
		if (!pixels.isEmpty())
		{
			scalePixelField(pixels, "x", storedWidth, sourceWidth);
			scalePixelField(pixels, "width", storedWidth, sourceWidth);
			scalePixelField(pixels, "y", storedHeight, sourceHeight);
			scalePixelField(pixels, "height", storedHeight, sourceHeight);
			framing.put("pixels", pixels);
			metadata.put("framing", framing);
		}
	}

	private static void scalePixelField(Map<String, Object> pixels, String field, int storedExtent, int sourceExtent)
	{
		Object value = pixels.get(field);
		if (value instanceof Number)
		{
			pixels.put(field, (int) Math.round(((Number) value).doubleValue() * storedExtent / sourceExtent));
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

	private static Rectangle scaleCanvasRectangle(
		Rectangle logicalBounds,
		int logicalCanvasWidth,
		int logicalCanvasHeight,
		int imageWidth,
		int imageHeight,
		boolean clampToImage
	)
	{
		if (logicalCanvasWidth <= 0 || logicalCanvasHeight <= 0 || imageWidth <= 0 || imageHeight <= 0)
		{
			throw new IllegalArgumentException("Canvas and screenshot dimensions must be positive.");
		}
		if (logicalBounds == null || logicalBounds.width <= 0 || logicalBounds.height <= 0)
		{
			throw new IllegalArgumentException("Capture bounds must be positive.");
		}

		long logicalRight = (long) logicalBounds.x + logicalBounds.width;
		long logicalBottom = (long) logicalBounds.y + logicalBounds.height;
		long left = scaleEdge(logicalBounds.x, logicalCanvasWidth, imageWidth);
		long top = scaleEdge(logicalBounds.y, logicalCanvasHeight, imageHeight);
		long right = scaleEdge(logicalRight, logicalCanvasWidth, imageWidth);
		long bottom = scaleEdge(logicalBottom, logicalCanvasHeight, imageHeight);

		if (clampToImage)
		{
			left = clamp(left, 0, imageWidth);
			right = clamp(right, 0, imageWidth);
			top = clamp(top, 0, imageHeight);
			bottom = clamp(bottom, 0, imageHeight);
		}
		if (right <= left || bottom <= top)
		{
			throw new IllegalArgumentException("Capture bounds do not intersect the screenshot.");
		}
		if (left < Integer.MIN_VALUE || top < Integer.MIN_VALUE
			|| right > Integer.MAX_VALUE || bottom > Integer.MAX_VALUE)
		{
			throw new IllegalArgumentException("Scaled capture bounds exceed supported image coordinates.");
		}

		long width = right - left;
		long height = bottom - top;
		if (width > Integer.MAX_VALUE || height > Integer.MAX_VALUE)
		{
			throw new IllegalArgumentException("Scaled capture dimensions exceed supported image dimensions.");
		}
		return new Rectangle((int) left, (int) top, (int) width, (int) height);
	}

	private static long scaleEdge(long logicalEdge, int logicalExtent, int imageExtent)
	{
		return Math.round((double) logicalEdge * imageExtent / logicalExtent);
	}

	private static long clamp(long value, long minimum, long maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static Map<String, Object> copyStringMap(Object rawValue)
	{
		Map<String, Object> copy = new LinkedHashMap<>();
		if (rawValue instanceof Map)
		{
			for (Map.Entry<?, ?> entry : ((Map<?, ?>) rawValue).entrySet())
			{
				if (entry.getKey() instanceof String)
				{
					copy.put((String) entry.getKey(), entry.getValue());
				}
			}
		}
		return copy;
	}

	private static double round(double value)
	{
		return Math.round(value * 10000.0) / 10000.0;
	}

	private static final class EncodedCapture
	{
		private final BufferedImage image;
		private final byte[] pngBytes;

		private EncodedCapture(BufferedImage image, byte[] pngBytes)
		{
			this.image = image;
			this.pngBytes = pngBytes;
		}
	}
}
