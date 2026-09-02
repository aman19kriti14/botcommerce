package com.botcommerce.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.botcommerce.model.KnowledgeSource;

@Repository
public interface KnowledgeSourceRepository extends JpaRepository<KnowledgeSource, Long> {

	List<KnowledgeSource> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);

	List<KnowledgeSource> findByMerchantIdAndStatus(Long merchantId, KnowledgeSource.SourceStatus status);

	@Query("SELECT COUNT(s) FROM KnowledgeSource s WHERE s.merchantId = :merchantId AND s.status = 'READY'")
	int countReadyByMerchantId(Long merchantId);

	@Query("SELECT COALESCE(SUM(s.chunkCount), 0) FROM KnowledgeSource s WHERE s.merchantId = :merchantId AND s.status = 'READY'")
	int totalChunksByMerchantId(Long merchantId);

	@Query("SELECT COALESCE(SUM(s.charCount), 0) FROM KnowledgeSource s WHERE s.merchantId = :merchantId AND s.status = 'READY'")
	int totalCharsByMerchantId(Long merchantId);
}
