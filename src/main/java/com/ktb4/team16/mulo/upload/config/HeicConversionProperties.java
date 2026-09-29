package com.ktb4.team16.mulo.upload.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "upload.heic")
public record HeicConversionProperties(
        long maxPixels,
        Duration processTimeout,
        int maxConcurrentConversions,
        Duration permitTimeout,
        String heifConvertCommand,
        String jpegtranCommand) {
    public HeicConversionProperties {
        if (maxPixels <= 0 || processTimeout == null || processTimeout.isNegative()
                || processTimeout.isZero() || maxConcurrentConversions <= 0
                || permitTimeout == null || permitTimeout.isNegative() || permitTimeout.isZero()
                || heifConvertCommand == null || heifConvertCommand.isBlank()
                || jpegtranCommand == null || jpegtranCommand.isBlank()) {
            throw new IllegalArgumentException("HEIC conversion properties must be positive and non-blank");
        }
    }
}
