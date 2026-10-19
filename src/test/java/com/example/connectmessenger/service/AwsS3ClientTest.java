package com.example.connectmessenger.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AwsS3ClientTest {
    private final AwsS3Client client = new AwsS3Client(null, null);

    private static MockMultipartFile file(String claimedType, int... bytes) {
        byte[] data = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) data[i] = (byte) bytes[i];
        return new MockMultipartFile("f", "whatever.bin", claimedType, data);
    }

    @Test
    void detectsImagesByMagicBytesIgnoringClaimedType() {
        assertThat(client.detectImage(file("text/plain", 0xFF, 0xD8, 0xFF, 0xE0, 0, 0)))
                .isEqualTo(new AwsS3Client.ImageInfo("image/jpeg", "jpg"));
        assertThat(client.detectImage(file("application/octet-stream", 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)))
                .isEqualTo(new AwsS3Client.ImageInfo("image/png", "png"));
        assertThat(client.detectImage(file("image/jpeg", 'G', 'I', 'F', '8', '9', 'a')))
                .isEqualTo(new AwsS3Client.ImageInfo("image/gif", "gif"));
    }

    @Test
    void rejectsNonImagesEvenWithImageContentType() {
        assertBadRequest(file("image/png", '<', 's', 'c', 'r', 'i', 'p', 't', '>'));
        assertBadRequest(file("image/png", 0x89, 'P'));  // truncated PNG signature
    }

    @Test
    void rejectsEmptyAndMissingFiles() {
        assertBadRequest(file("image/png"));
        assertThatThrownBy(() -> client.detectImage(null)).isInstanceOf(ResponseStatusException.class);
    }

    private void assertBadRequest(MockMultipartFile file) {
        assertThatThrownBy(() -> client.detectImage(file)).isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
