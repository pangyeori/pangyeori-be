package com.debate.pangyeori.user.exception

import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class EmailAlreadyExistsException : BusinessException(
    errorCode = ErrorCode.EMAIL_ALREADY_EXISTS,
)
