package app.xquare.xquareopenapi.config

import app.xquare.xquareopenapi.domain.apikey.ApiKeyAuthenticationFilter
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.server.PathContainer
import org.springframework.web.util.pattern.PathPatternParser

// 대부분의 API가 액세스 토큰을 요구하므로, Swagger UI의 Authorize 버튼으로 토큰을 넣어 호출할 수 있게 한다.
// 조회 API는 API 키 체인이 받으므로 그 GET만 X-API-Key 헤더 방식으로 바꾼다. 토큰을 보내면 401이다.
@Configuration
class SwaggerConfig {

    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .components(
                Components()
                    .addSecuritySchemes(
                        BEARER_AUTH,
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT"),
                    )
                    .addSecuritySchemes(
                        API_KEY_AUTH,
                        SecurityScheme()
                            .type(SecurityScheme.Type.APIKEY)
                            .`in`(SecurityScheme.In.HEADER)
                            .name(ApiKeyAuthenticationFilter.HEADER),
                    ),
            )
            .addSecurityItem(SecurityRequirement().addList(BEARER_AUTH))

    @Bean
    fun apiKeyReadOperationCustomizer(): OpenApiCustomizer = OpenApiCustomizer { openApi ->
        val patterns = SecurityConfig.API_KEY_READ_PATHS.keys.map { PathPatternParser.defaultInstance.parse(it) }
        openApi.paths.orEmpty().forEach { (path, item) ->
            // 문서의 경로 변수 {studentId}도 한 세그먼트라 "/students/*"에 맞는다.
            if (patterns.any { it.matches(PathContainer.parsePath(path)) }) {
                item.get?.security = listOf(SecurityRequirement().addList(API_KEY_AUTH))
            }
        }
    }

    companion object {
        private const val BEARER_AUTH = "bearerAuth"
        private const val API_KEY_AUTH = "apiKeyAuth"
    }
}
