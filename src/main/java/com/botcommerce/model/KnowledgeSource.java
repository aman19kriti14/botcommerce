package com.botcommerce.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "knowledge_sources")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeSource {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "merchant_id", nullable = false)
	private Merchant merchant;

	@Column(nullable = false, length = 20)
	private String type;

	@Column(nullable = false)
	private String name;

	@Column(length = 500)
	private String url;

	@Column(name = "file_name")
	private String fileName;

	@Column(length = 20)
	@Builder.Default
	private String status = "processing";

	@Column(columnDefinition = "text")
	private String content;

	@Column(name = "chunks_count")
	@Builder.Default
	private Integer chunksCount = 0;

	@Column(name = "char_count")
	@Builder.Default
	private Integer charCount = 0;

	@Column(name = "error_message")
	private String errorMessage;

	@Column(name = "created_at")
	@Builder.Default
	private OffsetDateTime createdAt = OffsetDateTime.now();
}