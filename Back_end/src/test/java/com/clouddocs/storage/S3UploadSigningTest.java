package com.clouddocs.storage;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class S3UploadSigningTest {
    @Test
    void signsExactReservedLengthWithoutBindingContentType() {
        try (S3Presigner signer =
                S3Presigner.builder()
                        .region(Region.AP_SOUTHEAST_1)
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create("test-key", "test-secret")))
                        .build()) {
            var storage = new S3StorageService(mock(S3Client.class), signer, 900);
            var result = storage.createUploadUrl("test-bucket", "documents/test.pdf", "PDF", 123L);
            String query = URLDecoder.decode(result.url().getQuery(), StandardCharsets.UTF_8);
            assertTrue(query.contains("X-Amz-SignedHeaders=content-length;host"), query);
            assertFalse(query.contains("content-type"));
            assertEquals(900L, result.expiresInSeconds());
        }
    }
}
