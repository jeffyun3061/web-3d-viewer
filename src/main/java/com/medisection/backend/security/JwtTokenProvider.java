package com.medisection.backend.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {

	private final SecretKey key;
	private final long validityInMilliseconds;

	public JwtTokenProvider(
		@Value("${jwt.secret-key}")
		String secretKey,
		@Value("${jwt.expiration-time:3600000}")
		long validityInMilliseconds) {
		this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
		this.validityInMilliseconds = validityInMilliseconds;
	}

	/**
	 * username을 subject로 넣어 access token을 생성합니다.
	 * 만료 시간은 application.yml 설정값을 사용해 개발/운영 환경에서 다르게 조정할 수 있습니다.
	 */
	public String createToken(String userId) {
		Date now = new Date();
		Date validity = new Date(now.getTime() + validityInMilliseconds);

		return Jwts.builder()
			.subject(userId)
			.issuedAt(now)
			.expiration(validity)
			.signWith(key)
			.compact();
	}

	/**
	 * 검증된 JWT에서 subject를 꺼내 현재 사용자 식별자로 사용합니다.
	 */
	public String getUserIdFromToken(String token) {
		Claims claims = Jwts.parser()
			.verifyWith(key)
			.build()
			.parseSignedClaims(token)
			.getPayload();
		return claims.getSubject();
	}

	/**
	 * 서명과 만료 시간을 함께 검증합니다.
	 * 예외를 boolean으로 변환해 Spring Security 필터에서 단순하게 분기할 수 있게 했습니다.
	 */
	public boolean validateToken(String token) {
		try {
			Jwts.parser()
				.verifyWith(key)
				.build()
				.parseSignedClaims(token);
			return true;
		} catch (JwtException | IllegalArgumentException e) {
			return false;
		}
	}
}
