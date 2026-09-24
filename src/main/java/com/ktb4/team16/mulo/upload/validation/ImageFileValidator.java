package com.ktb4.team16.mulo.upload.validation;

import com.ktb4.team16.mulo.upload.exception.EmptyImageException;
import com.ktb4.team16.mulo.upload.exception.ImageSizeExceededException;
import com.ktb4.team16.mulo.upload.exception.InvalidImageFormatException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

public final class ImageFileValidator {
    public static final long MAX_FILE_SIZE = 10 * 1024 * 1024L;
    private static final Set<String> HEIF_BRANDS = Set.of(
            "heic", "heix", "hevc", "hevx", "heif", "heim", "heis", "hevm", "hevs",
            "mif1", "msf1");

    private ImageFileValidator() {
    }

    public static ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new EmptyImageException();
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ImageSizeExceededException();
        }

        String contentType = normalizeContentType(file.getContentType());
        String extension = extensionFor(contentType);
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new InvalidImageFormatException();
        }
        if (extension == null || !hasMatchingExtension(file.getOriginalFilename(), extension)
                || !hasExpectedSignature(content, contentType)) {
            throw new InvalidImageFormatException();
        }
        return new ValidatedImage(content, contentType, extension);
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/heic", "image/heif" -> "heic";
            case "image/webp" -> "webp";
            default -> null;
        };
    }

    private static boolean hasMatchingExtension(String filename, String extension) {
        if (filename == null || filename.isBlank()) {
            return true;
        }
        String lowerFilename = filename.toLowerCase(Locale.ROOT);
        int dotIndex = lowerFilename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == lowerFilename.length() - 1) {
            return true;
        }
        String actualExtension = lowerFilename.substring(dotIndex + 1);
        return switch (extension) {
            case "jpg" -> actualExtension.equals("jpg") || actualExtension.equals("jpeg");
            case "heic" -> actualExtension.equals("heic") || actualExtension.equals("heif");
            default -> actualExtension.equals(extension);
        };
    }

    private static boolean hasExpectedSignature(byte[] bytes, String contentType) {
        return switch (contentType) {
                case "image/jpeg" -> bytes.length >= 3
                        && (bytes[0] & 0xff) == 0xff
                        && (bytes[1] & 0xff) == 0xd8
                        && (bytes[2] & 0xff) == 0xff;
                case "image/png" -> bytes.length >= 8
                        && bytes[0] == (byte) 0x89 && bytes[1] == 0x50
                        && bytes[2] == 0x4e && bytes[3] == 0x47
                        && bytes[4] == 0x0d && bytes[5] == 0x0a
                        && bytes[6] == 0x1a && bytes[7] == 0x0a;
                case "image/webp" -> bytes.length >= 12
                        && bytes[0] == 'R' && bytes[1] == 'I'
                        && bytes[2] == 'F' && bytes[3] == 'F'
                        && bytes[8] == 'W' && bytes[9] == 'E'
                        && bytes[10] == 'B' && bytes[11] == 'P';
                case "image/heic", "image/heif" -> isHeif(bytes);
                default -> false;
        };
    }

    private static boolean isHeif(byte[] bytes) {
        if (bytes.length < 16 || bytes[4] != 'f' || bytes[5] != 't'
                || bytes[6] != 'y' || bytes[7] != 'p') {
            return false;
        }
        long boxSize = ((long) (bytes[0] & 0xff) << 24)
                | ((long) (bytes[1] & 0xff) << 16)
                | ((long) (bytes[2] & 0xff) << 8)
                | (bytes[3] & 0xffL);
        if (boxSize != 0 && (boxSize < 16 || boxSize > bytes.length)) {
            return false;
        }
        int boxEnd = boxSize == 0 ? bytes.length : (int) boxSize;
        if (isHeifBrand(bytes, 8)) {
            return true;
        }
        for (int offset = 16; offset + 4 <= boxEnd; offset += 4) {
            if (isHeifBrand(bytes, offset)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHeifBrand(byte[] bytes, int offset) {
        String brand = new String(bytes, offset, 4, StandardCharsets.US_ASCII);
        return HEIF_BRANDS.contains(brand);
    }

    public record ValidatedImage(byte[] content, String contentType, String extension) {
    }
}
