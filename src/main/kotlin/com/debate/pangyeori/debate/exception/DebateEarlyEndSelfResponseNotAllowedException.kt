package com.debate.pangyeori.debate.exception

import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class DebateEarlyEndSelfResponseNotAllowedException : BusinessException(
    errorCode = ErrorCode.DEBATE_EARLY_END_SELF_RESPONSE_NOT_ALLOWED,
)
