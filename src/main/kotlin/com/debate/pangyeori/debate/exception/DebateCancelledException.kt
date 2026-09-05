package com.debate.pangyeori.debate.exception
import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class DebateCancelledException : BusinessException(
    errorCode = ErrorCode.DEBATE_CANCELLED,
)
