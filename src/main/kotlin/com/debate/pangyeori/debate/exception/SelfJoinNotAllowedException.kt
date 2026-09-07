package com.debate.pangyeori.debate.exception
import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class SelfJoinNotAllowedException : BusinessException(
    errorCode = ErrorCode.SELF_JOIN_NOT_ALLOWED,
)
