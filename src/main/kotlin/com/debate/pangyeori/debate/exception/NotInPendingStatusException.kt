package com.debate.pangyeori.debate.exception
import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class NotInPendingStatusException : BusinessException(
    errorCode = ErrorCode.NOT_IN_PENDING_STATUS,
)
