package com.ktb4.team16.mulo.upload.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.upload.config.S3Properties;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

class S3PhotoStorageServiceTest {
    private final S3Client client = mock(S3Client.class);
    private final S3Presigner presigner = mock(S3Presigner.class);
    private final S3PhotoStorageService service = new S3PhotoStorageService(
            client, presigner, new S3Properties("ap-northeast-2", "photo-bucket"));

    @Test
    void uploadsObjectWithSameKeyAndContentType() throws Exception {
        byte[] content = {1, 2, 3};

        service.upload("uploads/35/photo.jpg", content, "image/jpeg");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(client).putObject(request.capture(), body.capture());
        assertThat(request.getValue().bucket()).isEqualTo("photo-bucket");
        assertThat(request.getValue().key()).isEqualTo("uploads/35/photo.jpg");
        assertThat(request.getValue().contentType()).isEqualTo("image/jpeg");
        assertThat(body.getValue().contentLength()).isEqualTo(content.length);
        assertThat(body.getValue().contentStreamProvider().newStream().readAllBytes()).isEqualTo(content);
    }

    @Test
    void deletesObjectWithSameKey() {
        service.delete("uploads/35/photo.jpg");

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo("photo-bucket");
        assertThat(request.getValue().key()).isEqualTo("uploads/35/photo.jpg");
    }

    @Test
    void signsReadUrlForTenMinutesByDefault() throws MalformedURLException {
        PresignedGetObjectRequest signed = mock(PresignedGetObjectRequest.class);
        when(signed.url()).thenReturn(new URL("https://signed.example/photo"));
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(signed);

        assertThat(service.createReadSignedUrl("uploads/35/photo.jpg"))
                .isEqualTo("https://signed.example/photo");

        ArgumentCaptor<GetObjectPresignRequest> request =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner).presignGetObject(request.capture());
        assertThat(request.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(10));
        assertThat(request.getValue().getObjectRequest().bucket()).isEqualTo("photo-bucket");
        assertThat(request.getValue().getObjectRequest().key()).isEqualTo("uploads/35/photo.jpg");
    }

    @Test
    void acceptsExplicitExpiration() throws MalformedURLException {
        PresignedGetObjectRequest signed = mock(PresignedGetObjectRequest.class);
        when(signed.url()).thenReturn(new URL("https://signed.example/photo"));
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(signed);

        service.createReadSignedUrl("uploads/35/photo.jpg", Duration.ofHours(1));

        ArgumentCaptor<GetObjectPresignRequest> request =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner).presignGetObject(request.capture());
        assertThat(request.getValue().signatureDuration()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void rejectsInvalidExpiration() {
        assertThatThrownBy(() -> service.createReadSignedUrl("photo.jpg", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createReadSignedUrl("photo.jpg", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
