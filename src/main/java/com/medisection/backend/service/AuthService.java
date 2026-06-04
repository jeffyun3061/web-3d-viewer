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
