package com.botcommerce.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.botcommerce.model.KnowledgeChunk;

@Repository
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

	List<KnowledgeChunk> findByMerchantIdOrderByKnowledgeSourceIdAscChunkIndexAsc(Long merchantId);

	List<KnowledgeChunk> findByKnowledgeSourceIdOrderByChunkIndexAsc(Long knowledgeSourceId);

	void deleteByKnowledgeSourceId(Long knowledgeSourceId);
}
