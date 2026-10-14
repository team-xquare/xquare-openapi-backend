package app.xquare.xquareopenapi.global.error

class BusinessException(val errorCode: ErrorCode) : RuntimeException(errorCode.message)
