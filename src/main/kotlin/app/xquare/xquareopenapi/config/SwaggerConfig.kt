package app.xquare.xquareopenapi.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// 대부분의 API가 액세스 토큰을 요구하므로, Swagger UI의 Authorize 버튼으로 토큰을 넣어 호출할 수 있게 한다.
@Configuration
class SwaggerConfig {

    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .components(
                Components().addSecuritySchemes(
                    BEARER_AUTH,
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"),
                ),
            )
            .addSecurityItem(SecurityRequirement().addList(BEARER_AUTH))

    companion object {
        private const val BEARER_AUTH = "bearerAuth"
    }
}
