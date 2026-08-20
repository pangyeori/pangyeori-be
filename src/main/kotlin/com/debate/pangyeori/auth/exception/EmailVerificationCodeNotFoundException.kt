package com.debate.pangyeori.auth.exception

import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class EmailVerificationCodeNotFoundException : BusinessException(
    errorCode = ErrorCode.EMAIL_VERIFICATION_CODE_NOT_FOUND,
)
