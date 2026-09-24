package com.ktb4.team16.mulo.upload.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb4.team16.mulo.upload.exception.EmptyImageException;
import com.ktb4.team16.mulo.upload.exception.ImageSizeExceededException;
import com.ktb4.team16.mulo.upload.exception.InvalidImageFormatException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ImageFileValidatorTest {
    @Test
    void validatesJpegAndReturnsNormalizedMetadata() {
        MockMultipartFile file = file("photo.jpeg", "image/jpeg", new byte[]{
                (byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00
        });

        ImageFileValidator.ValidatedImage image = ImageFileValidator.validate(file);

        assertThat(image.contentType()).isEqualTo("image/jpeg");
        assertThat(image.extension()).isEqualTo("jpg");
    }

    @Test
    void acceptsWebp() {
        MockMultipartFile file = file("photo.webp", "image/webp", new byte[]{
                'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'
        });

        assertThat(ImageFileValidator.validate(file).extension()).isEqualTo("webp");
    }

    @Test
    void acceptsPng() {
        MockMultipartFile file = file("photo.png", "image/png", new byte[]{
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
        });

        assertThat(ImageFileValidator.validate(file).extension()).isEqualTo("png");
    }

    @Test
    void acceptsHeicAndHeifFtypBrands() {
        assertThat(ImageFileValidator.validate(
                file("photo.HEIC", "image/heic", heif("heic", "mif1"))).extension())
                .isEqualTo("heic");
        assertThat(ImageFileValidator.validate(
                file("photo.heif", "image/heif", heif("mif1", "heif"))).extension())
                .isEqualTo("heic");
    }

    @Test
    void acceptsUppercaseExtensionAndExtensionlessFilename() {
        byte[] jpeg = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00};

        assertThat(ImageFileValidator.validate(file("photo.JPG", "image/jpeg", jpeg))
                .extension()).isEqualTo("jpg");
        assertThat(ImageFileValidator.validate(file("photo", "image/jpeg", jpeg))
                .extension()).isEqualTo("jpg");
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> ImageFileValidator.validate(
                file("photo.jpg", "image/jpeg", new byte[0])))
                .isInstanceOf(EmptyImageException.class);
    }

    @Test
    void rejectsUnsupportedFormat() {
        assertThatThrownBy(() -> ImageFileValidator.validate(
                file("photo.gif", "image/gif", new byte[]{'G', 'I', 'F'})))
                .isInstanceOf(InvalidImageFormatException.class);
    }

    @Test
    void rejectsMimeAndSignatureMismatch() {
        assertThatThrownBy(() -> ImageFileValidator.validate(
                file("photo.png", "image/png", new byte[]{
                        (byte) 0xff, (byte) 0xd8, (byte) 0xff
                })))
                .isInstanceOf(InvalidImageFormatException.class);
    }

    @Test
    void acceptsExactlyTenMegabytes() {
        byte[] content = new byte[(int) ImageFileValidator.MAX_FILE_SIZE];
        content[0] = (byte) 0xff;
        content[1] = (byte) 0xd8;
        content[2] = (byte) 0xff;

        assertThat(ImageFileValidator.validate(file("photo.jpg", "image/jpeg", content))
                .content()).hasSize((int) ImageFileValidator.MAX_FILE_SIZE);
    }

    @Test
    void rejectsFileLargerThanTenMegabytes() {
        byte[] content = new byte[(int) ImageFileValidator.MAX_FILE_SIZE + 1];

        assertThatThrownBy(() -> ImageFileValidator.validate(
                file("photo.jpg", "image/jpeg", content)))
                .isInstanceOf(ImageSizeExceededException.class);
    }

    private MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("photo", name, contentType, content);
    }

    private byte[] heif(String majorBrand, String compatibleBrand) {
        byte[] bytes = new byte[24];
        bytes[3] = 24;
        bytes[4] = 'f';
        bytes[5] = 't';
        bytes[6] = 'y';
        bytes[7] = 'p';
        System.arraycopy(majorBrand.getBytes(), 0, bytes, 8, 4);
        System.arraycopy(compatibleBrand.getBytes(), 0, bytes, 16, 4);
        return bytes;
    }
}
