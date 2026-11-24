package app.xquare.xquareopenapi.config

import app.xquare.xquareopenapi.domain.apikey.ApiKeyAuthenticationFilter
import app.xquare.xquareopenapi.domain.apikey.ApiKeyField
import app.xquare.xquareopenapi.domain.apikey.ApiProjectFieldRepository
import app.xquare.xquareopenapi.domain.apikey.ApiProjectRepository
import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.global.cors.CorsProperties
import app.xquare.xquareopenapi.global.error.SecurityErrorResponder
import app.xquare.xquareopenapi.global.jwt.JwtAuthenticationFilter
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import jakarta.servlet.DispatcherType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.DispatcherTypeRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class SecurityConfig {

    // 조회 API는 API 키로만 인증한다. JWT 필터가 없는 체인을 따로 둬서 토큰이 있어도 키 없이는 401이다.
    // 키가 맞아도 경로의 분야(API_KEY_READ_PATHS)가 승인되지 않았으면 403 API_KEY_FIELD_FORBIDDEN이다.
    @Bean
    @Order(1)
    fun apiKeySecurityFilterChain(
        http: HttpSecurity,
        projectRepository: ApiProjectRepository,
        fieldRepository: ApiProjectFieldRepository,
        securityErrorResponder: SecurityErrorResponder,
    ): SecurityFilterChain {
        http {
            securityMatcher(*API_KEY_READ_ENDPOINTS.map { it.first }.toTypedArray())
            csrf { disable() }
            cors { }
            formLogin { disable() }
            httpBasic { disable() }
            sessionManagement {
                sessionCreationPolicy = SessionCreationPolicy.STATELESS
            }
            authorizeHttpRequests {
                API_KEY_READ_ENDPOINTS.forEach { (matcher, field) -> authorize(matcher, hasAuthority(field.authority)) }
            }
            exceptionHandling {
                authenticationEntryPoint = securityErrorResponder.invalidApiKey
                accessDeniedHandler = securityErrorResponder.apiKeyFieldForbidden
            }
            addFilterBefore<UsernamePasswordAuthenticationFilter>(
                ApiKeyAuthenticationFilter(projectRepository, fieldRepository),
            )
        }
        return http.build()
    }

    @Bean
    @Order(2)
    fun securityFilterChain(
        http: HttpSecurity,
        jwtProvider: JwtProvider,
        securityErrorResponder: SecurityErrorResponder,
    ): SecurityFilterChain {
        http {
            csrf { disable() }
            cors { }
            formLogin { disable() }
            httpBasic { disable() }
            sessionManagement {
                sessionCreationPolicy = SessionCreationPolicy.STATELESS
            }
            authorizeHttpRequests {
                authorize(HttpMethod.POST, "/auth/email/otp", permitAll)
                authorize(HttpMethod.POST, "/auth/email/otp/verify", permitAll)
                authorize(HttpMethod.POST, "/auth/signup", permitAll)
                authorize(HttpMethod.POST, "/auth/login", permitAll)
                authorize(HttpMethod.POST, "/auth/reissue", permitAll)
                authorize(HttpMethod.POST, "/auth/password/reset", permitAll)
                // Swagger UI와 API 문서(springdoc)
                authorize(HttpMethod.GET, "/swagger-ui.html", permitAll)
                authorize(HttpMethod.GET, "/swagger-ui/**", permitAll)
                authorize(HttpMethod.GET, "/v3/api-docs/**", permitAll)
                // 급식 조회는 공개(ALL)다. 토큰이 없거나 유효하지 않아도 401 없이 조회된다.
                authorize(HttpMethod.GET, "/meals", permitAll)
                authorize(HttpMethod.GET, "/meals/*", permitAll)
                // 처리되지 않은 오류는 /error로 포워드된다. 이걸 막으면 415·500 같은 원래 응답이 전부 401로 바뀐다.
                authorize(DispatcherTypeRequestMatcher(DispatcherType.ERROR), permitAll)
                authorize(HttpMethod.POST, "/students", hasRole(ADMIN))
                authorize(HttpMethod.PATCH, "/students/*", hasRole(ADMIN))
                authorize(HttpMethod.DELETE, "/students/*", hasRole(ADMIN))
                authorize(HttpMethod.POST, "/teachers", hasRole(ADMIN))
                authorize(HttpMethod.PATCH, "/teachers/*", hasRole(ADMIN))
                authorize(HttpMethod.DELETE, "/teachers/*", hasRole(ADMIN))
                authorize(HttpMethod.POST, "/meals", hasRole(ADMIN))
                authorize(HttpMethod.PATCH, "/meals/*", hasRole(ADMIN))
                authorize(HttpMethod.POST, "/schedules", hasRole(ADMIN))
                authorize(HttpMethod.PATCH, "/schedules/*", hasRole(ADMIN))
                authorize(HttpMethod.DELETE, "/schedules/*", hasRole(ADMIN))
                authorize(HttpMethod.POST, "/clubs", hasRole(ADMIN))
                authorize(HttpMethod.PATCH, "/clubs/*", hasRole(ADMIN))
                authorize(HttpMethod.DELETE, "/clubs/*", hasRole(ADMIN))
                authorize(HttpMethod.POST, "/study-supervisors", hasRole(ADMIN))
                authorize(HttpMethod.PATCH, "/study-supervisors/*", hasRole(ADMIN))
                authorize(HttpMethod.DELETE, "/study-supervisors/*", hasRole(ADMIN))
                authorize(HttpMethod.POST, "/notices", hasRole(ADMIN))
                authorize(HttpMethod.PATCH, "/notices/*", hasRole(ADMIN))
                authorize(HttpMethod.DELETE, "/notices/*", hasRole(ADMIN))
                // 메서드를 한정하지 않아 Spring MVC가 GET에 붙이는 HEAD도 관리자만 호출한다.
                authorize("/admin/api-usage-logs", hasRole(ADMIN))
                // 나머지는 로그인만 하면 된다(USER, ADMIN). 조회 API는 위의 API 키 체인이 먼저 가져간다.
                authorize(anyRequest, authenticated)
            }
            exceptionHandling {
                authenticationEntryPoint = securityErrorResponder
                accessDeniedHandler = securityErrorResponder
            }
            // 빈으로 등록하면 Spring Boot가 서블릿 필터로도 등록해 Security 밖에서 한 번 더 실행되므로 직접 만들어 넣는다.
            addFilterBefore<UsernamePasswordAuthenticationFilter>(JwtAuthenticationFilter(jwtProvider))
        }
        return http.build()
    }

    // cors { }가 이 빈을 찾아 쓴다. preflight는 인증 검사보다 먼저 처리된다.
    @Bean
    fun corsConfigurationSource(corsProperties: CorsProperties): CorsConfigurationSource =
        UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", corsProperties.toCorsConfiguration())
        }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    companion object {
        private val ADMIN = Role.ADMIN.name

        // API 키로만 여는 조회 경로와 그 경로를 열 수 있는 분야. SwaggerConfig도 이 목록으로 문서의 인증 방식을 정한다.
        // 조회 API가 새로 생기면 한 줄씩 넣는다. 예: "/clubs" to ApiKeyField.CLUB, "/study-supervisors/day" to ApiKeyField.STUDY_SUPERVISOR
        val API_KEY_READ_PATHS: Map<String, ApiKeyField> = linkedMapOf(
            "/students" to ApiKeyField.STUDENT,
            "/students/*" to ApiKeyField.STUDENT,
            "/teachers" to ApiKeyField.TEACHER,
            "/teachers/*" to ApiKeyField.TEACHER,
        )

        // Spring MVC는 GET 매핑에 HEAD도 자동으로 응답하므로 HEAD도 같이 잡는다. 빠지면 토큰만으로 존재 여부가 드러난다.
        private val API_KEY_READ_ENDPOINTS: List<Pair<RequestMatcher, ApiKeyField>> = API_KEY_READ_PATHS
            .flatMap { (path, field) ->
                listOf(HttpMethod.GET, HttpMethod.HEAD).map { PathPatternRequestMatcher.withDefaults().matcher(it, path) to field }
            }
    }
}
