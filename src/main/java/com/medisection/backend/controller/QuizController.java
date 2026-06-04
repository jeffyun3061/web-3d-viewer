package com.medisection.backend.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medisection.backend.domain.user.User;
import com.medisection.backend.dto.QuizDto;
import com.medisection.backend.dto.QuizResponse;
import com.medisection.backend.exception.BusinessException;
import com.medisection.backend.exception.CommonErrorCode;
import com.medisection.backend.repository.UserRepository;
import com.medisection.backend.security.CustomUserDetails;
import com.medisection.backend.service.QuizGradingService;
import com.medisection.backend.service.QuizService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/scenes/{sceneId}/quizzes")
@RequiredArgsConstructor
public class QuizController {

	private final QuizGradingService gradingService;
	private final QuizService quizService;
	private final UserRepository userRepository;

	@GetMapping
	public ResponseEntity<QuizResponse> getQuizzes(
		@AuthenticationPrincipal
		CustomUserDetails userDetails,
		@PathVariable
		Long sceneId) {
		User user = userRepository.findByUsername(userDetails.getUsername())
			.orElseThrow(() -> new BusinessException(CommonErrorCode.USER_NOT_FOUND));

		QuizResponse response = quizService.getSceneQuizzes(sceneId, user);
		return ResponseEntity.ok(response);
	}

	@PostMapping("/{quizId}/grade")
	public ResponseEntity<QuizDto.GradeResponse> grade(
		@AuthenticationPrincipal
		CustomUserDetails userDetails,
		@PathVariable
		Long sceneId,
		@PathVariable
		Long quizId,
		@Valid @RequestBody
		QuizDto.GradeRequest request) {
		User user = userRepository.findByUsername(userDetails.getUsername())
			.orElseThrow(() -> new BusinessException(CommonErrorCode.USER_NOT_FOUND));

		QuizDto.GradeResponse response = gradingService.grade(quizId, request.answer(), user);
		return ResponseEntity.ok(response);
	}

	@PatchMapping("/progress")
	public ResponseEntity<Void> syncProgress(
		@AuthenticationPrincipal
		CustomUserDetails userDetails,
		@PathVariable
		Long sceneId,
		@Valid @RequestBody
		QuizDto.SyncProgressRequest request) {
		User user = userRepository.findByUsername(userDetails.getUsername())
			.orElseThrow(() -> new BusinessException(CommonErrorCode.USER_NOT_FOUND));

		quizService.syncProgress(sceneId, request, user);
		return ResponseEntity.ok().build();
	}
}
