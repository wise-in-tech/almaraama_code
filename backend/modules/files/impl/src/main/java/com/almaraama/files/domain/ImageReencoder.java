package com.almaraama.files.domain;

/**
 * Out port: decodes an image and writes it again as JPEG (ADR-038), which drops every metadata block (EXIF, GPS
 * position) and any payload hidden in the file. The adapter checks the dimensions before decoding.
 */
public interface ImageReencoder {

    /**
     * The image as JPEG, at most {@code maxSide} pixels on its long side. {@code IMAGE_TOO_LARGE} above 40 megapixels
     * or 10,000 pixels on a side; {@code UNSUPPORTED_CONTENT} when the image cannot be decoded.
     */
    byte[] toJpeg(byte[] image, int maxSide);
}
