package app.xquare.xquareopenapi.domain.auth.mail

import app.xquare.xquareopenapi.global.mail.ResendProperties
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException

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
        try {
            restClient.post()
                .uri("/emails")
                .header("Authorization", "Bearer ${properties.apiKey}")
                .body(mapOf("from" to properties.from, "to" to listOf(email), "subject" to "[XQUARE] 이메일 인증코드", "html" to html))
                .retrieve()
                .toBodilessEntity()
        } catch (e: ResourceAccessException) {
            // 연결 자체를 못 했으면 요청이 나가지 않았다. 그 밖의 I/O 오류(읽기 타임아웃, 응답 전 연결 끊김 등)는
            // Resend가 요청을 받아들인 뒤일 수 있다. 연결 타임아웃도 읽기 타임아웃과 같은 예외라 함께 '알 수 없음'으로 둔다.
            if (isNotSent(e.cause)) throw e
            throw OtpMailMaybeSentException(e)
        }
    }

    private fun isNotSent(cause: Throwable?): Boolean =
        cause is ConnectException || cause is UnknownHostException || cause is NoRouteToHostException
}
