package com.ktb4.team16.mulo.upload.conversion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb4.team16.mulo.upload.config.HeicConversionProperties;
import com.ktb4.team16.mulo.upload.exception.HeicConversionException;
import com.ktb4.team16.mulo.upload.exception.HeicConversionException.Reason;
import com.ktb4.team16.mulo.upload.validation.ImageFileValidator;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeHeicImageConverterTest {
    @TempDir
    private Path temporaryRoot;

    @Test
    void convertsWithQualityNinetyAndCopiesOnlyIccMetadata() throws Exception {
        Path sourceJpeg = jpeg(2, 2);
        Path heifArguments = temporaryRoot.resolve("heif-arguments");
        Path jpegtranArguments = temporaryRoot.resolve("jpegtran-arguments");
        Path heif = executable("heif-success", "printf '%s' \"$*\" > '"
                + heifArguments + "'\ncp '" + sourceJpeg + "' \"$4\"");
        Path jpegtran = executable("jpegtran-success", "printf '%s' \"$*\" > '"
                + jpegtranArguments + "'\ncp \"$5\" \"$4\"");
        NativeHeicImageConverter converter = converter(heif, jpegtran,
                Duration.ofSeconds(2), Duration.ofMillis(100), 2, 25_000_000L);

        HeicImageConverter.ConvertedImage result = converter.convert(new byte[]{1, 2, 3});

        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.extension()).isEqualTo("jpg");
        assertThat(result.content()).startsWith((byte) 0xff, (byte) 0xd8, (byte) 0xff);
        assertThat(Files.readString(heifArguments)).contains("--quality 90");
        assertThat(Files.readString(jpegtranArguments)).startsWith("-copy icc -outfile");
        assertNoConversionDirectories();
    }

    @Test
    void reportsInvalidHeicWhenHeifConvertExitsAbnormally() throws Exception {
        NativeHeicImageConverter converter = converter(executable("heif-fail", "exit 2"),
                executable("jpegtran-unused", "exit 0"), Duration.ofSeconds(1),
                Duration.ofMillis(100), 2, 25_000_000L);

        assertReason(converter, Reason.HEIF_CONVERT_FAILED);
        assertNoConversionDirectories();
    }

    @Test
    void terminatesHeifConvertAfterTimeout() throws Exception {
        NativeHeicImageConverter converter = converter(
                executable("heif-timeout", "sleep 2"),
                executable("jpegtran-unused", "exit 0"), Duration.ofMillis(50),
                Duration.ofMillis(100), 2, 25_000_000L);

        assertReason(converter, Reason.HEIF_CONVERT_TIMEOUT);
        assertNoConversionDirectories();
    }

    @Test
    void reportsMissingHeifConvertExecutable() throws Exception {
        NativeHeicImageConverter converter = converter(
                temporaryRoot.resolve("missing-heif-convert"),
                executable("jpegtran-unused", "exit 0"), Duration.ofSeconds(1),
                Duration.ofMillis(100), 2, 25_000_000L);

        assertReason(converter, Reason.HEIF_CONVERT_UNAVAILABLE);
        assertNoConversionDirectories();
    }

    @Test
    void reportsJpegtranFailureAndCleansTemporaryFiles() throws Exception {
        Path sourceJpeg = jpeg(2, 2);
        NativeHeicImageConverter converter = converter(
                executable("heif-success", "cp '" + sourceJpeg + "' \"$4\""),
                executable("jpegtran-fail", "exit 3"), Duration.ofSeconds(1),
                Duration.ofMillis(100), 2, 25_000_000L);

        assertReason(converter, Reason.JPEGTRAN_FAILED);
        assertNoConversionDirectories();
    }

    @Test
    void terminatesJpegtranAfterTimeout() throws Exception {
        Path sourceJpeg = jpeg(2, 2);
        NativeHeicImageConverter converter = converter(
                executable("heif-success", "cp '" + sourceJpeg + "' \"$4\""),
                executable("jpegtran-timeout", "sleep 10"), Duration.ofSeconds(3),
                Duration.ofMillis(100), 2, 25_000_000L);

        assertReason(converter, Reason.JPEGTRAN_TIMEOUT);
        assertNoConversionDirectories();
    }

    @Test
    void reportsMissingJpegtranExecutable() throws Exception {
        Path sourceJpeg = jpeg(2, 2);
        NativeHeicImageConverter converter = converter(
                executable("heif-success", "cp '" + sourceJpeg + "' \"$4\""),
                temporaryRoot.resolve("missing-jpegtran"), Duration.ofSeconds(1),
                Duration.ofMillis(100), 2, 25_000_000L);

        assertReason(converter, Reason.JPEGTRAN_UNAVAILABLE);
        assertNoConversionDirectories();
    }

    @Test
    void rejectsInvalidFinalJpeg() throws Exception {
        Path sourceJpeg = jpeg(2, 2);
        NativeHeicImageConverter converter = converter(
                executable("heif-success", "cp '" + sourceJpeg + "' \"$4\""),
                executable("jpegtran-invalid", "printf 'not-jpeg' > \"$4\""),
                Duration.ofSeconds(1), Duration.ofMillis(100), 2, 25_000_000L);

        assertReason(converter, Reason.INVALID_JPEG);
        assertNoConversionDirectories();
    }

    @Test
    void rejectsJpegOverTwentyFiveMegapixelsWithoutDecodingPixels() throws Exception {
        Path sourceJpeg = jpegWithDimensions(5_001, 5_000);
        NativeHeicImageConverter converter = successfulConverter(sourceJpeg, 25_000_000L);

        assertReason(converter, Reason.PIXEL_LIMIT_EXCEEDED);
        assertNoConversionDirectories();
    }

    @Test
    void rejectsConvertedJpegOverTenMebibytes() throws Exception {
        Path sourceJpeg = jpeg(2, 2);
        try (RandomAccessFile file = new RandomAccessFile(sourceJpeg.toFile(), "rw")) {
            file.setLength(ImageFileValidator.MAX_FILE_SIZE + 1);
        }
        NativeHeicImageConverter converter = successfulConverter(sourceJpeg, 25_000_000L);

        assertReason(converter, Reason.OUTPUT_SIZE_EXCEEDED);
        assertNoConversionDirectories();
    }

    @Test
    void limitsConcurrentConversionsAndReturnsPermitAfterCompletion() throws Exception {
        Path sourceJpeg = jpeg(2, 2);
        Path started = temporaryRoot.resolve("started");
        Path heif = executable("heif-slow", "touch '" + started
                + "'\nsleep 1\ncp '" + sourceJpeg + "' \"$4\"");
        Path jpegtran = executable("jpegtran-success", "cp \"$5\" \"$4\"");
        NativeHeicImageConverter converter = converter(heif, jpegtran,
                Duration.ofSeconds(2), Duration.ofMillis(50), 1, 25_000_000L);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<HeicImageConverter.ConvertedImage> first =
                    executor.submit(() -> converter.convert(new byte[]{1}));
            awaitFile(started);

            assertReason(converter, Reason.CAPACITY_TIMEOUT);
            assertThat(first.get().contentType()).isEqualTo("image/jpeg");
            assertThat(converter.convert(new byte[]{3}).contentType()).isEqualTo("image/jpeg");
        } finally {
            executor.shutdownNow();
        }
        assertNoConversionDirectories();
    }

    @Test
    void preservesInterruptionWhileWaitingForConversionPermit() throws Exception {
        Path sourceJpeg = jpeg(2, 2);
        NativeHeicImageConverter converter = successfulConverter(sourceJpeg, 25_000_000L);
        Thread.currentThread().interrupt();
        try {
            assertReason(converter, Reason.INTERRUPTED);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        assertNoConversionDirectories();
    }

    private NativeHeicImageConverter successfulConverter(Path sourceJpeg, long maxPixels)
            throws IOException {
        return converter(
                executable("heif-success", "cp '" + sourceJpeg + "' \"$4\""),
                executable("jpegtran-success", "cp \"$5\" \"$4\""),
                Duration.ofSeconds(5), Duration.ofMillis(100), 2, maxPixels);
    }

    private NativeHeicImageConverter converter(Path heif, Path jpegtran,
            Duration processTimeout, Duration permitTimeout, int maxConcurrent,
            long maxPixels) {
        HeicConversionProperties properties = new HeicConversionProperties(
                maxPixels, processTimeout, maxConcurrent, permitTimeout,
                heif.toString(), jpegtran.toString());
        return new NativeHeicImageConverter(properties, temporaryRoot);
    }

    private Path executable(String name, String body) throws IOException {
        Path script = temporaryRoot.resolve(name);
        Files.writeString(script, "#!/bin/sh\nset -eu\n" + body + "\n");
        assertThat(script.toFile().setExecutable(true)).isTrue();
        return script;
    }

    private Path jpeg(int width, int height) throws IOException {
        Path jpeg = Files.createTempFile(temporaryRoot, "source-", ".jpg");
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        assertThat(ImageIO.write(image, "jpeg", jpeg.toFile())).isTrue();
        return jpeg;
    }

    private Path jpegWithDimensions(int width, int height) throws IOException {
        Path jpeg = jpeg(2, 2);
        byte[] content = Files.readAllBytes(jpeg);
        for (int index = 0; index < content.length - 8; index++) {
            if ((content[index] & 0xff) == 0xff && (content[index + 1] & 0xff) == 0xc0) {
                content[index + 5] = (byte) (height >>> 8);
                content[index + 6] = (byte) height;
                content[index + 7] = (byte) (width >>> 8);
                content[index + 8] = (byte) width;
                Files.write(jpeg, content);
                return jpeg;
            }
        }
        throw new IllegalStateException("JPEG start-of-frame marker not found");
    }

    private void assertReason(NativeHeicImageConverter converter, Reason reason) {
        assertThatThrownBy(() -> converter.convert(new byte[]{1, 2, 3}))
                .isInstanceOf(HeicConversionException.class)
                .extracting(exception -> ((HeicConversionException) exception).reason())
                .isEqualTo(reason);
    }

    private void awaitFile(Path file) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        while (!Files.exists(file) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(file).exists();
    }

    private void assertNoConversionDirectories() throws IOException {
        try (var paths = Files.list(temporaryRoot)) {
            assertThat(paths.filter(path -> path.getFileName().toString().startsWith("mulo-heic-")))
                    .isEmpty();
        }
    }
}
