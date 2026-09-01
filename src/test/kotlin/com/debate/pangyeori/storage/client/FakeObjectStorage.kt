package com.debate.pangyeori.storage.client

import java.time.Duration

class FakeObjectStorage : ObjectStorage {

    override fun createUploadUrl(
        objectKey: String,
        contentType: String,
        expiry: Duration,
    ): String = "$FAKE_BASE_URL/$objectKey"

    override fun createViewUrl(
        objectKey: String,
        expiry: Duration,
    ): String = "$FAKE_BASE_URL/$objectKey"

    companion object {
        private const val FAKE_BASE_URL = "https://fake.s3"
    }
}
