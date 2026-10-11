package com.debate.pangyeori.debate.exception

import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class DebateNotYourTurnException : BusinessException(
    errorCode = ErrorCode.DEBATE_NOT_YOUR_TURN,
)
