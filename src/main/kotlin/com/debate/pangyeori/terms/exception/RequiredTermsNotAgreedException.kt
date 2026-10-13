package com.debate.pangyeori.terms.exception

import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class RequiredTermsNotAgreedException : BusinessException(
    errorCode = ErrorCode.REQUIRED_TERMS_NOT_AGREED,
)
