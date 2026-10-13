package com.debate.pangyeori.terms.exception

import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class RequiredTermsWithdrawalNotAllowedException : BusinessException(
    errorCode = ErrorCode.REQUIRED_TERMS_WITHDRAWAL_NOT_ALLOWED,
)
