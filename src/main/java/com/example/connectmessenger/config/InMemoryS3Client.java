package com.example.connectmessenger.config;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stand-in for S3 in the `dev` profile: objects live in memory (lost on restart), so no AWS account or
 * credentials are needed. Only the two operations the app uses are implemented.
 */
public class InMemoryS3Client implements S3Client {
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public PutObjectResponse putObject(PutObjectRequest request, RequestBody body) {
        try (var in = body.contentStreamProvider().newStream()) {
            objects.put(key(request.bucket(), request.key()), in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return PutObjectResponse.builder().build();
    }

    @Override
    public ResponseBytes<GetObjectResponse> getObjectAsBytes(GetObjectRequest request) {
        byte[] data = objects.get(key(request.bucket(), request.key()));
        if (data == null) {
            throw NoSuchKeyException.builder().message("No such key: " + request.key()).build();
        }
        return ResponseBytes.fromByteArray(GetObjectResponse.builder().contentLength((long) data.length).build(), data);
    }

    @Override
    public String serviceName() {
        return "s3";
    }

    @Override
    public void close() {
        objects.clear();
    }

    private static String key(String bucket, String key) {
        return bucket + "/" + key;
    }
}
