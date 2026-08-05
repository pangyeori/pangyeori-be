package com.debate.pangyeori.user.exception

import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class EmailNotVerifiedException : BusinessException(
    errorCode = ErrorCode.EMAIL_NOT_VERIFIED,
)
