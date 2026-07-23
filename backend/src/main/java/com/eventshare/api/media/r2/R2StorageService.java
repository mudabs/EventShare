package com.eventshare.api.media.r2;

import com.eventshare.api.config.AppProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * Thin wrapper over the R2 (S3) client. The presigned URLs let the browser stream
 * bytes directly to/from object storage, so large media never transits this
 * service. {@link #headObject} is used at completion time to confirm the upload
 * landed and to read its authoritative size.
 */
@Service
public class R2StorageService {

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final AppProperties.R2 r2;

    public R2StorageService(S3Client s3Client, S3Presigner presigner, AppProperties props) {
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.r2 = props.r2();
    }

    public String presignUpload(String objectKey, String contentType) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(r2.bucket())
                .key(objectKey)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(r2.presignUploadTtlSeconds()))
                .putObjectRequest(put)
                .build();
        return presigner.presignPutObject(presignRequest).url().toString();
    }

    public String presignDownload(String objectKey) {
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(r2.bucket())
                .key(objectKey)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(r2.presignDownloadTtlSeconds()))
                .getObjectRequest(get)
                .build();
        return presigner.presignGetObject(presignRequest).url().toString();
    }

    public Optional<HeadObjectResponse> headObject(String objectKey) {
        try {
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(r2.bucket())
                    .key(objectKey)
                    .build());
            return Optional.of(response);
        } catch (NoSuchKeyException notFound) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    public long uploadTtlSeconds() {
        return r2.presignUploadTtlSeconds();
    }

    public void deleteObject(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(r2.bucket())
                .key(objectKey)
                .build());
    }

    /**
     * Streams an object from R2 into a fresh temp file and returns its path. Used
     * by in-process media processing (thumbnail/poster generation), where the bytes
     * must be read server-side rather than presigned to the browser.
     */
    public Path downloadToTempFile(String objectKey, String suffix) throws IOException {
        Path target = Files.createTempFile("es-orig-", suffix);
        Files.deleteIfExists(target); // the SDK requires the destination not to exist yet
        s3Client.getObject(
                GetObjectRequest.builder().bucket(r2.bucket()).key(objectKey).build(),
                ResponseTransformer.toFile(target));
        return target;
    }

    /** Uploads a local file to R2 under the given key and content type. */
    public void uploadFile(Path file, String objectKey, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(r2.bucket())
                        .key(objectKey)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromFile(file));
    }
}
