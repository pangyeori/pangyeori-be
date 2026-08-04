package com.debate.pangyeori.email.exception

import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class EmailSendFailedException : BusinessException(
    errorCode = ErrorCode.EMAIL_SEND_FAILED,
)
