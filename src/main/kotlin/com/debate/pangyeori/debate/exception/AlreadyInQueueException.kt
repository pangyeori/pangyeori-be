package com.debate.pangyeori.debate.exception
import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class AlreadyInQueueException : BusinessException(
    errorCode = ErrorCode.ALREADY_IN_QUEUE,
)
