package com.botcommerce.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "knowledge_chunks")
public class KnowledgeChunk {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "knowledge_source_id", nullable = false)
	private KnowledgeSource knowledgeSource;

	@Column(name = "merchant_id", nullable = false)
	private Long merchantId;

	@Column(name = "chunk_index", nullable = false)
	private Integer chunkIndex;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(name = "char_count")
	private Integer charCount;

	@Column(name = "source_page")
	private String sourcePage; // page number for PDFs, URL for websites

	@CreationTimestamp
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	// ── Getters & Setters ──

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public KnowledgeSource getKnowledgeSource() {
		return knowledgeSource;
	}

	public void setKnowledgeSource(KnowledgeSource knowledgeSource) {
		this.knowledgeSource = knowledgeSource;
	}

	public Long getMerchantId() {
		return merchantId;
	}

	public void setMerchantId(Long merchantId) {
		this.merchantId = merchantId;
	}

	public Integer getChunkIndex() {
		return chunkIndex;
	}

	public void setChunkIndex(Integer chunkIndex) {
		this.chunkIndex = chunkIndex;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public Integer getCharCount() {
		return charCount;
	}

	public void setCharCount(Integer charCount) {
		this.charCount = charCount;
	}

	public String getSourcePage() {
		return sourcePage;
	}

	public void setSourcePage(String sourcePage) {
		this.sourcePage = sourcePage;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
