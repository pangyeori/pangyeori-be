package com.debate.pangyeori.debate.exception
import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class DebateInProgressException : BusinessException(
    errorCode = ErrorCode.DEBATE_IN_PROGRESS,
)
