package com.debate.pangyeori.storage.exception

import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class FileTooLargeException : BusinessException(
    errorCode = ErrorCode.FILE_TOO_LARGE,
)
