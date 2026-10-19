package com.example.connectmessenger.config;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryS3ClientTest {
    private final InMemoryS3Client s3 = new InMemoryS3Client();

    @Test
    void storedObjectCanBeReadBack() {
        s3.putObject(PutObjectRequest.builder().bucket("b").key("k.png").build(), RequestBody.fromBytes(new byte[]{1, 2, 3}));

        var read = s3.getObjectAsBytes(GetObjectRequest.builder().bucket("b").key("k.png").build());

        assertThat(read.asByteArray()).containsExactly(1, 2, 3);
    }

    @Test
    void missingObjectThrowsNoSuchKey() {
        assertThatThrownBy(() -> s3.getObjectAsBytes(GetObjectRequest.builder().bucket("b").key("nope").build()))
                .isInstanceOf(NoSuchKeyException.class);
    }

    @Test
    void sameKeyInAnotherBucketIsADifferentObject() {
        s3.putObject(PutObjectRequest.builder().bucket("a").key("k").build(), RequestBody.fromBytes(new byte[]{1}));

        assertThatThrownBy(() -> s3.getObjectAsBytes(GetObjectRequest.builder().bucket("b").key("k").build()))
                .isInstanceOf(NoSuchKeyException.class);
    }
}
