package com.debate.pangyeori.storage.client

import java.time.Duration

interface ObjectStorage {
    fun createUploadUrl(
        objectKey: String,
        contentType: String,
        expiry: Duration,
    ): String

    fun createViewUrl(
        objectKey: String,
        expiry: Duration,
    ): String
}
