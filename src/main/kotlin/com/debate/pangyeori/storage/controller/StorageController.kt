package com.debate.pangyeori.storage.controller

import com.debate.pangyeori.common.dto.ApiResponse
import com.debate.pangyeori.storage.dto.request.UploadUrlCreateRequest
import com.debate.pangyeori.storage.dto.request.ViewUrlRequest
import com.debate.pangyeori.storage.dto.response.UploadUrlResponse
import com.debate.pangyeori.storage.dto.response.ViewUrlResponse
import com.debate.pangyeori.storage.service.StorageService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/storage")
class StorageController(
    private val storageService: StorageService,
) {

    @PostMapping("/upload-urls")
    fun createUploadUrl(
        @RequestBody @Valid request: UploadUrlCreateRequest,
    ): ResponseEntity<ApiResponse<UploadUrlResponse>> {
        val response = storageService.createUploadUrl(
            category = request.category!!,
            contentType = request.contentType!!,
            contentLength = request.contentLength!!,
        )

        return ResponseEntity.ok(
            ApiResponse.success(
                data = response,
            ),
        )
    }

    @GetMapping("/view-urls")
    fun createViewUrl(
        @Valid request: ViewUrlRequest,
    ): ResponseEntity<ApiResponse<ViewUrlResponse>> {
        val response = storageService.createViewUrl(
            objectKey = request.objectKey!!,
        )

        return ResponseEntity.ok(
            ApiResponse.success(
                data = response,
            ),
        )
    }
}
