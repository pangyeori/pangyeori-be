package com.debate.pangyeori.debate.exception
import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class DebateNotFoundException : BusinessException(
    errorCode = ErrorCode.DEBATE_NOT_FOUND,
)
