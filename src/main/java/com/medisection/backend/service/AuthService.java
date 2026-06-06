package com.medisection.backend.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medisection.backend.domain.user.User;
import com.medisection.backend.dto.AuthDto;
import com.medisection.backend.exception.BusinessException;
import com.medisection.backend.exception.CommonErrorCode;
import com.medisection.backend.repository.UserRepository;
import com.medisection.backend.security.JwtTokenProvider;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;

	/**
	 * 사용자 로그인 요청을 검증하고 프론트에서 사용할 JWT access token을 발급합니다.
	 * 실패 사유를 세분화하지 않고 LOGIN_FAILED로 통일해 계정 존재 여부가 노출되지 않도록 했습니다.
	 */
	public AuthDto.LoginResponse handleLogin(AuthDto.LoginRequest request) {
		User user = userRepository.findByUsername(request.username())
			.orElseThrow(() -> new BusinessException(CommonErrorCode.LOGIN_FAILED));

		if (!passwordEncoder.matches(request.password(), user.getPassword())) {
			throw new BusinessException(CommonErrorCode.LOGIN_FAILED);
		}

		String accessToken = jwtTokenProvider.createToken(user.getUsername());

		AuthDto.LoginUser loginUser = AuthDto.LoginUser.builder()
			.username(user.getUsername())
			.name(user.getName())
			.isFinishOnboard(user.isOnBoardingCompleted())
			.preferCategory(user.getPreferCategory())
			.themeColor(user.getThemeColor())
			.build();

		return AuthDto.LoginResponse.builder()
			.loginUser(loginUser)
			.accessToken(accessToken)
			.build();
	}
}
