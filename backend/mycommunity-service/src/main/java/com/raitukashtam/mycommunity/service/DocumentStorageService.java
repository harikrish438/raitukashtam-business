package com.raitukashtam.mycommunity.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Thin wrapper around the S3 SDK -- no community/domain knowledge here,
 * just object storage by key. DocumentService owns the community_document
 * metadata row and the S3 key naming convention.
 */
@Service
@Slf4j
public class DocumentStorageService {
    @Autowired
    private S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.auto-create-bucket:false}")
    private boolean autoCreateBucket;

    @PostConstruct
    void ensureBucketExists() {
        if (!autoCreateBucket) {
            return;
        }
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (NoSuchBucketException e) {
            log.info("S3 bucket '{}' does not exist, creating it (dev/LocalStack only)", bucketName);
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        }
    }

    public void upload(String key, String contentType, byte[] content) {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(bucketName).key(key).contentType(contentType).build(),
                    RequestBody.fromBytes(content));
        } catch (S3Exception e) {
            log.error("Failed to upload document to S3, key={}", key, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to store document");
        }
    }

    public byte[] download(String key) {
        try (ResponseInputStream<GetObjectResponse> in = s3Client.getObject(
                GetObjectRequest.builder().bucket(bucketName).key(key).build())) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (S3Exception e) {
            log.error("Failed to download document from S3, key={}", key, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to retrieve document");
        }
    }

    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
        } catch (S3Exception e) {
            log.error("Failed to delete document from S3, key={}", key, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to delete document");
        }
    }
}
