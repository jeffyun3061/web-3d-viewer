package com.medisection.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.medisection.backend.domain.conversation.Reference;

public interface ReferenceRepository extends JpaRepository<Reference, Long> {

	@Query("SELECT r FROM Reference r JOIN FETCH r.component WHERE r.message.id IN :messageIds")
	List<Reference> findByMessageIdInWithComponent(@Param("messageIds")
	List<Long> messageIds);
}
