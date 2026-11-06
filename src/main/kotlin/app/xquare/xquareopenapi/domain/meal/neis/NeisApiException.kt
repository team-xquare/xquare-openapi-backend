package app.xquare.xquareopenapi.domain.meal.neis

// 나이스가 정상(INFO-000)이나 데이터 없음(INFO-200)이 아닌 결과 코드를 줬을 때 던진다(예: ERROR-290 인증키 오류).
class NeisApiException(code: String, message: String) : RuntimeException("$code $message")
