package com.debate.pangyeori.notification.domain.converter

import com.debate.pangyeori.common.converter.CodeEnumConverter
import com.debate.pangyeori.notification.domain.enums.NotificationType
import jakarta.persistence.Converter

@Converter(autoApply = true)
class NotificationTypeConverter : CodeEnumConverter<NotificationType>(
    enumConstants = NotificationType.entries.toTypedArray(),
)
