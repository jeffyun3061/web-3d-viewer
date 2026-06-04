package com.medisection.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.medisection.backend.domain.quiz.QuizUserProgress;

public interface QuizUserProgressRepository extends JpaRepository<QuizUserProgress, Long> {
	Optional<QuizUserProgress> findByUserIdAndSceneId(Long userId, Long sceneId);
}
