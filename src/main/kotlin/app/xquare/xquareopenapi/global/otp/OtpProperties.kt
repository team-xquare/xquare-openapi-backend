package app.xquare.xquareopenapi.global.otp

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

// 이메일 OTP 남용 제한. 기본값을 두지 않고 application.yaml에서 모두 받는다.
// 횟수 창은 고정 창이다. 창 안의 첫 발송(오입력)부터 창 길이만큼 센다.
@ConfigurationProperties(prefix = "otp")
data class OtpProperties(
    val resendCooldown: Duration,
    val sendWindow: Duration,
    val maxSendsPerEmail: Int,
    val maxSendsPerIp: Int,
    val attemptWindow: Duration,
    val maxAttempts: Int,
)
