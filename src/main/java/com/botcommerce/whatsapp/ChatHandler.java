package com.botcommerce.whatsapp;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.botcommerce.ai.AiService;
import com.botcommerce.model.Conversation;
import com.botcommerce.model.Customer;
import com.botcommerce.model.CustomerMerchant;
import com.botcommerce.model.Merchant;
import com.botcommerce.model.Message;
import com.botcommerce.model.Order;
import com.botcommerce.repository.ConversationRepository;
import com.botcommerce.repository.CustomerMerchantRepository;
import com.botcommerce.repository.CustomerRepository;
import com.botcommerce.repository.MerchantRepository;
import com.botcommerce.repository.MessageRepository;
import com.botcommerce.repository.OrderRepository;
import com.botcommerce.service.OrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatHandler {

	private final WhatsAppSender sender;
	private final AiService aiService;
	private final OrderService orderService;
	private final CustomerRepository customerRepository;
	private final MerchantRepository merchantRepository;
	private final ConversationRepository conversationRepository;
	private final MessageRepository messageRepository;
	private final CustomerMerchantRepository customerMerchantRepository;
	private final OrderRepository orderRepository;
	private final WhatsAppSender whatsAppSender;

	@Transactional
	public void handle(String from, String text, String messageId) {
		// Check if this is a merchant action (accept/reject order)
		if (text.startsWith("accept_") || text.startsWith("reject_")) {
			handleMerchantAction(from, text);
			return;
		}

		// Get or create customer
		Customer customer = customerRepository.findByWhatsappPhone(from).orElseGet(() -> {
			Customer c = Customer.builder().whatsappPhone(from).build();
			return customerRepository.save(c);
		});

		String lowerText = text.toLowerCase().trim();

		// Check if customer is starting with a shop link
		if (lowerText.startsWith("shop:")) {
			String[] lines = text.trim().split("\n", 2);
			String slug = lines[0].substring(5).trim().toLowerCase();
			handleShopStart(from, customer, slug, messageId);

			// If there's a second line (product order), process it after greeting
			if (lines.length > 1 && !lines[1].isBlank()) {
				String followUp = lines[1].trim();
				// Small delay to let the greeting send first
				new Thread(() -> {
					try {
						Thread.sleep(2000);
					} catch (Exception e) {
					}
					handle(from, followUp, null);
				}).start();
			}
			return;
		}

		// Find active conversation
		Conversation conversation = conversationRepository
				.findTopByCustomerIdAndStatusOrderByLastMessageAtDesc(customer.getId(), "active").orElse(null);

		if (conversation == null) {
			sender.sendText(from, "Hey! 👋 To get started, visit a store link shared by a shop owner.");
			return;
		}

		// Save customer message
		saveMessage(conversation, "customer", text, messageId);

		// Get merchant
		Merchant merchant = merchantRepository.findById(conversation.getMerchant().getId()).orElse(null);
		if (merchant == null) {
			sender.sendText(from, "Sorry, something went wrong. Please try again.");
			return;
		}

		// Generate AI response
		String aiResponse = aiService.generateResponse(merchant, customer, conversation, text);

		// Save and send
		saveMessage(conversation, "bot", aiResponse, null);
		conversation.setLastMessageAt(OffsetDateTime.now());
		conversationRepository.save(conversation);

		sender.sendText(from, aiResponse);
	}

	private void handleMerchantAction(String from, String text) {
		try {
			// Payment confirmation
			if (text.startsWith("payconfirm_") || text.startsWith("payreject_")) {
				handlePaymentConfirmation(from, text);
				return;
			}

			String[] parts = text.split("_", 2);
			String type = parts[0];
			UUID orderId = UUID.fromString(parts[1]);

			if ("accept".equals(type)) {
				orderService.updateStatus(orderId, "accepted", null);
				sender.sendText(from, "✅ Order accepted! The customer has been notified.");
			} else if ("reject".equals(type)) {
				orderService.updateStatus(orderId, "rejected", "Shop owner declined the order");
				sender.sendText(from, "❌ Order rejected. The customer has been notified.");
			}
		} catch (Exception e) {
			log.error("Error handling merchant action", e);
			sender.sendText(from, "Sorry, couldn't process that action. Please try again.");
		}
	}

	private void handlePaymentConfirmation(String from, String action) {
		try {
			String[] parts = action.split("_", 2);
			String type = parts[0];
			UUID orderId = UUID.fromString(parts[1]);

			Order order = orderRepository.findById(orderId).orElse(null);
			if (order == null)
				return;

			if ("payconfirm".equals(type)) {
				order.setPaymentStatus("paid");
				orderRepository.save(order);
				sender.sendText(from, "✅ Payment confirmed for " + order.getOrderNumber() + "!");
				// Notify customer
				String customerPhone = order.getCustomer().getWhatsappPhone();
				if (!customerPhone.startsWith("91"))
					customerPhone = "91" + customerPhone;
				sender.sendText(customerPhone,
						"✅ Payment confirmed for *" + order.getOrderNumber() + "*! Your order is being prepared! 🎉");
			} else {
				sender.sendText(from, "Noted. Customer has been asked to retry payment.");
				String customerPhone = order.getCustomer().getWhatsappPhone();
				if (!customerPhone.startsWith("91"))
					customerPhone = "91" + customerPhone;
				sender.sendText(customerPhone, "⚠️ Payment for *" + order.getOrderNumber()
						+ "* hasn't been received yet. Please retry or share a payment screenshot.");
			}
		} catch (Exception e) {
			log.error("Error handling payment confirmation", e);
		}
	}

	private void handleShopStart(String to, Customer customer, String slug, String messageId) {
		var merchantOpt = merchantRepository.findBySlug(slug);
		if (merchantOpt.isEmpty()) {
			sender.sendText(to, "Sorry, couldn't find that store. Please check the link and try again.");
			return;
		}

		Merchant merchant = merchantOpt.get();

		customerMerchantRepository.findByCustomerIdAndMerchantId(customer.getId(), merchant.getId()).orElseGet(() -> {
			CustomerMerchant cm = CustomerMerchant.builder().customer(customer).merchant(merchant).build();
			return customerMerchantRepository.save(cm);
		});

		// Close existing conversations
		conversationRepository.findByCustomerIdAndMerchantIdAndStatus(customer.getId(), merchant.getId(), "active")
				.ifPresent(conv -> {
					conv.setStatus("closed");
					conversationRepository.save(conv);
				});

		Conversation conversation = Conversation.builder().customer(customer).merchant(merchant)
				.lastMessageAt(OffsetDateTime.now()).build();
		conversation = conversationRepository.save(conversation);

		saveMessage(conversation, "customer", "shop:" + slug, messageId);

		String greeting = aiService.generateResponse(merchant, customer, conversation,
				"Customer just opened the store. Greet them warmly and show the product catalog.");

		saveMessage(conversation, "bot", greeting, null);
		sender.sendText(to, greeting);
	}

	private void saveMessage(Conversation conversation, String senderType, String content, String waMessageId) {
		Message message = Message.builder().conversation(conversation).senderType(senderType).content(content)
				.waMessageId(waMessageId).build();
		messageRepository.save(message);
	}
}