package com.debate.pangyeori.storage.client

import java.time.Duration

class FakeObjectStorage : ObjectStorage {

    override fun createUploadUrl(
        objectKey: String,
        contentType: String,
        contentLength: Long,
        expiry: Duration,
    ): String = "$FAKE_BASE_URL/$objectKey"

    override fun createViewUrl(
        objectKey: String,
        expiry: Duration,
    ): String = "$FAKE_BASE_URL/$objectKey"

    override fun deleteObject(
        objectKey: String,
    ) {
        // 테스트에서는 실제로 삭제하지 않는다
    }

    companion object {
        private const val FAKE_BASE_URL = "https://fake.s3"
    }
}
