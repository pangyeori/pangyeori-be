package com.debate.pangyeori.storage.client

import java.time.Duration

interface ObjectStorage {
    fun createUploadUrl(
        objectKey: String,
        contentType: String,
        contentLength: Long,
        expiry: Duration,
    ): String

    fun createViewUrl(
        objectKey: String,
        expiry: Duration,
    ): String

    fun deleteObject(
        objectKey: String,
    )
}
