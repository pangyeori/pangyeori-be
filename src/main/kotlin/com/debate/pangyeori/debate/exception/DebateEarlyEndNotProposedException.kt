package com.debate.pangyeori.debate.exception

import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class DebateEarlyEndNotProposedException : BusinessException(
    errorCode = ErrorCode.DEBATE_EARLY_END_NOT_PROPOSED,
)
