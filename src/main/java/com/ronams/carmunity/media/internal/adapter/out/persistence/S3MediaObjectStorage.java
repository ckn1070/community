package com.ronams.carmunity.media.internal.adapter.out.persistence;

import com.ronams.carmunity.media.api.MediaAvailability;
import com.ronams.carmunity.media.internal.application.MediaObjectStorage;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
@ConditionalOnProperty(prefix = "carmunity.community", name = "enabled", havingValue = "true")
class S3MediaObjectStorage implements MediaObjectStorage, MediaAvailability {
  private final String bucket;
  private final S3Client client;

  S3MediaObjectStorage(
      @Value("${carmunity.community.media.bucket}") String bucket,
      @Value("${carmunity.community.media.region}") String region,
      @Value("${carmunity.community.media.role-arn}") String roleArn,
      @Value("${carmunity.community.media.token-path}") String tokenPath) {
    this.bucket = bucket;
    if (bucket.isBlank() || roleArn.isBlank()) {
      this.client = null;
      return;
    }
    var credentials =
        WebIdentityTokenFileCredentialsProvider.builder()
            .roleArn(roleArn)
            .roleSessionName("car-community-media")
            .webIdentityTokenFile(Path.of(tokenPath))
            .build();
    this.client =
        S3Client.builder().region(Region.of(region)).credentialsProvider(credentials).build();
  }

  public void put(String key, byte[] bytes, String mediaType) {
    requireReady();
    client.putObject(
        builder ->
            builder.bucket(bucket).key(key).contentType(mediaType).serverSideEncryption("AES256"),
        RequestBody.fromBytes(bytes));
  }

  @Override
  public void put(String key, Path file, long byteSize, String mediaType) {
    requireReady();
    client.putObject(
        builder ->
            builder
                .bucket(bucket)
                .key(key)
                .contentLength(byteSize)
                .contentType(mediaType)
                .serverSideEncryption("AES256"),
        RequestBody.fromFile(file));
  }

  public byte[] get(String key) {
    requireReady();
    try {
      return client.getObjectAsBytes(builder -> builder.bucket(bucket).key(key)).asByteArray();
    } catch (S3Exception exception) {
      if (exception.statusCode() == 404) return new byte[0];
      throw exception;
    }
  }

  public void delete(String key) {
    requireReady();
    client.deleteObject(builder -> builder.bucket(bucket).key(key));
  }

  @Override
  public boolean exists(String key) {
    requireReady();
    try {
      client.headObject(builder -> builder.bucket(bucket).key(key));
      return true;
    } catch (S3Exception exception) {
      if (exception.statusCode() == 404) return false;
      throw exception;
    }
  }

  @Override
  public boolean isReady() {
    return client != null;
  }

  private void requireReady() {
    if (client == null) throw new IllegalStateException("Community media storage가 준비되지 않았습니다.");
  }
}
