package com.collabmodeler.api.generation.job;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@Component
@Profile("prod")
public class S3ArtifactStorage implements ArtifactStorage {
    private final S3Client s3; private final String bucket;
    public S3ArtifactStorage(@Value("${app.generation.s3-bucket}") String bucket, @Value("${AWS_REGION:us-east-1}") String region) {
        this.bucket = bucket; this.s3 = S3Client.builder().region(software.amazon.awssdk.regions.Region.of(region)).build();
    }
    @Override public void put(String key, byte[] contents, String contentType) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).serverSideEncryption(ServerSideEncryption.AES256).build(), RequestBody.fromBytes(contents));
    }
    @Override public StoredArtifact get(String key) {
        try { ResponseBytes<GetObjectResponse> result = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build());
            return new StoredArtifact(result.asByteArray(), result.response().contentType());
        } catch (NoSuchKeyException e) { throw new com.collabmodeler.api.support.NotFoundException("El artefacto expiró o no existe"); }
    }
    @Override public void delete(String key) { s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build()); }
}
