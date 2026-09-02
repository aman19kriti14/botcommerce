package com.botcommerce.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.botcommerce.dto.KnowledgeDTO.KnowledgeContext;
import com.botcommerce.dto.KnowledgeDTO.SourceResponse;
import com.botcommerce.dto.KnowledgeDTO.StatsResponse;
import com.botcommerce.model.KnowledgeChunk;
import com.botcommerce.model.KnowledgeSource;
import com.botcommerce.model.KnowledgeSource.SourceStatus;
import com.botcommerce.model.KnowledgeSource.SourceType;
import com.botcommerce.repository.KnowledgeChunkRepository;
import com.botcommerce.repository.KnowledgeSourceRepository;

@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final KnowledgeSourceRepository sourceRepo;
    private final KnowledgeChunkRepository chunkRepo;
    private final TextExtractorService extractor;

    public KnowledgeService(KnowledgeSourceRepository sourceRepo,
                            KnowledgeChunkRepository chunkRepo,
                            TextExtractorService extractor) {
        this.sourceRepo = sourceRepo;
        this.chunkRepo = chunkRepo;
        this.extractor = extractor;
    }

    // ──────────────── Queries ────────────────

    public List<SourceResponse> getSources(Long merchantId) {
        return sourceRepo.findByMerchantIdOrderByCreatedAtDesc(merchantId)
            .stream()
            .map(SourceResponse::from)
            .toList();
    }

    public StatsResponse getStats(Long merchantId) {
        List<KnowledgeSource> all = sourceRepo.findByMerchantIdOrderByCreatedAtDesc(merchantId);
        int ready = sourceRepo.countReadyByMerchantId(merchantId);
        int chunks = sourceRepo.totalChunksByMerchantId(merchantId);
        int chars = sourceRepo.totalCharsByMerchantId(merchantId);

        var lastUpdated = all.stream()
            .map(KnowledgeSource::getUpdatedAt)
            .max(java.time.LocalDateTime::compareTo)
            .orElse(null);

        return new StatsResponse(all.size(), ready, chunks, chars, lastUpdated);
    }

    // ──────────────── File Upload ────────────────

    @Transactional
    public SourceResponse uploadFile(Long merchantId, MultipartFile file) {
        KnowledgeSource source = new KnowledgeSource();
        source.setMerchantId(merchantId);
        source.setType(SourceType.FILE);
        source.setName(file.getOriginalFilename());
        source.setOriginalFilename(file.getOriginalFilename());
        source.setFileSize(file.getSize());
        source.setContentType(file.getContentType());
        source.setStatus(SourceStatus.PROCESSING);
        source = sourceRepo.save(source);

        // Process synchronously for now (can make async later)
        processFile(source.getId(), file);

        return SourceResponse.from(sourceRepo.findById(source.getId()).orElseThrow());
    }

    private void processFile(Long sourceId, MultipartFile file) {
        KnowledgeSource source = sourceRepo.findById(sourceId).orElseThrow();
        try {
            // 1. Extract text
            String text = extractor.extractFromFile(file);
            source.setExtractedText(text);
            source.setCharCount(text.length());

            // 2. Chunk the text
            List<String> chunks = extractor.chunkText(text);
            source.setChunkCount(chunks.size());

            // 3. Save chunks
            for (int i = 0; i < chunks.size(); i++) {
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setKnowledgeSource(source);
                chunk.setMerchantId(source.getMerchantId());
                chunk.setChunkIndex(i);
                chunk.setContent(chunks.get(i));
                chunk.setCharCount(chunks.get(i).length());
                source.getChunks().add(chunk);
            }

            source.setStatus(SourceStatus.READY);
            log.info("File processed: {} → {} chunks", source.getName(), chunks.size());

        } catch (Exception e) {
            log.error("Failed to process file {}: {}", source.getName(), e.getMessage());
            source.setStatus(SourceStatus.FAILED);
            source.setErrorMessage(e.getMessage());
        }
        sourceRepo.save(source);
    }

    // ──────────────── Website Crawl ────────────────

    @Transactional
    public SourceResponse crawlWebsite(Long merchantId, String url, int maxPages) {
        // Normalize URL
        if (!url.startsWith("http")) url = "https://" + url;

        KnowledgeSource source = new KnowledgeSource();
        source.setMerchantId(merchantId);
        source.setType(SourceType.WEBSITE);
        source.setName(extractDomain(url));
        source.setSourceUrl(url);
        source.setStatus(SourceStatus.PROCESSING);
        source = sourceRepo.save(source);

        // Process synchronously (can make async with SSE later for progress)
        processWebsite(source.getId(), url, maxPages);

        return SourceResponse.from(sourceRepo.findById(source.getId()).orElseThrow());
    }

    private void processWebsite(Long sourceId, String url, int maxPages) {
        KnowledgeSource source = sourceRepo.findById(sourceId).orElseThrow();
        try {
            // 1. Crawl website
            TextExtractorService.CrawlResult result = extractor.crawlWebsite(url, maxPages);

            if (result.error != null) {
                source.setStatus(SourceStatus.FAILED);
                source.setErrorMessage(result.error);
                sourceRepo.save(source);
                return;
            }

            if (result.pages.isEmpty()) {
                source.setStatus(SourceStatus.FAILED);
                source.setErrorMessage("No readable content found on the website");
                sourceRepo.save(source);
                return;
            }

            // 2. Combine all page texts
            StringBuilder allText = new StringBuilder();
            for (var page : result.pages) {
                allText.append("## ").append(page.title).append("\n");
                allText.append(page.text).append("\n\n");
            }

            String combinedText = allText.toString().trim();
            source.setExtractedText(combinedText);
            source.setCharCount(combinedText.length());
            source.setPagesCrawled(result.pages.size());

            // 3. Chunk the combined text (use page-aware chunking)
            int chunkIndex = 0;
            for (var page : result.pages) {
                List<String> pageChunks = extractor.chunkText(page.text);
                for (String chunkText : pageChunks) {
                    KnowledgeChunk chunk = new KnowledgeChunk();
                    chunk.setKnowledgeSource(source);
                    chunk.setMerchantId(source.getMerchantId());
                    chunk.setChunkIndex(chunkIndex++);
                    chunk.setContent(chunkText);
                    chunk.setCharCount(chunkText.length());
                    chunk.setSourcePage(page.url);
                    source.getChunks().add(chunk);
                }
            }
            source.setChunkCount(chunkIndex);
            source.setStatus(SourceStatus.READY);
            log.info("Website crawled: {} → {} pages, {} chunks", source.getName(), result.pages.size(), chunkIndex);

        } catch (Exception e) {
            log.error("Failed to crawl website {}: {}", url, e.getMessage());
            source.setStatus(SourceStatus.FAILED);
            source.setErrorMessage(e.getMessage());
        }
        sourceRepo.save(source);
    }

    // ──────────────── Manual Text ────────────────

    @Transactional
    public SourceResponse addText(Long merchantId, String title, String content) {
        KnowledgeSource source = new KnowledgeSource();
        source.setMerchantId(merchantId);
        source.setType(SourceType.TEXT);
        source.setName(title);
        source.setRawText(content);
        source.setExtractedText(content);
        source.setCharCount(content.length());
        source.setStatus(SourceStatus.PROCESSING);
        source = sourceRepo.save(source);

        // Chunk the text
        List<String> chunks = extractor.chunkText(content);
        source.setChunkCount(chunks.size());

        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setKnowledgeSource(source);
            chunk.setMerchantId(source.getMerchantId());
            chunk.setChunkIndex(i);
            chunk.setContent(chunks.get(i));
            chunk.setCharCount(chunks.get(i).length());
            source.getChunks().add(chunk);
        }

        source.setStatus(SourceStatus.READY);
        sourceRepo.save(source);

        log.info("Text added: {} → {} chunks", title, chunks.size());
        return SourceResponse.from(source);
    }

    // ──────────────── Delete Source ────────────────

    @Transactional
    public void deleteSource(Long merchantId, Long sourceId) {
        KnowledgeSource source = sourceRepo.findById(sourceId)
            .orElseThrow(() -> new RuntimeException("Source not found"));

        if (!source.getMerchantId().equals(merchantId)) {
            throw new RuntimeException("Unauthorized");
        }

        sourceRepo.delete(source); // cascades to chunks
        log.info("Deleted knowledge source: {} ({})", source.getName(), sourceId);
    }

    // ──────────────── Build AI Knowledge Context ────────────────

    /**
     * Get all knowledge chunks for a merchant, formatted for the AI system prompt.
     * This is called by your existing AI/chatbot service when building the prompt.
     */
    public String buildKnowledgePrompt(Long merchantId) {
        List<KnowledgeChunk> chunks = chunkRepo
            .findByMerchantIdOrderByKnowledgeSourceIdAscChunkIndexAsc(merchantId);

        if (chunks.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n--- BUSINESS KNOWLEDGE BASE ---\n");
        sb.append("The following is information about this business. Use it to answer customer questions accurately.\n\n");

        Long currentSourceId = null;
        for (KnowledgeChunk chunk : chunks) {
            if (!chunk.getKnowledgeSource().getId().equals(currentSourceId)) {
                currentSourceId = chunk.getKnowledgeSource().getId();
                sb.append("\n[Source: ").append(chunk.getKnowledgeSource().getName()).append("]\n");
            }
            sb.append(chunk.getContent()).append("\n");
        }

        sb.append("\n--- END KNOWLEDGE BASE ---\n");
        return sb.toString();
    }

    /**
     * Alternative: Get structured knowledge context (for API response).
     */
    public KnowledgeContext getKnowledgeContext(Long merchantId) {
        List<KnowledgeChunk> chunks = chunkRepo
            .findByMerchantIdOrderByKnowledgeSourceIdAscChunkIndexAsc(merchantId);

        var entries = chunks.stream()
            .map(c -> new KnowledgeContext.ChunkEntry(
                c.getKnowledgeSource().getName(),
                c.getKnowledgeSource().getType().name(),
                c.getContent()
            ))
            .toList();

        int totalChars = chunks.stream().mapToInt(KnowledgeChunk::getCharCount).sum();
        return new KnowledgeContext(chunks.size(), totalChars, entries);
    }

    // ──────────────── Helpers ────────────────

    private String extractDomain(String url) {
        try {
            return new java.net.URI(url).getHost();
        } catch (Exception e) {
            return url;
        }
    }
}
