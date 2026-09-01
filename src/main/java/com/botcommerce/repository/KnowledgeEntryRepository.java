package com.botcommerce.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.botcommerce.model.KnowledgeEntry;

@Repository
public interface KnowledgeEntryRepository extends JpaRepository<KnowledgeEntry, UUID> {

    List<KnowledgeEntry> findByMerchantId(UUID merchantId);
}