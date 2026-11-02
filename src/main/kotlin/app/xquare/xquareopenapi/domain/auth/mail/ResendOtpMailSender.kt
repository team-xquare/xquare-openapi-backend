package app.xquare.xquareopenapi.domain.auth.mail

import app.xquare.xquareopenapi.global.mail.ResendProperties
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context

@Component
class ResendOtpMailSender(
    private val properties: ResendProperties,
    private val templateEngine: TemplateEngine,
) : OtpMailSender {
    private val requestFactory = SimpleClientHttpRequestFactory()
    private val restClient: RestClient

    init {
        requestFactory.setConnectTimeout(properties.connectTimeout)
        requestFactory.setReadTimeout(properties.readTimeout)
        restClient = RestClient.builder().baseUrl(properties.baseUrl).requestFactory(requestFactory).build()
    }

    override fun send(email: String, otp: String) {
        if (properties.apiKey.isBlank()) {
            throw IllegalStateException("Resend API 키가 설정되지 않았습니다.")
        }
        val context = Context()
        context.setVariable("otp", otp)
        val html = templateEngine.process("email/otp", context)
        restClient.post()
            .uri("/emails")
            .header("Authorization", "Bearer ${properties.apiKey}")
            .body(mapOf("from" to properties.from, "to" to listOf(email), "subject" to "[XQUARE] 이메일 인증코드", "html" to html))
            .retrieve()
            .toBodilessEntity()
    }
}
