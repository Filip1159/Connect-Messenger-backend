package com.example.connectmessenger.service;

import lombok.RequiredArgsConstructor;
import com.example.connectmessenger.config.CustomProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class AwsS3Client {
    public record ImageInfo(String contentType, String extension) {
    }

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] GIF = {'G', 'I', 'F', '8'};

    private final S3Client s3Client;
    private final CustomProperties properties;

    /**
     * Determines the real image type from the file's magic bytes - the client supplied
     * Content-Type and filename are not trusted.
     */
    public ImageInfo detectImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot upload empty file");
        }
        byte[] header;
        try (var in = file.getInputStream()) {
            header = in.readNBytes(PNG.length);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read uploaded file", e);
        }

        if (startsWith(header, JPEG)) return new ImageInfo("image/jpeg", "jpg");
        if (startsWith(header, PNG)) return new ImageInfo("image/png", "png");
        if (startsWith(header, GIF)) return new ImageInfo("image/gif", "gif");
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must be a jpeg, png or gif image");
    }

    public void putFile(String name, MultipartFile multipartFile, ImageInfo image) {
        var request = PutObjectRequest.builder()
                .bucket(properties.s3Bucket())
                .key(name)
                .contentType(image.contentType())
                .contentLength(multipartFile.getSize())
                .build();

        try (var in = multipartFile.getInputStream()) {
            s3Client.putObject(request, RequestBody.fromInputStream(in, multipartFile.getSize()));
        } catch (SdkException | IOException e) {
            throw new IllegalStateException("Failed to upload file to s3", e);
        }
    }

    public byte[] getFile(String fileName) {
        try {
            var request = GetObjectRequest.builder().bucket(properties.s3Bucket()).key(fileName).build();
            return s3Client.getObjectAsBytes(request).asByteArray();
        } catch (SdkException e) {
            throw new IllegalStateException("Failed to download file from s3", e);
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.equals(Arrays.copyOf(data, prefix.length), prefix);
    }
}
