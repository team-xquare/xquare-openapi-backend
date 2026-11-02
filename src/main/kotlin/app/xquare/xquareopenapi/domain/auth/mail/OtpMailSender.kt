package app.xquare.xquareopenapi.domain.auth.mail

interface OtpMailSender {
    fun send(email: String, otp: String)
}
