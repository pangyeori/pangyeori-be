package com.debate.pangyeori.user.exception

import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class UserNotFoundException : BusinessException(
    errorCode = ErrorCode.USER_NOT_FOUND,
)
