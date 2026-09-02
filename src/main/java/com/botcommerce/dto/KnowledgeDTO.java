package com.botcommerce.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.botcommerce.model.KnowledgeSource;

public class KnowledgeDTO {

	// ── Response: Single source ──
	public record SourceResponse(Long id, String type, String name, String status, Long fileSize, String sourceUrl,
			Integer pagesCrawled, Integer chunkCount, Integer charCount, String errorMessage, String preview, // first
																												// ~200
																												// chars
																												// of
																												// extracted
																												// text
			LocalDateTime createdAt, LocalDateTime updatedAt) {
		public static SourceResponse from(KnowledgeSource s) {
			String preview = null;
			if (s.getExtractedText() != null && !s.getExtractedText().isEmpty()) {
				preview = s.getExtractedText().length() > 200 ? s.getExtractedText().substring(0, 200) + "..."
						: s.getExtractedText();
			}
			return new SourceResponse(s.getId(), s.getType().name(), s.getName(), s.getStatus().name(), s.getFileSize(),
					s.getSourceUrl(), s.getPagesCrawled(), s.getChunkCount(), s.getCharCount(), s.getErrorMessage(),
					preview, s.getCreatedAt(), s.getUpdatedAt());
		}
	}

	// ── Response: Stats overview ──
	public record StatsResponse(int totalSources, int readySources, int totalChunks, int totalChars,
			LocalDateTime lastUpdated) {
	}

	// ── Request: Add website ──
	public record WebsiteRequest(String url, int maxPages // limit crawl depth, default 20
	) {
	}

	// ── Request: Add manual text ──
	public record TextRequest(String title, String content) {
	}

	// ── Response: Crawl progress (SSE events) ──
	public record CrawlProgress(Long sourceId, String pageUrl, String pageTitle, int chunksExtracted,
			int totalPagesCrawled, boolean done, String error) {
	}

	// ── Response: All knowledge for AI prompt ──
	public record KnowledgeContext(int totalChunks, int totalChars, List<ChunkEntry> chunks) {
		public record ChunkEntry(String sourceName, String sourceType, String content) {
		}
	}
}
