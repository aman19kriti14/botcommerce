package com.botcommerce.controller;

import com.botcommerce.dto.KnowledgeDTO.*;
import com.botcommerce.service.KnowledgeService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
@CrossOrigin(origins = "*")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    // ── Get all sources for a merchant ──
    @GetMapping("/sources")
    public ResponseEntity<List<SourceResponse>> getSources(
            @RequestHeader("X-Merchant-Id") Long merchantId) {
        return ResponseEntity.ok(knowledgeService.getSources(merchantId));
    }

    // ── Get knowledge stats ──
    @GetMapping("/stats")
    public ResponseEntity<StatsResponse> getStats(
            @RequestHeader("X-Merchant-Id") Long merchantId) {
        return ResponseEntity.ok(knowledgeService.getStats(merchantId));
    }

    // ── Upload file(s) ──
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<SourceResponse>> uploadFiles(
            @RequestHeader("X-Merchant-Id") Long merchantId,
            @RequestParam("files") List<MultipartFile> files) {

        List<SourceResponse> results = files.stream()
            .map(file -> knowledgeService.uploadFile(merchantId, file))
            .toList();

        return ResponseEntity.ok(results);
    }

    // ── Crawl a website ──
    @PostMapping("/website")
    public ResponseEntity<SourceResponse> crawlWebsite(
            @RequestHeader("X-Merchant-Id") Long merchantId,
            @RequestBody WebsiteRequest request) {

        int maxPages = request.maxPages() > 0 ? request.maxPages() : 20;
        SourceResponse result = knowledgeService.crawlWebsite(merchantId, request.url(), maxPages);
        return ResponseEntity.ok(result);
    }

    // ── Add manual text ──
    @PostMapping("/text")
    public ResponseEntity<SourceResponse> addText(
            @RequestHeader("X-Merchant-Id") Long merchantId,
            @RequestBody TextRequest request) {

        if (request.title() == null || request.title().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (request.content() == null || request.content().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        SourceResponse result = knowledgeService.addText(merchantId, request.title(), request.content());
        return ResponseEntity.ok(result);
    }

    // ── Delete a source ──
    @DeleteMapping("/sources/{sourceId}")
    public ResponseEntity<Map<String, String>> deleteSource(
            @RequestHeader("X-Merchant-Id") Long merchantId,
            @PathVariable Long sourceId) {

        knowledgeService.deleteSource(merchantId, sourceId);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    // ── Get built knowledge context (for debugging / preview) ──
    @GetMapping("/context")
    public ResponseEntity<KnowledgeContext> getContext(
            @RequestHeader("X-Merchant-Id") Long merchantId) {
        return ResponseEntity.ok(knowledgeService.getKnowledgeContext(merchantId));
    }

    // ── Get knowledge as prompt text (for AI integration) ──
    @GetMapping("/prompt")
    public ResponseEntity<Map<String, String>> getPrompt(
            @RequestHeader("X-Merchant-Id") Long merchantId) {
        String prompt = knowledgeService.buildKnowledgePrompt(merchantId);
        return ResponseEntity.ok(Map.of("prompt", prompt));
    }
}
