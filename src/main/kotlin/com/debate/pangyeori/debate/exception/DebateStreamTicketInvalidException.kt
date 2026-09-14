package com.debate.pangyeori.debate.exception

import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class DebateStreamTicketInvalidException : BusinessException(
    errorCode = ErrorCode.DEBATE_STREAM_TICKET_INVALID,
)
