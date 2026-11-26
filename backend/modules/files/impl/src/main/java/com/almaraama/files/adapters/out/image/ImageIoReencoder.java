package com.almaraama.files.adapters.out.image;

import com.almaraama.files.domain.ImageReencoder;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Component;

/**
 * {@link ImageReencoder} with the JDK's {@code javax.imageio} (ADR-038), no added library. The dimensions are read from
 * the header before any pixel is decoded, which stops decompression bombs; the image is drawn on a white background,
 * reduced and written as JPEG quality 0.85, which drops every metadata block (EXIF, GPS).
 */
@Component
class ImageIoReencoder implements ImageReencoder {

    private static final long MAX_PIXELS = 40_000_000L;
    private static final int MAX_DIMENSION = 10_000;

    /** {@inheritDoc} */
    @Override
    public byte[] toJpeg(byte[] image, int maxSide) {
        BufferedImage decoded = decode(image);
        int width = decoded.getWidth();
        int height = decoded.getHeight();
        double scale = Math.min(1.0, (double) maxSide / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        BufferedImage rgb = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.drawImage(decoded, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return writeJpeg(rgb);
    }

    /** Decodes the image after checking its declared dimensions. */
    private static BufferedImage decode(byte[] image) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(image))) {
            Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) {
                throw unsupported();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw unsupported();
                }
                if (width > MAX_DIMENSION || height > MAX_DIMENSION || (long) width * height > MAX_PIXELS) {
                    throw new BusinessException("IMAGE_TOO_LARGE", ErrorCategory.TOO_LARGE, "Image too large");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw unsupported();
                }
                return decoded;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BusinessException business) {
                throw business;
            }
            throw unsupported();
        }
    }

    /** Writes an RGB image as JPEG quality 0.85. */
    private static byte[] writeJpeg(BufferedImage rgb) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(output);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(0.85f);
            writer.write(null, new IIOImage(rgb, null, null), params);
        } catch (IOException e) {
            throw new IllegalStateException("JPEG not written", e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    /** The answer for an image the JDK cannot decode. */
    private static BusinessException unsupported() {
        return new BusinessException("UNSUPPORTED_CONTENT", ErrorCategory.UNSUPPORTED_CONTENT, "Unreadable image");
    }
}
