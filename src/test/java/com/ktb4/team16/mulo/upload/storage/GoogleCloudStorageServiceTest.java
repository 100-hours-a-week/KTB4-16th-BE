package com.ktb4.team16.mulo.upload.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.storage.Storage;
import com.ktb4.team16.mulo.upload.config.GcsProperties;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GoogleCloudStorageServiceTest {
    private final Storage storage = mock(Storage.class);
    private final GoogleCloudStorageService service = new GoogleCloudStorageService(
            storage, new GcsProperties("project", "bucket"));

    @Test
    void createsReadSignedUrlForTenMinutes() throws MalformedURLException {
        URL signedUrl = new URL("https://signed.example/photo");
        when(storage.signUrl(any(), eq(10L), eq(TimeUnit.MINUTES),
                any(Storage.SignUrlOption.class))).thenReturn(signedUrl);

        assertThat(service.createReadSignedUrl("uploads/35/photo.jpg"))
                .isEqualTo(signedUrl.toString());

        ArgumentCaptor<Storage.SignUrlOption> option =
                ArgumentCaptor.forClass(Storage.SignUrlOption.class);
        verify(storage).signUrl(any(), eq(10L), eq(TimeUnit.MINUTES), option.capture());
        assertThat(option.getValue()).isNotNull();
    }
}
