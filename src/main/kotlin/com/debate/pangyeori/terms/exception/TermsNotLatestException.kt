package com.debate.pangyeori.terms.exception

import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class TermsNotLatestException : BusinessException(
    errorCode = ErrorCode.TERMS_NOT_LATEST,
)
