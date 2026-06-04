package com.medisection.backend.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

// 인증을 비활성화했으므로 JWT 관련 필터와 토큰 제공자는 사용하지 않습니다.

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // 모든 요청을 인증 없이 허용합니다. JWT 필터를 제거하여 누구나 접근 가능한 상태로 설정합니다.
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		// health-check 전용 CORS 설정 (모든 오리진 허용)
		CorsConfiguration healthCheckCors = new CorsConfiguration();
		healthCheckCors.setAllowedOriginPatterns(List.of("*"));
		healthCheckCors.setAllowedMethods(List.of("GET", "OPTIONS"));
		healthCheckCors.setAllowedHeaders(List.of("*"));

		// 일반 API용 CORS 설정
		CorsConfiguration defaultCors = new CorsConfiguration();
		defaultCors.setAllowedOriginPatterns(List.of("*"));
		defaultCors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
		defaultCors.setAllowedHeaders(List.of("*"));
		defaultCors.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/health-check", healthCheckCors);
		source.registerCorsConfiguration("/**", defaultCors);
		return source;
	}
}
