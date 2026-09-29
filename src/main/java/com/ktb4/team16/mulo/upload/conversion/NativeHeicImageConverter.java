package com.ktb4.team16.mulo.upload.conversion;

import com.ktb4.team16.mulo.upload.config.HeicConversionProperties;
import com.ktb4.team16.mulo.upload.exception.HeicConversionException;
import com.ktb4.team16.mulo.upload.exception.HeicConversionException.Reason;
import com.ktb4.team16.mulo.upload.validation.ImageFileValidator;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NativeHeicImageConverter implements HeicImageConverter {
    private static final String JPEG_CONTENT_TYPE = "image/jpeg";
    private static final String JPEG_EXTENSION = "jpg";
    private static final String JPEG_QUALITY = "90";
    private static final long PROCESS_TERMINATION_GRACE_MILLIS = 1_000L;

    private final HeicConversionProperties properties;
    private final Semaphore conversionPermits;
    private final Path temporaryRoot;

    public NativeHeicImageConverter(HeicConversionProperties properties) {
        this(properties, Path.of(System.getProperty("java.io.tmpdir")));
    }

    NativeHeicImageConverter(HeicConversionProperties properties, Path temporaryRoot) {
        this.properties = properties;
        this.conversionPermits = new Semaphore(properties.maxConcurrentConversions(), true);
        this.temporaryRoot = temporaryRoot;
    }

    @Override
    public ConvertedImage convert(byte[] heicContent) {
        if (heicContent == null || heicContent.length == 0) {
            throw new HeicConversionException(Reason.INVALID_HEIC);
        }

        boolean acquired = acquirePermit();
        try {
            return convertWithTemporaryFiles(heicContent);
        } finally {
            if (acquired) {
                conversionPermits.release();
            }
        }
    }

    private boolean acquirePermit() {
        try {
            boolean acquired = conversionPermits.tryAcquire(
                    properties.permitTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new HeicConversionException(Reason.CAPACITY_TIMEOUT);
            }
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new HeicConversionException(Reason.INTERRUPTED, exception);
        }
    }

    private ConvertedImage convertWithTemporaryFiles(byte[] heicContent) {
        Path directory = null;
        try {
            directory = Files.createTempDirectory(temporaryRoot, "mulo-heic-");
            Path input = directory.resolve("input.heic");
            Path converted = directory.resolve("converted.jpg");
            Path sanitized = directory.resolve("sanitized.jpg");
            Files.write(input, heicContent);

            // HEIC is normalized to JPEG for browser and AI compatibility.
            runProcess(List.of(properties.heifConvertCommand(), "--quality", JPEG_QUALITY,
                            input.toString(), converted.toString()),
                    Reason.HEIF_CONVERT_UNAVAILABLE,
                    Reason.HEIF_CONVERT_FAILED,
                    Reason.HEIF_CONVERT_TIMEOUT);
            requireNonEmptyFile(converted, Reason.HEIF_CONVERT_FAILED);

            // Copying only ICC removes private capture metadata without recompressing image data.
            runProcess(List.of(properties.jpegtranCommand(), "-copy", "icc", "-outfile",
                            sanitized.toString(), converted.toString()),
                    Reason.JPEGTRAN_UNAVAILABLE,
                    Reason.JPEGTRAN_FAILED,
                    Reason.JPEGTRAN_TIMEOUT);

            validateFinalJpeg(sanitized);
            return new ConvertedImage(Files.readAllBytes(sanitized),
                    JPEG_CONTENT_TYPE, JPEG_EXTENSION);
        } catch (HeicConversionException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new HeicConversionException(Reason.IO_FAILURE, exception);
        } finally {
            deleteTemporaryDirectory(directory);
        }
    }

    private void runProcess(List<String> command, Reason unavailableReason,
            Reason failedReason, Reason timeoutReason) {
        Process process;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException exception) {
            throw new HeicConversionException(unavailableReason, exception);
        }

        Thread outputDrainer = Thread.ofVirtual().start(() -> discardOutput(process));
        try {
            if (!process.waitFor(properties.processTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                terminate(process);
                throw new HeicConversionException(timeoutReason);
            }
            if (process.exitValue() != 0) {
                throw new HeicConversionException(failedReason);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            terminate(process);
            throw new HeicConversionException(Reason.INTERRUPTED, exception);
        } finally {
            joinDrainer(outputDrainer);
        }
    }

    private void discardOutput(Process process) {
        try (InputStream output = process.getInputStream()) {
            output.transferTo(OutputStream.nullOutputStream());
        } catch (IOException exception) {
            log.debug("Failed to drain native image process output", exception);
        }
    }

    private void terminate(Process process) {
        process.destroy();
        try {
            if (!process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private void joinDrainer(Thread outputDrainer) {
        try {
            outputDrainer.join(PROCESS_TERMINATION_GRACE_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new HeicConversionException(Reason.INTERRUPTED, exception);
        }
    }

    private void validateFinalJpeg(Path jpeg) throws IOException {
        requireNonEmptyFile(jpeg, Reason.INVALID_JPEG);
        long fileSize = Files.size(jpeg);
        if (fileSize > ImageFileValidator.MAX_FILE_SIZE) {
            throw new HeicConversionException(Reason.OUTPUT_SIZE_EXCEEDED);
        }
        if (!hasJpegSignature(jpeg)) {
            throw new HeicConversionException(Reason.INVALID_JPEG);
        }

        try (ImageInputStream input = ImageIO.createImageInputStream(jpeg.toFile())) {
            if (input == null) {
                throw new HeicConversionException(Reason.INVALID_JPEG);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new HeicConversionException(Reason.INVALID_JPEG);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw new HeicConversionException(Reason.INVALID_JPEG);
                }
                // Pixel count protects native conversion capacity independently of byte size.
                long pixels = (long) width * height;
                if (pixels > properties.maxPixels()) {
                    throw new HeicConversionException(Reason.PIXEL_LIMIT_EXCEEDED);
                }
            } finally {
                reader.dispose();
            }
        }
    }

    private boolean hasJpegSignature(Path jpeg) throws IOException {
        try (InputStream input = Files.newInputStream(jpeg)) {
            byte[] signature = input.readNBytes(3);
            return signature.length == 3
                    && (signature[0] & 0xff) == 0xff
                    && (signature[1] & 0xff) == 0xd8
                    && (signature[2] & 0xff) == 0xff;
        }
    }

    private void requireNonEmptyFile(Path file, Reason reason) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) == 0) {
            throw new HeicConversionException(reason);
        }
    }

    private void deleteTemporaryDirectory(Path directory) {
        if (directory == null) {
            return;
        }
        try (var files = Files.walk(directory)) {
            files.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                    .forEach(this::deleteTemporaryPath);
        } catch (IOException exception) {
            log.warn("Failed to clean up HEIC conversion temporary resources", exception);
        }
    }

    private void deleteTemporaryPath(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn("Failed to delete a HEIC conversion temporary resource", exception);
        }
    }
}
