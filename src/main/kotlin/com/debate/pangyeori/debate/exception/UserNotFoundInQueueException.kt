package com.debate.pangyeori.debate.exception
import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class UserNotFoundInQueueException : BusinessException(
    errorCode = ErrorCode.USER_NOT_FOUND_IN_QUEUE,
)
