package com.swyp.ploutos.common.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    // 프론트엔드는 Cloudflare Pages를 쓰며, 브랜치마다 Preview 주소가 새로 생긴다.
    // 주소를 미리 다 알 수 없으므로 정확한 일치가 아니라 패턴으로 허용한다.
    // 운영 주소는 서브도메인이 없어 패턴에 걸리지 않으므로 따로 적는다.
    private static final List<String> ALLOWED_ORIGIN_PATTERNS = List.of(
            "https://ploutos-2vw.pages.dev",
            "https://*.ploutos-2vw.pages.dev"
    );

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // CORS 처리를 인증 검사보다 앞에 둔다. 이게 없으면 사전 요청(OPTIONS)이
                // 인증 필터에서 401로 잘려 아래 CorsConfigurationSource까지 닿지 않는다.
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                // 인증 도입 전 임시 설정. 로그인 기능이 들어오면 경로별 인가 규칙으로 교체해야 한다.
                .authorizeHttpRequests(request -> request.anyRequest().permitAll())
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        // 사전 요청 결과를 브라우저가 1시간 캐싱해 본 요청마다 왕복하지 않게 한다.
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
