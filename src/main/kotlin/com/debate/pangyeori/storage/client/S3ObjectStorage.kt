package com.debate.pangyeori.storage.client

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration

@Component
@Profile("!local")
class S3ObjectStorage(
    private val s3Presigner: S3Presigner,
    @param:Value("\${aws.s3.bucket}") private val bucket: String,
) : ObjectStorage {

    override fun createUploadUrl(
        objectKey: String,
        contentType: String,
        expiry: Duration,
    ): String {
        val putObjectRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(objectKey)
            .contentType(contentType)
            .build()
        val presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(expiry)
            .putObjectRequest(putObjectRequest)
            .build()
        return s3Presigner.presignPutObject(presignRequest).url().toString()
    }

    override fun createViewUrl(
        objectKey: String,
        expiry: Duration,
    ): String {
        val getObjectRequest = GetObjectRequest.builder()
            .bucket(bucket)
            .key(objectKey)
            .build()
        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(expiry)
            .getObjectRequest(getObjectRequest)
            .build()
        return s3Presigner.presignGetObject(presignRequest).url().toString()
    }
}
