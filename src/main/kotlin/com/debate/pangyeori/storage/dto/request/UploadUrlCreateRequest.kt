package com.debate.pangyeori.storage.dto.request

import com.debate.pangyeori.storage.policy.StorageCategory
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class UploadUrlCreateRequest(
    @field:NotNull(message = "카테고리는 필수입니다.")
    val category: StorageCategory?,

    @field:NotBlank(message = "콘텐츠 타입은 필수입니다.")
    val contentType: String?,
)
