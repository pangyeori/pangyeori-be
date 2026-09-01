package com.debate.pangyeori.storage.service

import com.debate.pangyeori.storage.client.ObjectStorage
import com.debate.pangyeori.storage.exception.PresignFailedException
import com.debate.pangyeori.storage.exception.UnsupportedContentTypeException
import com.debate.pangyeori.storage.policy.StorageCategory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.mockk.every
import io.mockk.mockk
import software.amazon.awssdk.core.exception.SdkClientException
import java.time.Duration

class StorageServiceTest : BehaviorSpec({
    val objectStorage = mockk<ObjectStorage>()

    val uploadExpiry = Duration.ofMinutes(5)
    val downloadExpiry = Duration.ofMinutes(10)

    val storageService = StorageService(
        objectStorage = objectStorage,
        uploadExpiry = uploadExpiry,
        downloadExpiry = downloadExpiry,
    )

    Given("업로드 URL 발급 요청이 오면") {
        When("카테고리가 허용하는 콘텐츠 타입이면") {
            Then("카테고리 prefix와 날짜 파티션, 확장자를 붙인 오브젝트 키와 업로드 URL을 반환한다") {
                every {
                    objectStorage.createUploadUrl(
                        objectKey = any(),
                        contentType = "image/png",
                        expiry = uploadExpiry,
                    )
                } returns "https://s3.example/upload"

                val response = storageService.createUploadUrl(
                    category = StorageCategory.PROFILE_IMAGE,
                    contentType = "image/png",
                )

                response.objectKey shouldMatch Regex("""^profile-images/\d{4}/\d{2}/[0-9A-Za-z]+\.png$""")
                response.uploadUrl shouldBe "https://s3.example/upload"
                response.expiresInSeconds shouldBe 300
            }
        }

        When("카테고리가 허용하지 않는 콘텐츠 타입이면") {
            Then("UnsupportedContentTypeException을 던진다") {
                shouldThrow<UnsupportedContentTypeException> {
                    storageService.createUploadUrl(
                        category = StorageCategory.PROFILE_IMAGE,
                        contentType = "image/gif",
                    )
                }
            }
        }

        When("오브젝트 스토리지 호출이 SdkException으로 실패하면") {
            Then("PresignFailedException을 던진다") {
                every {
                    objectStorage.createUploadUrl(
                        objectKey = any(),
                        contentType = any(),
                        expiry = any(),
                    )
                } throws SdkClientException.create("presign failed")

                shouldThrow<PresignFailedException> {
                    storageService.createUploadUrl(
                        category = StorageCategory.PROFILE_IMAGE,
                        contentType = "image/png",
                    )
                }
            }
        }
    }

    Given("조회 URL 발급 요청이 오면") {
        val objectKey = "profile-images/2026/09/0000000000001.png"

        When("오브젝트 키가 주어지면") {
            Then("조회 URL과 만료 시간을 반환한다") {
                every {
                    objectStorage.createViewUrl(
                        objectKey = objectKey,
                        expiry = downloadExpiry,
                    )
                } returns "https://s3.example/view"

                val response = storageService.createViewUrl(
                    objectKey = objectKey,
                )

                response.url shouldBe "https://s3.example/view"
                response.expiresInSeconds shouldBe 600
            }
        }

        When("오브젝트 스토리지 호출이 SdkException으로 실패하면") {
            Then("PresignFailedException을 던진다") {
                every {
                    objectStorage.createViewUrl(
                        objectKey = any(),
                        expiry = any(),
                    )
                } throws SdkClientException.create("presign failed")

                shouldThrow<PresignFailedException> {
                    storageService.createViewUrl(
                        objectKey = objectKey,
                    )
                }
            }
        }
    }
})
