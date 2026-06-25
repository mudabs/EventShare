package com.eventshare.worker.r2;

import com.eventshare.worker.config.WorkerProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class R2ObjectStore {

    private final S3Client s3Client;
    private final String bucket;

    public R2ObjectStore(S3Client s3Client, WorkerProperties props) {
        this.s3Client = s3Client;
        this.bucket = props.r2().bucket();
    }

    /** Streams an object to a fresh temp file and returns its path. */
    public Path download(String objectKey, String suffix) throws IOException {
        Path target = Files.createTempFile("es-orig-", suffix);
        Files.deleteIfExists(target); // SDK requires the file not to exist yet
        s3Client.getObject(
                GetObjectRequest.builder().bucket(bucket).key(objectKey).build(),
                ResponseTransformer.toFile(target));
        return target;
    }

    public void upload(Path file, String objectKey, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromFile(file));
    }
}
