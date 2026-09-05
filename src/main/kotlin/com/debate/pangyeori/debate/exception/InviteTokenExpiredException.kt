package com.debate.pangyeori.debate.exception
import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class InviteTokenExpiredException : BusinessException(
    errorCode = ErrorCode.INVITE_TOKEN_EXPIRED,
)
