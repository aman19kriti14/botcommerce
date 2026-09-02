package com.botcommerce.repository;

import com.botcommerce.model.KnowledgeSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgeSourceRepository extends JpaRepository<KnowledgeSource, UUID> {

	List<KnowledgeSource> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

	Optional<KnowledgeSource> findByIdAndMerchantId(UUID id, UUID merchantId);

	int countByMerchantId(UUID merchantId);
}