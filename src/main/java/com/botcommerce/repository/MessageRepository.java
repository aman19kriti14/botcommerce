package com.botcommerce.repository;

import com.botcommerce.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

	@Query(value = "SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
	List<Message> findRecentByConversationId(UUID conversationId, int limit);
}