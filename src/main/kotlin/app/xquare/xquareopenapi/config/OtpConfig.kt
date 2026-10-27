package app.xquare.xquareopenapi.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.security.SecureRandom

@Configuration
class OtpConfig {
    @Bean
    fun otpSecureRandom(): SecureRandom = SecureRandom()
}
