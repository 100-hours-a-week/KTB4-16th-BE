package com.ktb4.team16.mulo.upload.conversion;

public interface HeicImageConverter {
    ConvertedImage convert(byte[] heicContent);

    record ConvertedImage(byte[] content, String contentType, String extension) {
    }
}
