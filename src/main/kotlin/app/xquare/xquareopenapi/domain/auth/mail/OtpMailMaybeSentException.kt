package app.xquare.xquareopenapi.domain.auth.mail

// 요청을 보낸 뒤 응답을 받지 못해(읽기 타임아웃 등) 메일 서비스가 메일을 받아들였는지 알 수 없을 때 던진다.
// 이때는 사용자가 메일을 받았을 수 있으므로 OTP를 지우지 않는다.
class OtpMailMaybeSentException(cause: Throwable) : RuntimeException("메일이 발송됐는지 알 수 없습니다.", cause)
