package com.debate.pangyeori.debate.exception
import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class AlreadyCancelledException : BusinessException(
    errorCode = ErrorCode.ALREADY_CANCELLED,
)
