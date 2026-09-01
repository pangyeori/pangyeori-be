package com.debate.pangyeori.storage.controller

import com.debate.pangyeori.auth.service.AuthService
import com.debate.pangyeori.storage.client.TestObjectStorageConfig
import com.debate.pangyeori.storage.policy.StorageCategory
import com.debate.pangyeori.support.RestDocsMvcTest
import com.debate.pangyeori.support.dsl.restDocs
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.security.crypto.password.PasswordEncoder

@Import(TestObjectStorageConfig::class)
class StorageControllerTest : RestDocsMvcTest() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var authService: AuthService

    private val maxUploadBytes = StorageCategory.PROFILE_IMAGE.maxUploadBytes

    private fun issueAccessToken(
        email: String = "storage-user@pangyeori.com",
    ): String {
        val password = "password123!"
        userRepository.save(
            User.create(
                email = email,
                password = passwordEncoder.encode(password)!!,
                nickname = "스토리지사용자",
                profileImageUrl = null,
            ),
        )
        return authService.signIn(
            email = email,
            password = password,
        ).accessToken
    }

    @Test
    fun `인증된 사용자가 업로드 URL 발급에 성공한다`() {
        val accessToken = issueAccessToken()

        restDocs(mockMvc, "storage/create-upload-url") {
            summary("업로드 URL 발급")
            tag("Storage")
            request {
                post("/api/v1/storage/upload-urls")
                header("Authorization", "Bearer $accessToken")
                body {
                    field("category", "PROFILE_IMAGE", "업로드 용도 (PROFILE_IMAGE)")
                    field("contentType", "image/png", "업로드할 파일의 콘텐츠 타입 (image/png, image/jpeg, image/webp)")
                    field("contentLength", maxUploadBytes, "업로드할 파일 크기 (바이트, 카테고리별 상한 이내)")
                }
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "발급된 업로드 URL 정보") {
                        field("objectKey", "저장 시 사용할 오브젝트 키")
                        field("uploadUrl", "S3에 PUT 요청할 pre-signed URL")
                        field("expiresInSeconds", "URL 유효 시간(초)")
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `인증 없이 업로드 URL을 요청하면 401을 반환한다`() {
        restDocs(mockMvc, "storage/create-upload-url-unauthorized") {
            summary("업로드 URL 발급")
            tag("Storage")
            request {
                post("/api/v1/storage/upload-urls")
                body {
                    field("category", "PROFILE_IMAGE", "업로드 용도")
                    field("contentType", "image/png", "업로드할 파일의 콘텐츠 타입")
                    field("contentLength", maxUploadBytes, "업로드할 파일 크기 (바이트)")
                }
            }
            response {
                status(401)
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    obj("error", "오류 정보") {
                        field("code", "오류 코드")
                        field("message", "오류 메시지")
                        field("details", "필드별 검증 오류 목록").optional()
                    }
                }
            }
        }
    }

    @Test
    fun `지원하지 않는 콘텐츠 타입이면 422를 반환한다`() {
        val accessToken = issueAccessToken()

        restDocs(mockMvc, "storage/create-upload-url-unsupported-content-type") {
            summary("업로드 URL 발급")
            tag("Storage")
            request {
                post("/api/v1/storage/upload-urls")
                header("Authorization", "Bearer $accessToken")
                body {
                    field("category", "PROFILE_IMAGE", "업로드 용도")
                    field("contentType", "image/gif", "카테고리가 허용하지 않는 콘텐츠 타입")
                    field("contentLength", maxUploadBytes, "업로드할 파일 크기 (바이트)")
                }
            }
            response {
                status(422)
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    obj("error", "오류 정보") {
                        field("code", "오류 코드")
                        field("message", "오류 메시지")
                        field("details", "필드별 검증 오류 목록").optional()
                    }
                }
            }
        }
    }

    @Test
    fun `필수 입력값이 누락되면 400을 반환한다`() {
        val accessToken = issueAccessToken()

        restDocs(mockMvc, "storage/create-upload-url-missing-fields") {
            summary("업로드 URL 발급")
            tag("Storage")
            request {
                post("/api/v1/storage/upload-urls")
                header("Authorization", "Bearer $accessToken")
                body {
                    rawJson("{}")
                }
            }
            response {
                status(400)
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    obj("error", "오류 정보") {
                        field("code", "오류 코드")
                        field("message", "오류 메시지")
                        array("details", "필드별 검증 오류 목록") {
                            field("field", "오류가 발생한 필드")
                            field("message", "필드 오류 메시지")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `유효하지 않은 카테고리면 400을 반환한다`() {
        val accessToken = issueAccessToken()

        restDocs(mockMvc, "storage/create-upload-url-invalid-category") {
            summary("업로드 URL 발급")
            tag("Storage")
            request {
                post("/api/v1/storage/upload-urls")
                header("Authorization", "Bearer $accessToken")
                body {
                    rawJson("""{"category":"UNKNOWN","contentType":"image/png","contentLength":$maxUploadBytes}""")
                }
            }
            response {
                status(400)
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    obj("error", "오류 정보") {
                        field("code", "오류 코드")
                        field("message", "오류 메시지")
                        field("details", "필드별 검증 오류 목록").optional()
                    }
                }
            }
        }
    }

    @Test
    fun `파일 크기가 카테고리 상한을 초과하면 422를 반환한다`() {
        val accessToken = issueAccessToken()

        restDocs(mockMvc, "storage/create-upload-url-file-too-large") {
            summary("업로드 URL 발급")
            tag("Storage")
            request {
                post("/api/v1/storage/upload-urls")
                header("Authorization", "Bearer $accessToken")
                body {
                    field("category", "PROFILE_IMAGE", "업로드 용도")
                    field("contentType", "image/png", "업로드할 파일의 콘텐츠 타입")
                    field("contentLength", maxUploadBytes + 1, "카테고리 상한을 초과하는 파일 크기")
                }
            }
            response {
                status(422)
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    obj("error", "오류 정보") {
                        field("code", "오류 코드")
                        field("message", "오류 메시지")
                        field("details", "필드별 검증 오류 목록").optional()
                    }
                }
            }
        }
    }

    @Test
    fun `파일 크기가 0 이하면 400을 반환한다`() {
        val accessToken = issueAccessToken()

        restDocs(mockMvc, "storage/create-upload-url-non-positive-length") {
            summary("업로드 URL 발급")
            tag("Storage")
            request {
                post("/api/v1/storage/upload-urls")
                header("Authorization", "Bearer $accessToken")
                body {
                    field("category", "PROFILE_IMAGE", "업로드 용도")
                    field("contentType", "image/png", "업로드할 파일의 콘텐츠 타입")
                    field("contentLength", 0, "0 이하의 잘못된 파일 크기")
                }
            }
            response {
                status(400)
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    obj("error", "오류 정보") {
                        field("code", "오류 코드")
                        field("message", "오류 메시지")
                        array("details", "필드별 검증 오류 목록") {
                            field("field", "오류가 발생한 필드")
                            field("message", "필드 오류 메시지")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `인증된 사용자가 조회 URL 발급에 성공한다`() {
        val accessToken = issueAccessToken()

        restDocs(mockMvc, "storage/create-view-url") {
            summary("조회 URL 발급")
            tag("Storage")
            request {
                get("/api/v1/storage/view-urls")
                header("Authorization", "Bearer $accessToken")
                queryParameters {
                    param("objectKey", "profile-images/2026/09/0000000000001.png", "조회할 오브젝트 키")
                }
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "발급된 조회 URL 정보") {
                        field("url", "S3에서 GET 요청할 pre-signed URL")
                        field("expiresInSeconds", "URL 유효 시간(초)")
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `오브젝트 키 형식이 올바르지 않으면 400을 반환한다`() {
        val accessToken = issueAccessToken()

        restDocs(mockMvc, "storage/create-view-url-invalid-object-key") {
            summary("조회 URL 발급")
            tag("Storage")
            request {
                get("/api/v1/storage/view-urls")
                header("Authorization", "Bearer $accessToken")
                queryParameters {
                    param("objectKey", "../secret.txt", "형식이 잘못된 오브젝트 키")
                }
            }
            response {
                status(400)
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    obj("error", "오류 정보") {
                        field("code", "오류 코드")
                        field("message", "오류 메시지")
                        array("details", "필드별 검증 오류 목록") {
                            field("field", "오류가 발생한 필드")
                            field("message", "필드 오류 메시지")
                        }
                    }
                }
            }
        }
    }
}
