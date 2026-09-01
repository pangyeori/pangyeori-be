package com.debate.pangyeori.storage.service

import com.debate.pangyeori.storage.client.ObjectStorage
import com.debate.pangyeori.storage.dto.response.UploadUrlResponse
import com.debate.pangyeori.storage.dto.response.ViewUrlResponse
import com.debate.pangyeori.storage.exception.PresignFailedException
import com.debate.pangyeori.storage.exception.UnsupportedContentTypeException
import com.debate.pangyeori.storage.policy.StorageCategory
import io.github.oshai.kotlinlogging.KotlinLogging
import io.hypersistence.tsid.TSID
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.exception.SdkException
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Service
class StorageService(
    private val objectStorage: ObjectStorage,
    @param:Value("\${aws.s3.presign.upload-expiry}") private val uploadExpiry: Duration,
    @param:Value("\${aws.s3.presign.download-expiry}") private val downloadExpiry: Duration,
) {

    private val logger = KotlinLogging.logger {}

    fun createUploadUrl(
        category: StorageCategory,
        contentType: String,
    ): UploadUrlResponse {
        val extension = category.extensionFor(
            contentType = contentType,
        ) ?: throw UnsupportedContentTypeException()
        val datePath = LocalDate.now(ZoneOffset.UTC).format(DATE_PATH_FORMAT)
        val objectId = TSID.fast().toString()
        val objectKey = "${category.prefix}/$datePath/$objectId.$extension"

        val uploadUrl = try {
            objectStorage.createUploadUrl(
                objectKey = objectKey,
                contentType = contentType,
                expiry = uploadExpiry,
            )
        } catch (e: SdkException) {
            logger.error(e) { "업로드 URL 발급에 실패했습니다. objectKey=$objectKey" }
            throw PresignFailedException()
        }

        return UploadUrlResponse(
            objectKey = objectKey,
            uploadUrl = uploadUrl,
            expiresInSeconds = uploadExpiry.seconds,
        )
    }

    fun createViewUrl(
        objectKey: String,
    ): ViewUrlResponse {
        val url = try {
            objectStorage.createViewUrl(
                objectKey = objectKey,
                expiry = downloadExpiry,
            )
        } catch (e: SdkException) {
            logger.error(e) { "조회 URL 발급에 실패했습니다. objectKey=$objectKey" }
            throw PresignFailedException()
        }

        return ViewUrlResponse(
            url = url,
            expiresInSeconds = downloadExpiry.seconds,
        )
    }

    companion object {
        private val DATE_PATH_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM")
    }
}
