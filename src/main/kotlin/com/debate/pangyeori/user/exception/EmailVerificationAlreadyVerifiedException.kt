package com.debate.pangyeori.user.exception

import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class EmailVerificationAlreadyVerifiedException : BusinessException(
    errorCode = ErrorCode.EMAIL_VERIFICATION_ALREADY_VERIFIED,
    message = ErrorCode.EMAIL_VERIFICATION_ALREADY_VERIFIED.message,
)
