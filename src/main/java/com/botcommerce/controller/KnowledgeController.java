package com.botcommerce.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.botcommerce.model.KnowledgeEntry;
import com.botcommerce.repository.KnowledgeEntryRepository;
import com.botcommerce.service.KnowledgeService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

	private final KnowledgeService knowledgeService;
	private final KnowledgeEntryRepository entryRepository;

	@GetMapping("/sources")
	public ResponseEntity<List<Map<String, Object>>> getSources(Authentication auth) {
		UUID merchantId = (UUID) auth.getPrincipal();
		return ResponseEntity.ok(knowledgeService.getSources(merchantId));
	}

	@GetMapping("/stats")
	public ResponseEntity<Map<String, Object>> getStats(Authentication auth) {
		UUID merchantId = (UUID) auth.getPrincipal();
		return ResponseEntity.ok(knowledgeService.getStats(merchantId));
	}

	@GetMapping("/entries")
	public ResponseEntity<List<Map<String, Object>>> getEntries(Authentication auth) {
		UUID merchantId = (UUID) auth.getPrincipal();
		List<KnowledgeEntry> entries = entryRepository.findByMerchantId(merchantId);
		return ResponseEntity.ok(entries.stream().map(e -> {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", e.getId().toString());
			m.put("type", e.getType());
			m.put("question", e.getQuestion());
			m.put("answer", e.getAnswer());
			m.put("createdAt", e.getCreatedAt().toString());
			return m;
		}).toList());
	}

	@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Map<String, Object>> uploadFile(Authentication auth,
			@RequestParam("file") MultipartFile file) {
		UUID merchantId = (UUID) auth.getPrincipal();
		return ResponseEntity.ok(knowledgeService.uploadFile(merchantId, file));
	}

	@PostMapping("/website")
	public ResponseEntity<Map<String, Object>> crawlWebsite(Authentication auth,
			@RequestBody Map<String, Object> body) {
		UUID merchantId = (UUID) auth.getPrincipal();
		String url = (String) body.get("url");
		int maxPages = body.containsKey("maxPages") ? (int) body.get("maxPages") : 10;
		return ResponseEntity.ok(knowledgeService.crawlWebsite(merchantId, url, maxPages));
	}

	@PostMapping("/text")
	public ResponseEntity<Map<String, Object>> addText(Authentication auth, @RequestBody Map<String, String> body) {
		UUID merchantId = (UUID) auth.getPrincipal();
		return ResponseEntity.ok(knowledgeService.addText(merchantId, body.get("title"), body.get("content")));
	}

	@PostMapping("/faq")
	public ResponseEntity<Map<String, Object>> addFaq(Authentication auth, @RequestBody Map<String, String> body) {
		UUID merchantId = (UUID) auth.getPrincipal();
		return ResponseEntity.ok(knowledgeService.addFaq(merchantId, body.get("question"), body.get("answer")));
	}

	@DeleteMapping("/sources/{id}")
	public ResponseEntity<Void> deleteSource(Authentication auth, @PathVariable UUID id) {
		UUID merchantId = (UUID) auth.getPrincipal();
		knowledgeService.deleteSource(merchantId, id);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/entries/{id}")
	public ResponseEntity<Void> deleteEntry(Authentication auth, @PathVariable UUID id) {
		UUID merchantId = (UUID) auth.getPrincipal();
		knowledgeService.deleteEntry(merchantId, id);
		return ResponseEntity.noContent().build();
	}
}