package com.ktb4.team16.mulo.global.config;

import com.ktb4.team16.mulo.global.security.ApiAccessDeniedHandler;
import com.ktb4.team16.mulo.global.security.ApiAuthenticationEntryPoint;
import com.ktb4.team16.mulo.global.security.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({SecurityProperties.class, JwtProperties.class})
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProperties properties,
            ApiAuthenticationEntryPoint entryPoint, ApiAccessDeniedHandler deniedHandler,
            CorsConfigurationSource corsConfigurationSource,
            JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        // CSRF Cookie만 JS로 읽는다. 로그인 단계의 Refresh Cookie는 반드시 HttpOnly다.
        var csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie.path("/")
                .secure(properties.cookieSecure()).sameSite("Strict"));

        http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                // 인증 상태뿐 아니라 요청 캐시도 세션에 저장하지 않는다.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                // SPA 설정은 Cookie의 원본 토큰 헤더와 BREACH 보호 처리를 함께 지원한다.
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfRepository)
                        // 회원가입은 비인증 공개 요청이므로 CSRF 토큰을 요구하지 않는다.
                        .ignoringRequestMatchers(PathPatternRequestMatcher.pathPattern(
                                HttpMethod.POST, "/api/users/signup")))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // /logout 기본 엔드포인트 대신 이후 auth 도메인의 명시적 로그아웃을 사용한다.
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // 내부 오류 디스패치가 기존 404/500을 401로 바꾸지 않도록 한다.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/csrf").permitAll()
                        // 날씨 조회는 사용자별 데이터가 아닌 공용 예보 데이터만 반환한다.
                        .requestMatchers(HttpMethod.GET, "/api/weather").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/places/popular").permitAll()
                        // Swagger UI와 OpenAPI 문서의 공개 조회만 허용한다.
                        .requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users/signup").permitAll()
                        // 로그인은 공개지만 CSRF 제외 대상이 아니므로 위 CSRF 규칙은 유지한다.
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        // Refresh Cookie로 인증하므로 Access Token은 요구하지 않되 CSRF 보호는 유지한다.
                        .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
                        // 로그아웃은 멱등 처리하며 Cookie 인증 요청이므로 CSRF 보호는 유지한다.
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // users.password_hash VARCHAR(60)에 맞게 {bcrypt} 접두사 없는 BCrypt를 사용한다.
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        var source = new UrlBasedCorsConfigurationSource();
        // 운영 기본값은 빈 목록: same-origin만 사용하며 외부 Origin을 허용하지 않는다.
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowCredentials(true);
        cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-XSRF-TOKEN"));
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
