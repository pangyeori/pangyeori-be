package com.debate.pangyeori.auth.exception

import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class PasswordResetTokenNotFoundException : BusinessException(
    errorCode = ErrorCode.PASSWORD_RESET_TOKEN_NOT_FOUND,
)
