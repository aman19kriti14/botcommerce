package com.botcommerce.repository;

import com.botcommerce.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByCustomerIdAndMerchantIdAndStatus(UUID customerId, UUID merchantId, String status);
    
    Optional<Conversation> findTopByCustomerIdAndStatusOrderByLastMessageAtDesc(UUID customerId, String status);
}