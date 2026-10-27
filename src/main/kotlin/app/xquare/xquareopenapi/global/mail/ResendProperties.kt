package app.xquare.xquareopenapi.global.mail

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "resend")
data class ResendProperties(
    val apiKey: String,
    val from: String,
    val baseUrl: String,
    val connectTimeout: Duration = Duration.ofSeconds(5),
    val readTimeout: Duration = Duration.ofSeconds(10),
)
