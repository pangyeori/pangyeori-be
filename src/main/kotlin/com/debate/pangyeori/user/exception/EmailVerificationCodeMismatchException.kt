package com.debate.pangyeori.user.exception

import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class EmailVerificationCodeMismatchException : BusinessException(
    errorCode = ErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH,
)
