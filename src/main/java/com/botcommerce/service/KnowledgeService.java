package com.botcommerce.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.botcommerce.model.KnowledgeEntry;
import com.botcommerce.model.KnowledgeSource;
import com.botcommerce.model.Merchant;
import com.botcommerce.repository.KnowledgeEntryRepository;
import com.botcommerce.repository.KnowledgeSourceRepository;
import com.botcommerce.repository.MerchantRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeService {

	private final KnowledgeSourceRepository sourceRepository;
	private final KnowledgeEntryRepository entryRepository;
	private final MerchantRepository merchantRepository;

	// ── Get all sources ──
	public List<Map<String, Object>> getSources(UUID merchantId) {
		return sourceRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId).stream().map(this::sourceToMap)
				.toList();
	}

	// ── Get stats ──
	public Map<String, Object> getStats(UUID merchantId) {
		List<KnowledgeSource> sources = sourceRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId);
		List<KnowledgeEntry> entries = entryRepository.findByMerchantId(merchantId);
		int totalChars = sources.stream().mapToInt(s -> s.getCharCount() != null ? s.getCharCount() : 0).sum();

		return Map.of("totalSources", sources.size(), "totalEntries", entries.size(), "totalChars", totalChars,
				"fileCount", sources.stream().filter(s -> "file".equals(s.getType())).count(), "websiteCount",
				sources.stream().filter(s -> "website".equals(s.getType())).count(), "textCount",
				sources.stream().filter(s -> "text".equals(s.getType())).count());
	}

	// ── Upload file ──
	@Transactional
	public Map<String, Object> uploadFile(UUID merchantId, MultipartFile file) {
		Merchant merchant = merchantRepository.findById(merchantId).orElseThrow();

		KnowledgeSource source = KnowledgeSource.builder().merchant(merchant).type("file")
				.name(file.getOriginalFilename()).fileName(file.getOriginalFilename()).status("processing").build();
		source = sourceRepository.save(source);

		try {
			String content = extractFileContent(file);
			source.setContent(content);
			source.setCharCount(content.length());
			source.setStatus("ready");

			// Create knowledge entries from content
			List<String> chunks = chunkText(content, 500);
			source.setChunksCount(chunks.size());

			for (int i = 0; i < chunks.size(); i++) {
				KnowledgeEntry entry = KnowledgeEntry.builder().merchant(merchant).type("file")
						.question(file.getOriginalFilename() + " (part " + (i + 1) + ")").answer(chunks.get(i)).build();
				entryRepository.save(entry);
			}

			sourceRepository.save(source);
			return sourceToMap(source);

		} catch (Exception e) {
			log.error("File processing failed: {}", e.getMessage());
			source.setStatus("failed");
			source.setErrorMessage(e.getMessage());
			sourceRepository.save(source);
			return sourceToMap(source);
		}
	}

	// ── Crawl website ──
	@Transactional
	public Map<String, Object> crawlWebsite(UUID merchantId, String url, int maxPages) {
		Merchant merchant = merchantRepository.findById(merchantId).orElseThrow();

		KnowledgeSource source = KnowledgeSource.builder().merchant(merchant).type("website").name(url).url(url)
				.status("processing").build();
		source = sourceRepository.save(source);

		try {
			StringBuilder allContent = new StringBuilder();
			Set<String> visited = new HashSet<>();
			Queue<String> toVisit = new LinkedList<>();
			toVisit.add(normalizeUrl(url));

			String baseDomain = extractDomain(url);
			int pagesCrawled = 0;

			while (!toVisit.isEmpty() && pagesCrawled < maxPages) {
				String currentUrl = toVisit.poll();
				if (visited.contains(currentUrl))
					continue;
				visited.add(currentUrl);

				try {
					Document doc = Jsoup.connect(currentUrl).userAgent("BotCommerce/1.0").timeout(10000).get();

					// Extract text
					String title = doc.title();
					String bodyText = doc.body().text();

					if (!bodyText.isBlank()) {
						allContent.append("--- Page: ").append(title).append(" ---\n");
						allContent.append(bodyText).append("\n\n");
						pagesCrawled++;
					}

					// Find more links on same domain
					if (pagesCrawled < maxPages) {
						Elements links = doc.select("a[href]");
						for (Element link : links) {
							String href = link.absUrl("href");
							if (href.contains(baseDomain) && !visited.contains(href) && !href.contains("#")
									&& !href.endsWith(".pdf") && !href.endsWith(".jpg") && !href.endsWith(".png")) {
								toVisit.add(href);
							}
						}
					}

				} catch (Exception e) {
					log.warn("Failed to crawl {}: {}", currentUrl, e.getMessage());
				}
			}

			String content = allContent.toString();
			source.setContent(content);
			source.setCharCount(content.length());
			source.setStatus("ready");

			// Create knowledge entries
			List<String> chunks = chunkText(content, 500);
			source.setChunksCount(chunks.size());

			for (int i = 0; i < chunks.size(); i++) {
				KnowledgeEntry entry = KnowledgeEntry.builder().merchant(merchant).type("website")
						.question(url + " (section " + (i + 1) + ")").answer(chunks.get(i)).build();
				entryRepository.save(entry);
			}

			sourceRepository.save(source);
			return sourceToMap(source);

		} catch (Exception e) {
			log.error("Website crawl failed: {}", e.getMessage());
			source.setStatus("failed");
			source.setErrorMessage(e.getMessage());
			sourceRepository.save(source);
			return sourceToMap(source);
		}
	}

	// ── Add manual text ──
	@Transactional
	public Map<String, Object> addText(UUID merchantId, String title, String content) {
		Merchant merchant = merchantRepository.findById(merchantId).orElseThrow();

		KnowledgeSource source = KnowledgeSource.builder().merchant(merchant).type("text").name(title).content(content)
				.charCount(content.length()).status("ready").build();

		List<String> chunks = chunkText(content, 500);
		source.setChunksCount(chunks.size());
		source = sourceRepository.save(source);

		for (int i = 0; i < chunks.size(); i++) {
			KnowledgeEntry entry = KnowledgeEntry.builder().merchant(merchant).type("text")
					.question(title + (chunks.size() > 1 ? " (part " + (i + 1) + ")" : "")).answer(chunks.get(i))
					.build();
			entryRepository.save(entry);
		}

		return sourceToMap(source);
	}

	// ── Add FAQ ──
	@Transactional
	public Map<String, Object> addFaq(UUID merchantId, String question, String answer) {
		Merchant merchant = merchantRepository.findById(merchantId).orElseThrow();

		KnowledgeEntry entry = KnowledgeEntry.builder().merchant(merchant).type("faq").question(question).answer(answer)
				.build();
		entry = entryRepository.save(entry);

		return Map.of("id", entry.getId().toString(), "type", "faq", "question", entry.getQuestion(), "answer",
				entry.getAnswer(), "createdAt", entry.getCreatedAt().toString());
	}

	// ── Delete source ──
	@Transactional
	public void deleteSource(UUID merchantId, UUID sourceId) {
		KnowledgeSource source = sourceRepository.findByIdAndMerchantId(sourceId, merchantId)
				.orElseThrow(() -> new RuntimeException("Source not found"));

		// Delete associated entries
		List<KnowledgeEntry> entries = entryRepository.findByMerchantId(merchantId);
		String sourceName = source.getName();
		entries.stream().filter(e -> e.getQuestion() != null && e.getQuestion().startsWith(sourceName))
				.forEach(entryRepository::delete);

		sourceRepository.delete(source);
	}

	// ── Delete FAQ entry ──
	@Transactional
	public void deleteEntry(UUID merchantId, UUID entryId) {
		KnowledgeEntry entry = entryRepository.findById(entryId).orElseThrow();
		if (!entry.getMerchant().getId().equals(merchantId))
			throw new RuntimeException("Forbidden");
		entryRepository.delete(entry);
	}

	// ── File content extraction ──
	private String extractFileContent(MultipartFile file) throws Exception {
		String filename = file.getOriginalFilename().toLowerCase();

		if (filename.endsWith(".pdf")) {
			return extractPdf(file);
		} else if (filename.endsWith(".txt") || filename.endsWith(".md") || filename.endsWith(".csv")) {
			return extractText(file);
		} else {
			throw new RuntimeException("Unsupported file type. Supported: PDF, TXT, MD, CSV");
		}
	}

	private String extractPdf(MultipartFile file) throws Exception {
		try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
			PDFTextStripper stripper = new PDFTextStripper();
			return stripper.getText(doc).trim();
		}
	}

	private String extractText(MultipartFile file) throws Exception {
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
			return reader.lines().collect(Collectors.joining("\n")).trim();
		}
	}

	// ── Text chunking ──
	private List<String> chunkText(String text, int maxChars) {
		if (text.length() <= maxChars)
			return List.of(text);

		List<String> chunks = new ArrayList<>();
		String[] paragraphs = text.split("\n\n");
		StringBuilder current = new StringBuilder();

		for (String para : paragraphs) {
			if (current.length() + para.length() > maxChars && current.length() > 0) {
				chunks.add(current.toString().trim());
				current = new StringBuilder();
			}
			current.append(para).append("\n\n");
		}

		if (current.length() > 0) {
			chunks.add(current.toString().trim());
		}

		return chunks;
	}

	// ── URL helpers ──
	private String normalizeUrl(String url) {
		if (!url.startsWith("http"))
			url = "https://" + url;
		if (url.endsWith("/"))
			url = url.substring(0, url.length() - 1);
		return url;
	}

	private String extractDomain(String url) {
		try {
			String host = new java.net.URL(normalizeUrl(url)).getHost();
			return host.startsWith("www.") ? host.substring(4) : host;
		} catch (Exception e) {
			return url;
		}
	}

	// ── Mapping ──
	private Map<String, Object> sourceToMap(KnowledgeSource s) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("id", s.getId().toString());
		m.put("type", s.getType());
		m.put("name", s.getName());
		m.put("url", s.getUrl());
		m.put("fileName", s.getFileName());
		m.put("status", s.getStatus());
		m.put("chunksCount", s.getChunksCount());
		m.put("charCount", s.getCharCount());
		m.put("errorMessage", s.getErrorMessage());
		m.put("createdAt", s.getCreatedAt().toString());
		return m;
	}
}