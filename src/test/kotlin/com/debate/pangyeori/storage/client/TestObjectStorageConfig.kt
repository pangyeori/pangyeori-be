package com.debate.pangyeori.storage.client

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class TestObjectStorageConfig {

    @Bean
    @Primary
    fun objectStorage(): ObjectStorage = FakeObjectStorage()
}
