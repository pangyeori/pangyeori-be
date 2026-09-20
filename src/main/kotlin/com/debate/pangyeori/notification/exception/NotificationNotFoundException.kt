package com.debate.pangyeori.notification.exception

import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class NotificationNotFoundException : BusinessException(
    errorCode = ErrorCode.NOTIFICATION_NOT_FOUND,
)
