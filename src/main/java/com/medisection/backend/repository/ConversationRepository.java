package com.medisection.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.medisection.backend.domain.conversation.Conversation;
import com.medisection.backend.domain.user.User;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

	@Query("SELECT c FROM Conversation c WHERE c.user = :user AND c.scene.id = :sceneId")
	Optional<Conversation> findByUserAndSceneId(@Param("user")
	User user, @Param("sceneId")
	Long sceneId);

	List<Conversation> findByUser(User user);
}
