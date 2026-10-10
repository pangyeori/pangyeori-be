package com.debate.pangyeori.debate.exception

import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class DebateHistoryAlreadySubmittedException : BusinessException(
    errorCode = ErrorCode.DEBATE_HISTORY_ALREADY_SUBMITTED,
)
