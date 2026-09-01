package com.debate.pangyeori.storage.client

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Duration

@Component
@Profile("local")
class LoggingObjectStorage : ObjectStorage {

    private val logger = KotlinLogging.logger {}

    override fun createUploadUrl(
        objectKey: String,
        contentType: String,
        contentLength: Long,
        expiry: Duration,
    ): String {
        logger.info { "[local] 업로드 URL 발급 생략. objectKey=$objectKey, contentType=$contentType, contentLength=$contentLength" }
        return "$LOCAL_BASE_URL/$objectKey"
    }

    override fun createViewUrl(
        objectKey: String,
        expiry: Duration,
    ): String {
        logger.info { "[local] 조회 URL 발급 생략. objectKey=$objectKey" }
        return "$LOCAL_BASE_URL/$objectKey"
    }

    companion object {
        private const val LOCAL_BASE_URL = "https://local.example"
    }
}
