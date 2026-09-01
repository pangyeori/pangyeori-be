package com.debate.pangyeori.storage.exception

import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class UnsupportedContentTypeException : BusinessException(
    errorCode = ErrorCode.UNSUPPORTED_CONTENT_TYPE,
)
