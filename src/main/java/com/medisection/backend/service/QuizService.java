package com.medisection.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medisection.backend.domain.quiz.Quiz;
import com.medisection.backend.domain.quiz.QuizType;
import com.medisection.backend.domain.quiz.QuizUserProgress;
import com.medisection.backend.domain.scene.SceneInformation;
import com.medisection.backend.domain.user.User;
import com.medisection.backend.dto.QuizDto;
import com.medisection.backend.dto.QuizResponse;
import com.medisection.backend.exception.BusinessException;
import com.medisection.backend.exception.CommonErrorCode;
import com.medisection.backend.repository.QuizRepository;
import com.medisection.backend.repository.QuizUserProgressRepository;
import com.medisection.backend.repository.SceneInformationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuizService {

	private final QuizRepository quizRepository;
	private final QuizUserProgressRepository progressRepository;
	private final SceneInformationRepository sceneRepository;

	@Transactional
	public QuizResponse getSceneQuizzes(Long sceneId, User user) {
		SceneInformation scene = sceneRepository.findById(sceneId)
			.orElseThrow(() -> new BusinessException(CommonErrorCode.SCENE_NOT_FOUND));

		List<Quiz> quizzes = quizRepository.findAllBySceneIdOrderById(sceneId);

		QuizUserProgress progress = progressRepository.findByUserIdAndSceneId(user.getId(), sceneId)
			.orElseGet(() -> {
				QuizUserProgress newProgress = QuizUserProgress.builder()
					.user(user)
					.scene(scene)
					.lastQuizId(null)
					.totalQuestions(quizzes.size())
					.success(0)
					.failure(0)
					.isComplete(false)
					.solveTime(0)
					.build();
				return progressRepository.save(newProgress);
			});

		return mapToResponse(sceneId, progress, quizzes);
	}

	@Transactional
	public void syncProgress(Long sceneId, QuizDto.SyncProgressRequest request, User user) {
		QuizUserProgress progress = progressRepository.findByUserIdAndSceneId(user.getId(), sceneId)
			.orElseThrow(() -> new BusinessException(CommonErrorCode.QUIZ_PROGRESS_NOT_FOUND));

		QuizUserProgress updated = QuizUserProgress.builder()
			.id(progress.getId())
			.user(progress.getUser())
			.scene(progress.getScene())
			.lastQuizId(request.lastQuizId())
			.totalQuestions(request.totalQuestions())
			.success(request.success())
			.failure(request.failure())
			.solveTime(request.solveTime())
			.isComplete(request.isComplete())
			.build();

		progressRepository.save(updated);
	}

	private QuizResponse mapToResponse(Long sceneId, QuizUserProgress progress, List<Quiz> quizzes) {
		QuizResponse.UserProgressDto progressDto = new QuizResponse.UserProgressDto(
			progress.getId(),
			progress.getLastQuizId(),
			progress.getTotalQuestions(),
			progress.getSuccess(),
			progress.getFailure(),
			progress.isComplete());

		List<QuizResponse.QuizItemDto> quizItemDtos = quizzes.stream()
			.map(quiz -> new QuizResponse.QuizItemDto(
				quiz.getId(),
				quiz.getTargetPurpose(),
				quiz.getType(),
				quiz.getQuestion(),
				quiz.getType().equals(QuizType.SELECT) ? quiz.getAnswer() : null))
			.toList();

		return new QuizResponse(sceneId, progressDto, quizItemDtos);
	}
}
