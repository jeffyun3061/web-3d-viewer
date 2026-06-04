package com.medisection.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medisection.backend.domain.quiz.Quiz;
import com.medisection.backend.domain.quiz.QuizType;
import com.medisection.backend.domain.quiz.QuizUserProgress;
import com.medisection.backend.domain.user.User;
import com.medisection.backend.dto.QuizDto;
import com.medisection.backend.exception.BusinessException;
import com.medisection.backend.exception.CommonErrorCode;
import com.medisection.backend.repository.QuizRepository;
import com.medisection.backend.repository.QuizUserProgressRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuizGradingService {

	private static final double SIMILARITY_THRESHOLD = 0.8;

	private final QuizRepository quizRepository;
	private final QuizUserProgressRepository progressRepository;
	private final EmbeddingService embeddingService;

	@Transactional
	public QuizDto.GradeResponse grade(Long quizId, String userAnswer, User user) {
		Quiz quiz = quizRepository.findById(quizId)
			.orElseThrow(() -> new BusinessException(CommonErrorCode.QUIZ_NOT_FOUND));

		boolean correct;
		double score;
		String correctAnswer;

		if (quiz.getType() == QuizType.SELECT) {
			correctAnswer = quiz.getAnswer().split(",")[0];
			correct = correctAnswer.equalsIgnoreCase(userAnswer.trim());
			score = correct ? 1.0 : 0.0;
		} else {
			correctAnswer = quiz.getAnswer();
			score = embeddingService.calculateSimilarity(userAnswer, correctAnswer);
			correct = score >= SIMILARITY_THRESHOLD;
		}

		updateProgress(user, quiz, correct);

		return new QuizDto.GradeResponse(correct, score, correctAnswer);
	}

	private void updateProgress(User user, Quiz quiz, boolean correct) {
		QuizUserProgress progress = progressRepository
			.findByUserIdAndSceneId(user.getId(), quiz.getScene().getId())
			.orElseGet(() -> QuizUserProgress.builder()
				.user(user)
				.scene(quiz.getScene())
				.lastQuizId(quiz.getId())
				.totalQuestions(0)
				.success(0)
				.failure(0)
				.isComplete(false)
				.solveTime(0)
				.build());

		QuizUserProgress updated = QuizUserProgress.builder()
			.id(progress.getId())
			.user(progress.getUser())
			.scene(progress.getScene())
			.lastQuizId(quiz.getId())
			.totalQuestions(progress.getTotalQuestions())
			.success(correct ? progress.getSuccess() + 1 : progress.getSuccess())
			.failure(correct ? progress.getFailure() : progress.getFailure() + 1)
			.isComplete(progress.isComplete())
			.solveTime(progress.getSolveTime())
			.build();

		progressRepository.save(updated);
	}
}
