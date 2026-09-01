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
import com.botcommerce.repository.OrderRepository;
import com.botcommerce.repository.ProductRepository;
import com.botcommerce.service.CartService;
import com.botcommerce.service.OrderService;
import com.botcommerce.whatsapp.WhatsAppSender;
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
	private final OrderRepository orderRepository;
	private final WhatsAppSender whatsAppSender;

	public String generateResponse(Merchant merchant, Customer customer, Conversation conversation,
			String userMessage) {
		try {
			// Step 1: Ask AI to decide what action to take
			String actionResponse = callAi(buildActionPrompt(merchant, customer),
					buildMessages(conversation, userMessage));

			// Step 2: Parse and execute action
			String actionResult = executeAction(actionResponse, merchant, customer);

			// Step 3: Generate user-friendly response
			String systemPrompt = buildResponsePrompt(merchant, customer, actionResult);
			List<Map<String, String>> messages = buildMessages(conversation, userMessage);

			if (actionResult != null) {
				messages.add(Map.of("role", "assistant", "content", "[Action taken: " + actionResult + "]"));
				messages.add(Map.of("role", "user", "content",
						"Based on the action taken, respond to the customer naturally. Keep it short, 2-3 sentences max."));
			}

			return callAi(systemPrompt, messages);

		} catch (Exception e) {
			log.error("AI generation failed", e);
			return "Sorry, I'm having trouble right now. Please try again! 🙏";
		}
	}

	private String callAi(String systemPrompt, List<Map<String, String>> messages) throws Exception {
		Map<String, Object> request = Map.of("model", model, "max_tokens", 400, "system", systemPrompt, "messages",
				messages);

		String requestBody = objectMapper.writeValueAsString(request);

		WebClient client = WebClient.builder().baseUrl("https://api.anthropic.com").defaultHeader("x-api-key", apiKey)
				.defaultHeader("anthropic-version", "2023-06-01").defaultHeader("content-type", "application/json")
				.build();

		String response = client.post().uri("/v1/messages").bodyValue(requestBody).retrieve().bodyToMono(String.class)
				.block();

		JsonNode root = objectMapper.readTree(response);
		return root.path("content").get(0).path("text").asText();
	}

	private String executeAction(String aiResponse, Merchant merchant, Customer customer) {
		try {
			// Extract JSON action from response
			String json = extractJson(aiResponse);
			if (json == null)
				return null;

			JsonNode action = objectMapper.readTree(json);
			String type = action.path("action").asText();

			Cart cart = cartService.getOrCreateCart(customer, merchant);

			return switch (type) {
			case "add_to_cart" -> {
				String productName = action.path("product_name").asText();
				int qty = action.has("quantity") ? action.path("quantity").asInt() : 1;
				Product product = findProductByName(merchant.getId(), productName);
				if (product == null)
					yield "Product not found: " + productName;
				CartService.CartSummary summary = cartService.addItem(cart, product.getId(), qty);
				yield "Added " + qty + "x " + product.getName() + ". " + summary.toDisplayString();
			}
			case "set_quantity" -> {
				String productName = action.path("product_name").asText();
				int qty = action.path("quantity").asInt();
				Product product = findProductByName(merchant.getId(), productName);
				if (product == null)
					yield "Product not found: " + productName;
				// Remove existing and add with correct quantity
				cartService.removeItem(cart, product.getId());
				if (qty > 0) {
					CartService.CartSummary summary = cartService.addItem(cart, product.getId(), qty);
					yield "Set " + product.getName() + " to " + qty + ". " + summary.toDisplayString();
				} else {
					CartService.CartSummary summary = cartService.getCartSummary(cart);
					yield "Removed " + product.getName() + ". " + summary.toDisplayString();
				}
			}
			case "remove_from_cart" -> {
				String productName = action.path("product_name").asText();
				Product product = findProductByName(merchant.getId(), productName);
				if (product == null)
					yield "Product not found: " + productName;
				CartService.CartSummary summary = cartService.removeItem(cart, product.getId());
				yield "Removed " + product.getName() + ". " + summary.toDisplayString();
			}
			case "clear_cart" -> {
				CartService.CartSummary summary = cartService.clearCart(cart);
				yield "Cart cleared. " + summary.toDisplayString();
			}
			case "view_cart" -> {
				CartService.CartSummary summary = cartService.getCartSummary(cart);
				yield summary.toDisplayString();
			}
			case "place_order" -> {
				String address = action.path("delivery_address").asText();
				String deliveryType = action.has("delivery_type") ? action.path("delivery_type").asText() : "delivery";
				String note = action.has("customer_note") ? action.path("customer_note").asText() : null;
				CartService.CartSummary summary = cartService.getCartSummary(cart);
				if (summary.getItems().isEmpty()) {
					yield "Cart is empty — no order to place. Customer may have already ordered.";
				}
				Order order = orderService.createOrder(cart, customer, merchant, address, deliveryType, note);
				String upiLink = merchant.getUpiId() != null
						? "upi://pay?pa=" + merchant.getUpiId() + "&am=" + order.getTotal().toPlainString()
								+ "&tn=Order+" + order.getOrderNumber()
						: null;
				yield "Order placed! " + order.getOrderNumber() + " | Total: ₹"
						+ order.getTotal().stripTrailingZeros().toPlainString()
						+ (upiLink != null ? " | UPI: " + upiLink : "");
			}
			case "payment_done" -> {
				// Find the latest order for this customer with this merchant
				String merchantPhone = merchant.getWhatsappPhone();
				if (merchantPhone != null && !merchantPhone.startsWith("91")) {
					merchantPhone = "91" + merchantPhone;
				}
				// Find latest placed order
				Order latestOrder = orderRepository
						.findTopByCustomerIdAndMerchantIdOrderByCreatedAtDesc(customer.getId(), merchant.getId())
						.orElse(null);
				if (latestOrder == null) {
					yield "No recent order found.";
				}
				if (merchantPhone != null) {
					whatsAppSender.sendButtons(merchantPhone,
							"💰 Customer says payment done for *" + latestOrder.getOrderNumber() + "* (₹"
									+ latestOrder.getTotal().stripTrailingZeros().toPlainString()
									+ "). Did you receive it?",
							List.of(Map.of("id", "payconfirm_" + latestOrder.getId(), "title", "✅ Received"),
									Map.of("id", "payreject_" + latestOrder.getId(), "title", "❌ Not Received")));
				}
				yield "Payment verification sent to shop owner for " + latestOrder.getOrderNumber();
			}
			case "none" -> null;
			default -> null;
			};
		} catch (Exception e) {
			log.error("Action execution failed: {}", e.getMessage());
			return null;
		}
	}

	private String extractJson(String text) {
		// Find JSON block in response
		int start = text.indexOf("{");
		int end = text.lastIndexOf("}");
		if (start != -1 && end != -1 && end > start) {
			return text.substring(start, end + 1);
		}
		return null;
	}

	private Product findProductByName(UUID merchantId, String name) {
		List<Product> products = productRepository.findAvailableByMerchantId(merchantId);
		String lower = name.toLowerCase();
		return products.stream()
				.filter(p -> p.getName().toLowerCase().contains(lower) || lower.contains(p.getName().toLowerCase()))
				.findFirst().orElse(null);
	}

	private String buildActionPrompt(Merchant merchant, Customer customer) {
		List<Product> products = productRepository.findAvailableByMerchantId(merchant.getId());
		String catalog = products.stream()
				.map(p -> p.getName() + ": ₹" + p.getPrice().stripTrailingZeros().toPlainString())
				.collect(Collectors.joining(", "));

		Cart cart = cartService.getOrCreateCart(customer, merchant);
		String cartText = cartService.getCartSummary(cart).toDisplayString();

		return String.format(
				"""
						You are an action parser for a WhatsApp shopping bot for "%s".

						Products: %s
						Current cart: %s

						Based on the customer's message, respond with ONLY a JSON object for the action to take.
						No other text, just the JSON.

						Available actions:
						{"action": "add_to_cart", "product_name": "...", "quantity": 1}
						{"action": "set_quantity", "product_name": "...", "quantity": 1}  — use when customer wants to change quantity, e.g. "only 1", "make it 2"
						{"action": "remove_from_cart", "product_name": "..."}
						{"action": "clear_cart"}
						{"action": "view_cart"}
						{"action": "place_order", "delivery_address": "...", "delivery_type": "delivery", "customer_note": "..."}
						{"action": "payment_done", "order_number": "ORD-XXXXX"} — when customer says they've paid or payment is done
						{"action": "none"} — for questions, greetings, or when no cart action is needed

						RULES:
						- If customer mentions a product by name or number and seems to want it, use add_to_cart.
						- If they say "only 1" or "just 1" or "sirf ek", use set_quantity with quantity 1.
						- If they provide an address and want to order, use place_order.
						- If they just ask a question or say hi, use "none".
						- If customer says a number (1, 2, 3, 4), match it to the product list order.
						- Respond with ONLY the JSON. Nothing else.
						""",
				merchant.getBusinessName(), catalog, cartText);
	}

	private String buildResponsePrompt(Merchant merchant, Customer customer, String actionResult) {
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

		Cart cart = cartService.getOrCreateCart(customer, merchant);
		String cartText = cartService.getCartSummary(cart).toDisplayString();

		return String.format("""
				You are the friendly AI assistant for "%s", a %s business in %s.
				Tone: %s. Language: %s.

				CATALOG:
				%s

				KNOWLEDGE:
				%s

				CURRENT CART: %s
				DELIVERY FEE: ₹%s

				%s

				RULES:
				- This is WhatsApp. NEVER use markdown tables, headers, or bullet points.
				- Keep responses to 2-3 sentences MAX.
				- Use *bold* for product names and prices only.
				- Use emojis naturally but sparingly.
				- If customer speaks Hindi, reply in Hindi/Hinglish.
				- NEVER make up products or prices.
				- If an order was just placed, include the order number and payment link.
				- If customer says "payment done", say thanks and that the shop will confirm shortly.
				- After placing order, do NOT ask for items or address again.
				""", merchant.getBusinessName(), merchant.getCategory() != null ? merchant.getCategory() : "general",
				merchant.getCity() != null ? merchant.getCity() : "India",
				merchant.getBotTone() != null ? merchant.getBotTone() : "friendly",
				merchant.getBotLanguage() != null ? merchant.getBotLanguage() : "English",
				catalog.isEmpty() ? "No products yet" : catalog, knowledgeText.isEmpty() ? "None" : knowledgeText,
				cartText, merchant.getDeliveryFee().stripTrailingZeros().toPlainString(),
				actionResult != null ? "LAST ACTION RESULT: " + actionResult : "");
	}

	private List<Map<String, String>> buildMessages(Conversation conversation, String userMessage) {
		List<Message> recent = messageRepository.findRecentByConversationId(conversation.getId(), 10);

		List<Map<String, String>> messages = new ArrayList<>();
		for (Message m : recent) {
			String role = "customer".equals(m.getSenderType()) ? "user" : "assistant";
			messages.add(new HashMap<>(Map.of("role", role, "content", m.getContent())));
		}
		messages.add(new HashMap<>(Map.of("role", "user", "content", userMessage)));

		return messages;
	}
}