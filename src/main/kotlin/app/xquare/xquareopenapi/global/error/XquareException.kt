package app.xquare.xquareopenapi.global.error

class XquareException(val errorCode: ErrorCode) : RuntimeException(errorCode.message)
