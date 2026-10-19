package com.example.connectmessenger.persistence;

import com.example.connectmessenger.config.CustomProperties;
import com.example.connectmessenger.service.AwsS3Client;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link AwsS3Client} against a real S3 API (LocalStack), not the in-memory stand-in of the dev profile. */
@Testcontainers(disabledWithoutDocker = true)
class S3IT {
    private static final String BUCKET = "connect-messenger-test";

    @Container
    static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8")).withServices("s3");

    private static AwsS3Client client;

    @BeforeAll
    static void bucket() {
        S3Client s3 = S3Client.builder()
                .endpointOverride(LOCALSTACK.getEndpoint())
                .region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
                .forcePathStyle(true)
                .build();
        createBucketWhenReachable(s3);
        var properties = new CustomProperties("integration-secret-integration-secret-1234", 3_600_000, List.of("http://localhost"),
                BUCKET, LOCALSTACK.getRegion(), true, 5, Duration.ofMinutes(15), Duration.ofMinutes(15));
        client = new AwsS3Client(s3, properties);
    }

    /** The mapped port can take a moment to accept connections (e.g. with a VM based Docker such as Colima). */
    private static void createBucketWhenReachable(S3Client s3) {
        long deadline = System.currentTimeMillis() + 30_000;
        while (true) {
            try {
                s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
                return;
            } catch (SdkClientException e) {
                if (System.currentTimeMillis() > deadline) throw e;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

    @Test
    void anUploadedImageIsReturnedByteForByte() {
        var file = new MockMultipartFile("f", "x.png", "image/png", PNG);
        var info = client.detectImage(file);

        client.putFile("photo.png", file, info);

        assertThat(client.getFile("photo.png")).isEqualTo(PNG);
    }

    @Test
    void aMissingFileIsAnError() {
        assertThatThrownBy(() -> client.getFile("nothing-here.png")).isInstanceOf(IllegalStateException.class);
    }
}
