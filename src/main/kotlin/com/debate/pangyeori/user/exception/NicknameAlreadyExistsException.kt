package com.debate.pangyeori.user.exception

import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class NicknameAlreadyExistsException : BusinessException(
    errorCode = ErrorCode.NICKNAME_ALREADY_EXISTS,
)
