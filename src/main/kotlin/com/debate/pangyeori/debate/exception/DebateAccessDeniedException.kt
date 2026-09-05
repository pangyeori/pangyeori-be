package com.debate.pangyeori.debate.exception

import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class DebateAccessDeniedException : BusinessException(
    errorCode = ErrorCode.UNAUTHORIZED_ACCESS,
)
