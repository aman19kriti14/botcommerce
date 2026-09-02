package com.botcommerce.service;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TextExtractorService {

	private static final Logger log = LoggerFactory.getLogger(TextExtractorService.class);

	/**
	 * Extract text from an uploaded file based on its content type.
	 */
	public String extractFromFile(MultipartFile file) throws Exception {
		String contentType = file.getContentType();
		String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";

		if (contentType != null && contentType.contains("pdf") || filename.endsWith(".pdf")) {
			return extractFromPdf(file.getInputStream());
		}
		if (filename.endsWith(".docx") || (contentType != null && contentType.contains("wordprocessingml"))) {
			return extractFromDocx(file.getInputStream());
		}
		if (filename.endsWith(".xlsx") || filename.endsWith(".xls")
				|| (contentType != null && contentType.contains("spreadsheet"))) {
			return extractFromExcel(file.getInputStream());
		}
		if (filename.endsWith(".csv") || filename.endsWith(".tsv")
				|| (contentType != null && contentType.contains("csv"))) {
			return extractFromCsv(file.getInputStream());
		}
		if (contentType != null && contentType.startsWith("text/") || filename.endsWith(".txt")
				|| filename.endsWith(".md")) {
			return new String(file.getBytes(), StandardCharsets.UTF_8);
		}

		// Fallback: try reading as text
		try {
			return new String(file.getBytes(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new UnsupportedOperationException("Cannot extract text from file type: " + contentType);
		}
	}

	/**
	 * Extract text from a PDF using Apache PDFBox.
	 */
	private String extractFromPdf(InputStream is) throws Exception {
		try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(is))) {
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setSortByPosition(true);
			return stripper.getText(doc).trim();
		}
	}

	/**
	 * Extract text from a DOCX using Apache POI.
	 */
	private String extractFromDocx(InputStream is) throws Exception {
		try (XWPFDocument doc = new XWPFDocument(is)) {
			StringBuilder sb = new StringBuilder();
			for (XWPFParagraph para : doc.getParagraphs()) {
				String text = para.getText();
				if (text != null && !text.isBlank()) {
					sb.append(text.trim()).append("\n");
				}
			}
			// Also extract from tables
			doc.getTables().forEach(table -> {
				table.getRows().forEach(row -> {
					StringBuilder rowText = new StringBuilder();
					row.getTableCells().forEach(cell -> {
						if (!cell.getText().isBlank()) {
							if (rowText.length() > 0)
								rowText.append(" | ");
							rowText.append(cell.getText().trim());
						}
					});
					if (rowText.length() > 0)
						sb.append(rowText).append("\n");
				});
				sb.append("\n");
			});
			return sb.toString().trim();
		}
	}

	/**
	 * Extract text from Excel files — converts each sheet to readable text.
	 */
	private String extractFromExcel(InputStream is) throws Exception {
		try (Workbook wb = new XSSFWorkbook(is)) {
			StringBuilder sb = new StringBuilder();
			DataFormatter fmt = new DataFormatter();

			for (int s = 0; s < wb.getNumberOfSheets(); s++) {
				Sheet sheet = wb.getSheetAt(s);
				sb.append("## ").append(sheet.getSheetName()).append("\n");

				for (Row row : sheet) {
					StringBuilder rowText = new StringBuilder();
					for (Cell cell : row) {
						String val = fmt.formatCellValue(cell).trim();
						if (!val.isEmpty()) {
							if (rowText.length() > 0)
								rowText.append(" | ");
							rowText.append(val);
						}
					}
					if (rowText.length() > 0)
						sb.append(rowText).append("\n");
				}
				sb.append("\n");
			}
			return sb.toString().trim();
		}
	}

	/**
	 * Read CSV/TSV as text (preserving structure).
	 */
	private String extractFromCsv(InputStream is) throws Exception {
		return new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
	}

	// ──────────────── Website Crawling ────────────────

	/**
	 * Crawl a website and extract text from all reachable pages. Returns a map of
	 * URL → extracted text.
	 */
	public CrawlResult crawlWebsite(String startUrl, int maxPages) {
		CrawlResult result = new CrawlResult();
		Set<String> visited = new HashSet<>();
		Queue<String> queue = new LinkedList<>();

		String baseHost;
		try {
			baseHost = new URI(startUrl).getHost();
		} catch (Exception e) {
			result.error = "Invalid URL: " + startUrl;
			return result;
		}

		// Normalize URL
		if (!startUrl.startsWith("http")) {
			startUrl = "https://" + startUrl;
		}
		queue.add(startUrl);

		while (!queue.isEmpty() && visited.size() < maxPages) {
			String url = queue.poll();
			if (visited.contains(url))
				continue;
			visited.add(url);

			try {
				Document doc = Jsoup.connect(url).userAgent("BotCommerce/1.0 (Knowledge Crawler)").timeout(10_000)
						.followRedirects(true).get();

				String title = doc.title();
				String text = extractPageText(doc);

				if (!text.isBlank()) {
					CrawlResult.Page page = new CrawlResult.Page();
					page.url = url;
					page.title = title.isBlank() ? url : title;
					page.text = text;
					result.pages.add(page);
				}

				// Find links to crawl next (same domain only)
				Elements links = doc.select("a[href]");
				for (Element link : links) {
					String href = link.absUrl("href");
					if (href.isEmpty())
						continue;
					try {
						String linkHost = new URI(href).getHost();
						if (baseHost.equals(linkHost) && !visited.contains(href) && !href.contains("#")
								&& !href.matches(".*\\.(jpg|jpeg|png|gif|svg|pdf|zip|mp4|mp3)$")) {
							queue.add(href.split("\\?")[0]); // strip query params
						}
					} catch (Exception ignored) {
					}
				}

			} catch (Exception e) {
				log.warn("Failed to crawl {}: {}", url, e.getMessage());
			}
		}

		return result;
	}

	/**
	 * Extract meaningful text from an HTML document, stripping nav/footer/scripts.
	 */
	private String extractPageText(Document doc) {
		// Remove non-content elements
		doc.select("script, style, nav, footer, header, .nav, .footer, .header, "
				+ ".sidebar, .menu, .cookie, .popup, .modal, .advertisement, noscript, iframe").remove();

		// Try to find main content area
		Element main = doc.selectFirst("main, article, [role=main], .content, .main, #content, #main");
		String text;
		if (main != null) {
			text = main.text();
		} else {
			text = doc.body() != null ? doc.body().text() : doc.text();
		}

		// Clean up: collapse whitespace, remove very short results
		text = text.replaceAll("\\s+", " ").trim();
		return text.length() < 20 ? "" : text;
	}

	// ── Crawl result holder ──
	public static class CrawlResult {
		public List<Page> pages = new ArrayList<>();
		public String error;

		public static class Page {
			public String url;
			public String title;
			public String text;
		}
	}

	// ──────────────── Text Chunking ────────────────

	/**
	 * Split text into chunks of ~500 chars with overlap. Splits at sentence
	 * boundaries where possible.
	 */
	public List<String> chunkText(String text, int chunkSize, int overlap) {
		if (text == null || text.isBlank())
			return List.of();

		List<String> chunks = new ArrayList<>();
		// Split into sentences
		String[] sentences = text.split("(?<=[.!?।])\\s+");

		StringBuilder current = new StringBuilder();
		StringBuilder overlapBuffer = new StringBuilder();

		for (String sentence : sentences) {
			sentence = sentence.trim();
			if (sentence.isEmpty())
				continue;

			if (current.length() + sentence.length() > chunkSize && current.length() > 0) {
				chunks.add(current.toString().trim());

				// Keep overlap from end of current chunk
				String currentText = current.toString();
				int overlapStart = Math.max(0, currentText.length() - overlap);
				current = new StringBuilder(currentText.substring(overlapStart));
			}

			current.append(sentence).append(" ");
		}

		if (current.length() > 0) {
			chunks.add(current.toString().trim());
		}

		return chunks;
	}

	/**
	 * Default chunking: 500 chars, 50 char overlap.
	 */
	public List<String> chunkText(String text) {
		return chunkText(text, 500, 50);
	}
}
