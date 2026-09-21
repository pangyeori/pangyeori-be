package com.debate.pangyeori.notification.controller

import com.debate.pangyeori.common.dto.ApiResponse
import com.debate.pangyeori.common.dto.CursorPage
import com.debate.pangyeori.notification.dto.request.NotificationListRequest
import com.debate.pangyeori.notification.dto.response.NotificationListResponse
import com.debate.pangyeori.notification.dto.response.NotificationUnreadCountResponse
import com.debate.pangyeori.notification.service.NotificationService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notificationService: NotificationService,
) {
    @GetMapping
    fun getMyNotifications(
        principal: Principal,
        @Valid request: NotificationListRequest,
    ): ResponseEntity<ApiResponse<CursorPage<NotificationListResponse>>> {
        val response = notificationService.getMyNotifications(
            userEmail = principal.name,
            cursor = request.cursor,
            pageSize = request.pageSize,
        )

        return ResponseEntity.ok(
            ApiResponse.success(
                data = response,
            ),
        )
    }

    @GetMapping("/unread-count")
    fun getUnreadCount(
        principal: Principal,
    ): ResponseEntity<ApiResponse<NotificationUnreadCountResponse>> {
        val response = notificationService.getUnreadCount(
            userEmail = principal.name,
        )

        return ResponseEntity.ok(
            ApiResponse.success(
                data = response,
            ),
        )
    }

    @PatchMapping("/{notificationId}/read")
    fun markAsRead(
        principal: Principal,
        @PathVariable notificationId: String,
    ): ResponseEntity<ApiResponse<Unit>> {
        notificationService.markAsRead(
            userEmail = principal.name,
            notificationId = notificationId,
        )

        return ResponseEntity.ok(
            ApiResponse.success(
                data = Unit,
            ),
        )
    }

    @PatchMapping("/read-all")
    fun markAllAsRead(
        principal: Principal,
    ): ResponseEntity<ApiResponse<Unit>> {
        notificationService.markAllAsRead(
            userEmail = principal.name,
        )

        return ResponseEntity.ok(
            ApiResponse.success(
                data = Unit,
            ),
        )
    }

    @DeleteMapping("/{notificationId}")
    fun deleteNotification(
        principal: Principal,
        @PathVariable notificationId: String,
    ): ResponseEntity<Void> {
        notificationService.deleteNotification(
            userEmail = principal.name,
            notificationId = notificationId,
        )

        return ResponseEntity.noContent().build()
    }
}
