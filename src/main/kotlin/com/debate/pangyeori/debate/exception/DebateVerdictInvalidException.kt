package com.debate.pangyeori.debate.exception

import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class DebateVerdictInvalidException : BusinessException(
    errorCode = ErrorCode.DEBATE_VERDICT_INVALID,
)
