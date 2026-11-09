package app.xquare.xquareopenapi.config

import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.global.cors.CorsProperties
import app.xquare.xquareopenapi.global.error.SecurityErrorResponder
import app.xquare.xquareopenapi.global.jwt.JwtAuthenticationFilter
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import jakarta.servlet.DispatcherType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.util.matcher.DispatcherTypeRequestMatcher
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class SecurityConfig {

    @Bean
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
                authorize(HttpMethod.POST, "/auth/signup", permitAll)
                authorize(HttpMethod.POST, "/auth/login", permitAll)
                authorize(HttpMethod.POST, "/auth/reissue", permitAll)
                // 처리되지 않은 오류는 /error로 포워드된다. 이걸 막으면 415·500 같은 원래 응답이 전부 401로 바뀐다.
                authorize(DispatcherTypeRequestMatcher(DispatcherType.ERROR), permitAll)
                authorize(HttpMethod.POST, "/students", hasRole(ADMIN))
                authorize(HttpMethod.PATCH, "/students/*", hasRole(ADMIN))
                authorize(HttpMethod.DELETE, "/students/*", hasRole(ADMIN))
                // 나머지는 로그인만 하면 된다(USER, ADMIN).
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
    }
}
