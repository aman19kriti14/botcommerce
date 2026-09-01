package com.botcommerce.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.botcommerce.model.Cart;
import com.botcommerce.model.Conversation;
import com.botcommerce.model.Customer;
import com.botcommerce.model.KnowledgeEntry;
import com.botcommerce.model.Merchant;
import com.botcommerce.model.Message;
import com.botcommerce.model.Order;
import com.botcommerce.model.Product;
import com.botcommerce.repository.KnowledgeEntryRepository;
import com.botcommerce.repository.MessageRepository;
import com.botcommerce.repository.ProductRepository;
import com.botcommerce.service.CartService;
import com.botcommerce.service.OrderService;
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
	private final CartService cartService;
	private final OrderService orderService;

	public String generateResponse(Merchant merchant, Customer customer, Conversation conversation,
			String userMessage) {
		try {
			String systemPrompt = buildSystemPrompt(merchant, customer, conversation);
			List<Map<String, String>> messages = buildMessages(conversation, userMessage);

			Map<String, Object> request = new HashMap<>();
			request.put("model", model);
			request.put("max_tokens", 800);
			request.put("system", systemPrompt);
			request.put("messages", messages);
			request.put("tools", getTools());

			String requestBody = objectMapper.writeValueAsString(request);

			WebClient client = WebClient.builder().baseUrl("https://api.anthropic.com")
					.defaultHeader("x-api-key", apiKey).defaultHeader("anthropic-version", "2023-06-01")
					.defaultHeader("content-type", "application/json").build();

			String response = client.post().uri("/v1/messages").bodyValue(requestBody).retrieve()
					.bodyToMono(String.class).block();

			return processResponse(response, merchant, customer, conversation);

		} catch (Exception e) {
			log.error("AI generation failed", e);
			return "Sorry, I'm having trouble right now. Please try again! 🙏";
		}
	}

	private String processResponse(String response, Merchant merchant, Customer customer, Conversation conversation)
			throws Exception {
		JsonNode root = objectMapper.readTree(response);
		JsonNode content = root.path("content");

		StringBuilder textResponse = new StringBuilder();
		List<Map<String, Object>> toolResults = new ArrayList<>();

		for (JsonNode block : content) {
			String type = block.path("type").asText();

			if ("text".equals(type)) {
				textResponse.append(block.path("text").asText());
			} else if ("tool_use".equals(type)) {
				String toolName = block.path("name").asText();
				String toolId = block.path("id").asText();
				JsonNode input = block.path("input");

				String result = executeTool(toolName, input, merchant, customer);
				toolResults.add(Map.of("type", "tool_result", "tool_use_id", toolId, "content", result));
			}
		}

		// If there were tool calls, make a follow-up request with results
		if (!toolResults.isEmpty()) {
			return makeFollowUp(root, toolResults, merchant, customer, conversation);
		}

		return textResponse.toString();
	}

	private String executeTool(String toolName, JsonNode input, Merchant merchant, Customer customer) {
		try {
			return switch (toolName) {
			case "add_to_cart" -> {
				String productName = input.path("product_name").asText();
				int qty = input.has("quantity") ? input.path("quantity").asInt() : 1;

				Product product = findProductByName(merchant.getId(), productName);
				if (product == null)
					yield "Product not found: " + productName;

				Cart cart = cartService.getOrCreateCart(customer, merchant);
				CartService.CartSummary summary = cartService.addItem(cart, product.getId(), qty);
				yield "Added " + qty + "x " + product.getName() + " to cart. " + summary.toDisplayString();
			}
			case "remove_from_cart" -> {
				String productName = input.path("product_name").asText();
				Product product = findProductByName(merchant.getId(), productName);
				if (product == null)
					yield "Product not found: " + productName;

				Cart cart = cartService.getOrCreateCart(customer, merchant);
				CartService.CartSummary summary = cartService.removeItem(cart, product.getId());
				yield "Removed " + product.getName() + ". " + summary.toDisplayString();
			}
			case "view_cart" -> {
				Cart cart = cartService.getOrCreateCart(customer, merchant);
				CartService.CartSummary summary = cartService.getCartSummary(cart);
				yield summary.toDisplayString();
			}
			case "place_order" -> {
				String address = input.path("delivery_address").asText();
				String type = input.has("delivery_type") ? input.path("delivery_type").asText() : "delivery";
				String note = input.has("customer_note") ? input.path("customer_note").asText() : null;

				Cart cart = cartService.getOrCreateCart(customer, merchant);
				Order order = orderService.createOrder(cart, customer, merchant, address, type, note);

				String upiLink = merchant.getUpiId() != null
						? "upi://pay?pa=" + merchant.getUpiId() + "&am=" + order.getTotal().toPlainString()
								+ "&tn=Order " + order.getOrderNumber()
						: "UPI ID not set";

				yield "Order " + order.getOrderNumber() + " placed! Total: ₹"
						+ order.getTotal().stripTrailingZeros().toPlainString() + ". Payment link: " + upiLink;
			}
			default -> "Unknown action";
			};
		} catch (Exception e) {
			log.error("Tool execution failed: {}", toolName, e);
			return "Error: " + e.getMessage();
		}
	}

	private String makeFollowUp(JsonNode originalResponse, List<Map<String, Object>> toolResults, Merchant merchant,
			Customer customer, Conversation conversation) throws Exception {
		// Build follow-up messages
		List<Map<String, Object>> messages = new ArrayList<>();

		// Add conversation history
		List<Message> recent = messageRepository.findRecentByConversationId(conversation.getId(), 10);
		for (Message m : recent) {
			String role = "customer".equals(m.getSenderType()) ? "user" : "assistant";
			messages.add(Map.of("role", role, "content", m.getContent()));
		}

		// Add assistant response with tool use
		messages.add(Map.of("role", "assistant", "content", originalResponse.path("content")));

		// Add tool results
		messages.add(Map.of("role", "user", "content", toolResults));

		Map<String, Object> request = new HashMap<>();
		request.put("model", model);
		request.put("max_tokens", 500);
		request.put("system", buildSystemPrompt(merchant, customer, conversation));
		request.put("messages", messages);

		String requestBody = objectMapper.writeValueAsString(request);

		WebClient client = WebClient.builder().baseUrl("https://api.anthropic.com").defaultHeader("x-api-key", apiKey)
				.defaultHeader("anthropic-version", "2023-06-01").defaultHeader("content-type", "application/json")
				.build();

		String response = client.post().uri("/v1/messages").bodyValue(requestBody).retrieve().bodyToMono(String.class)
				.block();

		JsonNode root = objectMapper.readTree(response);
		return root.path("content").get(0).path("text").asText();
	}

	private Product findProductByName(UUID merchantId, String name) {
		List<Product> products = productRepository.findAvailableByMerchantId(merchantId);
		String lower = name.toLowerCase();
		return products.stream()
				.filter(p -> p.getName().toLowerCase().contains(lower) || lower.contains(p.getName().toLowerCase()))
				.findFirst().orElse(null);
	}

	private List<Object> getTools() {
		return List.of(Map.of("name", "add_to_cart", "description",
				"Add a product to the customer's cart. Use when customer wants to order something.", "input_schema",
				Map.of("type", "object", "properties",
						Map.of("product_name", Map.of("type", "string", "description", "Name of the product to add"),
								"quantity", Map.of("type", "integer", "description", "Quantity to add, default 1")),
						"required", List.of("product_name"))),
				Map.of("name", "remove_from_cart", "description", "Remove a product from the cart.", "input_schema",
						Map.of("type", "object", "properties",
								Map.of("product_name",
										Map.of("type", "string", "description", "Name of the product to remove")),
								"required", List.of("product_name"))),
				Map.of("name", "view_cart", "description",
						"Show the current cart contents. Use when customer asks to see their cart or wants to review before ordering.",
						"input_schema", Map.of("type", "object", "properties", Map.of())),
				Map.of("name", "place_order", "description",
						"Place the order. Use ONLY when customer has confirmed items AND provided a delivery address.",
						"input_schema",
						Map.of("type", "object", "properties", Map.of("delivery_address",
								Map.of("type", "string", "description", "Customer's delivery address"), "delivery_type",
								Map.of("type", "string", "enum", List.of("delivery", "pickup")), "customer_note",
								Map.of("type", "string", "description", "Any special instructions")), "required",
								List.of("delivery_address"))));
	}

	private String buildSystemPrompt(Merchant merchant, Customer customer, Conversation conversation) {
		List<Product> products = productRepository.findAvailableByMerchantId(merchant.getId());
		String catalog = products.stream()
				.map(p -> String.format("- %s: ₹%s%s%s", p.getName(), p.getPrice().stripTrailingZeros().toPlainString(),
						p.getDescription() != null ? " — " + p.getDescription() : "",
						p.getTags() != null ? " [" + p.getTags() + "]" : ""))
				.collect(Collectors.joining("\n"));

		List<KnowledgeEntry> knowledge = knowledgeEntryRepository.findByMerchantId(merchant.getId());
		String knowledgeText = knowledge.stream()
				.map(k -> k.getQuestion() != null ? "Q: " + k.getQuestion() + "\nA: " + k.getAnswer() : k.getAnswer())
				.collect(Collectors.joining("\n\n"));

		// Get current cart
		Cart cart = cartService.getOrCreateCart(customer, merchant);
		String cartText = cartService.getCartSummary(cart).toDisplayString();

		return String.format("""
				You are the AI shopping assistant for "%s", a %s business in %s.
				Personality: %s tone, speak in %s.

				PRODUCT CATALOG:
				%s

				BUSINESS KNOWLEDGE:
				%s

				Delivery areas: %s
				Minimum order: ₹%s
				Delivery fee: ₹%s

				CURRENT CART:
				%s

				CUSTOM RULES:
				%s

				INSTRUCTIONS:
				- Help customers browse, answer questions, and take orders.
				- Use the add_to_cart tool when customer wants to buy something.
				- Use view_cart when they want to see their cart.
				- Use place_order ONLY when customer has confirmed items AND given a delivery address.
				- Ask for delivery address before placing order.
				- Keep responses SHORT — 2-3 sentences max. This is WhatsApp.
				- Use emojis naturally.
				- NEVER make up products or prices not in the catalog.
				- If customer speaks Hindi, reply in Hindi/Hinglish.
				- When showing cart after adding items, include the total.
				""", merchant.getBusinessName(), merchant.getCategory() != null ? merchant.getCategory() : "general",
				merchant.getCity() != null ? merchant.getCity() : "India",
				merchant.getBotTone() != null ? merchant.getBotTone() : "friendly",
				merchant.getBotLanguage() != null ? merchant.getBotLanguage() : "English",
				catalog.isEmpty() ? "No products added yet" : catalog,
				knowledgeText.isEmpty() ? "No additional info" : knowledgeText,
				merchant.getDeliveryAreas() != null ? merchant.getDeliveryAreas() : "Not specified",
				merchant.getMinOrderAmount().stripTrailingZeros().toPlainString(),
				merchant.getDeliveryFee().stripTrailingZeros().toPlainString(), cartText,
				merchant.getBotRules() != null ? merchant.getBotRules() : "None");
	}

	private List<Map<String, String>> buildMessages(Conversation conversation, String userMessage) {
		List<Message> recent = messageRepository.findRecentByConversationId(conversation.getId(), 10);

		List<Map<String, String>> messages = new ArrayList<>();
		for (Message m : recent) {
			String role = "customer".equals(m.getSenderType()) ? "user" : "assistant";
			messages.add(Map.of("role", role, "content", m.getContent()));
		}
		messages.add(Map.of("role", "user", "content", userMessage));

		return messages;
	}
}