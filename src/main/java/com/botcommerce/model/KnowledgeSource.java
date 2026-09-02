package com.botcommerce.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "knowledge_sources")
public class KnowledgeSource {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "merchant_id", nullable = false)
	private Long merchantId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SourceType type; // FILE, WEBSITE, TEXT

	@Column(nullable = false)
	private String name; // filename, URL, or title

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SourceStatus status; // PENDING, PROCESSING, READY, FAILED

	// For FILE type
	@Column(name = "original_filename")
	private String originalFilename;

	@Column(name = "file_path")
	private String filePath; // path in storage (local or S3/Firebase)

	@Column(name = "file_size")
	private Long fileSize;

	@Column(name = "content_type")
	private String contentType; // MIME type

	// For WEBSITE type
	@Column(name = "source_url", length = 2048)
	private String sourceUrl;

	@Column(name = "pages_crawled")
	private Integer pagesCrawled;

	// For TEXT type
	@Column(name = "raw_text", columnDefinition = "TEXT")
	private String rawText;

	// Extraction results
	@Column(name = "extracted_text", columnDefinition = "TEXT")
	private String extractedText;

	@Column(name = "chunk_count")
	private Integer chunkCount = 0;

	@Column(name = "char_count")
	private Integer charCount = 0;

	@Column(name = "error_message", length = 1000)
	private String errorMessage;

	@OneToMany(mappedBy = "knowledgeSource", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("chunkIndex ASC")
	private List<KnowledgeChunk> chunks = new ArrayList<>();

	@CreationTimestamp
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	public enum SourceType {
		FILE, WEBSITE, TEXT
	}

	public enum SourceStatus {
		PENDING, PROCESSING, READY, FAILED
	}

	// ── Getters & Setters ──

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getMerchantId() {
		return merchantId;
	}

	public void setMerchantId(Long merchantId) {
		this.merchantId = merchantId;
	}

	public SourceType getType() {
		return type;
	}

	public void setType(SourceType type) {
		this.type = type;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public SourceStatus getStatus() {
		return status;
	}

	public void setStatus(SourceStatus status) {
		this.status = status;
	}

	public String getOriginalFilename() {
		return originalFilename;
	}

	public void setOriginalFilename(String originalFilename) {
		this.originalFilename = originalFilename;
	}

	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	public Long getFileSize() {
		return fileSize;
	}

	public void setFileSize(Long fileSize) {
		this.fileSize = fileSize;
	}

	public String getContentType() {
		return contentType;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	public String getSourceUrl() {
		return sourceUrl;
	}

	public void setSourceUrl(String sourceUrl) {
		this.sourceUrl = sourceUrl;
	}

	public Integer getPagesCrawled() {
		return pagesCrawled;
	}

	public void setPagesCrawled(Integer pagesCrawled) {
		this.pagesCrawled = pagesCrawled;
	}

	public String getRawText() {
		return rawText;
	}

	public void setRawText(String rawText) {
		this.rawText = rawText;
	}

	public String getExtractedText() {
		return extractedText;
	}

	public void setExtractedText(String extractedText) {
		this.extractedText = extractedText;
	}

	public Integer getChunkCount() {
		return chunkCount;
	}

	public void setChunkCount(Integer chunkCount) {
		this.chunkCount = chunkCount;
	}

	public Integer getCharCount() {
		return charCount;
	}

	public void setCharCount(Integer charCount) {
		this.charCount = charCount;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public List<KnowledgeChunk> getChunks() {
		return chunks;
	}

	public void setChunks(List<KnowledgeChunk> chunks) {
		this.chunks = chunks;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
