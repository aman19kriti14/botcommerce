package com.botcommerce.ai;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.botcommerce.model.Conversation;
import com.botcommerce.model.Customer;
import com.botcommerce.model.KnowledgeEntry;
import com.botcommerce.model.Merchant;
import com.botcommerce.model.Message;
import com.botcommerce.model.Product;
import com.botcommerce.repository.KnowledgeEntryRepository;
import com.botcommerce.repository.MessageRepository;
import com.botcommerce.repository.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

	@Value("${ai.api-key}")
	private String apiKey;

	@Value("${ai.model}")
	private String model;

	private final ObjectMapper objectMapper;
	private final ProductRepository productRepository;
	private final KnowledgeEntryRepository knowledgeEntryRepository;
	private final MessageRepository messageRepository;

	public String generateResponse(Merchant merchant, Customer customer, Conversation conversation,
			String userMessage) {
		try {
			String systemPrompt = buildSystemPrompt(merchant);
			String conversationHistory = buildConversationHistory(conversation);

			String requestBody = objectMapper.writeValueAsString(Map.of("model", model, "max_tokens", 500, "system",
					systemPrompt, "messages", buildMessages(conversationHistory, userMessage)));

			WebClient client = WebClient.builder().baseUrl("https://api.anthropic.com")
					.defaultHeader("x-api-key", apiKey).defaultHeader("anthropic-version", "2023-06-01")
					.defaultHeader("content-type", "application/json").build();

			String response = client.post().uri("/v1/messages").bodyValue(requestBody).retrieve()
					.bodyToMono(String.class).block();

			JsonNode root = objectMapper.readTree(response);
			return root.path("content").get(0).path("text").asText();

		} catch (Exception e) {
			log.error("AI generation failed", e);
			return "Sorry, I'm having trouble right now. Please try again in a moment!";
		}
	}

	private String buildSystemPrompt(Merchant merchant) {
		// Get products
		List<Product> products = productRepository.findAvailableByMerchantId(merchant.getId());
		String catalog = products.stream()
				.map(p -> String.format("- %s: ₹%s%s%s", p.getName(), p.getPrice().stripTrailingZeros().toPlainString(),
						p.getDescription() != null ? " — " + p.getDescription() : "",
						p.getTags() != null ? " [" + p.getTags() + "]" : ""))
				.collect(Collectors.joining("\n"));

		// Get knowledge base
		List<KnowledgeEntry> knowledge = knowledgeEntryRepository.findByMerchantId(merchant.getId());
		String knowledgeText = knowledge.stream().map(k -> {
			if (k.getQuestion() != null) {
				return "Q: " + k.getQuestion() + "\nA: " + k.getAnswer();
			}
			return k.getAnswer();
		}).collect(Collectors.joining("\n\n"));

		return String.format("""
				You are the AI shopping assistant for "%s", a %s business in %s.

				Your personality: %s tone, speak in %s.

				PRODUCT CATALOG:
				%s

				BUSINESS KNOWLEDGE:
				%s

				Delivery areas: %s
				Minimum order: ₹%s
				Delivery fee: ₹%s

				CUSTOM RULES:
				%s

				INSTRUCTIONS:
				- You help customers browse products, answer questions, and place orders.
				- When a customer wants to order, confirm the items and quantities, then ask for delivery address.
				- After getting the address, show an order summary with total and say you'll send a payment link.
				- Keep responses SHORT — max 2-3 sentences. This is WhatsApp, not email.
				- Use emojis naturally but don't overdo it.
				- If asked about something not in your knowledge, say you'll check with the shop owner.
				- NEVER make up products, prices, or policies that aren't in your catalog/knowledge.
				- Format prices as ₹XXX.
				- If the customer just says hi/hello, greet them warmly and show a few popular items.
				""", merchant.getBusinessName(), merchant.getCategory() != null ? merchant.getCategory() : "general",
				merchant.getCity() != null ? merchant.getCity() : "India",
				merchant.getBotTone() != null ? merchant.getBotTone() : "friendly",
				merchant.getBotLanguage() != null ? merchant.getBotLanguage() : "English",
				catalog.isEmpty() ? "No products added yet" : catalog,
				knowledgeText.isEmpty() ? "No additional info provided" : knowledgeText,
				merchant.getDeliveryAreas() != null ? merchant.getDeliveryAreas() : "Not specified",
				merchant.getMinOrderAmount().stripTrailingZeros().toPlainString(),
				merchant.getDeliveryFee().stripTrailingZeros().toPlainString(),
				merchant.getBotRules() != null ? merchant.getBotRules() : "None");
	}

	private String buildConversationHistory(Conversation conversation) {
		List<Message> recent = messageRepository.findRecentByConversationId(conversation.getId(), 10);

		return recent.stream().map(m -> {
			String role = "customer".equals(m.getSenderType()) ? "Customer" : "Assistant";
			return role + ": " + m.getContent();
		}).collect(Collectors.joining("\n"));
	}

	private Object buildMessages(String history, String userMessage) {
		String fullMessage = history.isEmpty() ? userMessage
				: "Previous conversation:\n" + history + "\n\nCustomer: " + userMessage;

		return List.of(Map.of("role", "user", "content", fullMessage));
	}
}