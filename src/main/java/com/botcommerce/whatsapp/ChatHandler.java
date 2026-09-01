package com.botcommerce.whatsapp;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.botcommerce.ai.AiService;
import com.botcommerce.model.Conversation;
import com.botcommerce.model.Customer;
import com.botcommerce.model.CustomerMerchant;
import com.botcommerce.model.Merchant;
import com.botcommerce.model.Message;
import com.botcommerce.repository.ConversationRepository;
import com.botcommerce.repository.CustomerMerchantRepository;
import com.botcommerce.repository.CustomerRepository;
import com.botcommerce.repository.MerchantRepository;
import com.botcommerce.repository.MessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatHandler {

	private final WhatsAppSender sender;
	private final AiService aiService;
	private final CustomerRepository customerRepository;
	private final MerchantRepository merchantRepository;
	private final ConversationRepository conversationRepository;
	private final MessageRepository messageRepository;
	private final CustomerMerchantRepository customerMerchantRepository;

	@Transactional
	public void handle(String from, String text, String messageId) {
		// Get or create customer
		Customer customer = customerRepository.findByWhatsappPhone(from).orElseGet(() -> {
			Customer c = Customer.builder().whatsappPhone(from).build();
			return customerRepository.save(c);
		});

		String lowerText = text.toLowerCase().trim();

		// Check if customer is starting with a shop link
		if (lowerText.startsWith("shop:")) {
			String slug = lowerText.substring(5).trim();
			handleShopStart(from, customer, slug, messageId);
			return;
		}

		// Find active conversation for this customer
		Conversation conversation = findActiveConversation(customer);

		if (conversation == null) {
			sender.sendText(from,
					"Hey! 👋 To get started, visit a store link shared by a shop owner. You'll be able to browse and order from there!");
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

		// Save bot response
		saveMessage(conversation, "bot", aiResponse, null);

		// Update conversation timestamp
		conversation.setLastMessageAt(OffsetDateTime.now());
		conversationRepository.save(conversation);

		// Send response
		sender.sendText(from, aiResponse);
	}

	private void handleShopStart(String to, Customer customer, String slug, String messageId) {
		var merchantOpt = merchantRepository.findBySlug(slug);
		if (merchantOpt.isEmpty()) {
			sender.sendText(to, "Sorry, couldn't find that store. Please check the link and try again.");
			return;
		}

		Merchant merchant = merchantOpt.get();

		// Create or get customer-merchant link
		customerMerchantRepository.findByCustomerIdAndMerchantId(customer.getId(), merchant.getId()).orElseGet(() -> {
			CustomerMerchant cm = CustomerMerchant.builder().customer(customer).merchant(merchant).build();
			return customerMerchantRepository.save(cm);
		});

		// Close any existing conversation with this merchant
		conversationRepository.findByCustomerIdAndMerchantIdAndStatus(customer.getId(), merchant.getId(), "active")
				.ifPresent(conv -> {
					conv.setStatus("closed");
					conversationRepository.save(conv);
				});

		// Create new conversation
		Conversation conversation = Conversation.builder().customer(customer).merchant(merchant)
				.lastMessageAt(OffsetDateTime.now()).build();
		conversation = conversationRepository.save(conversation);

		// Save the incoming message
		saveMessage(conversation, "customer", "shop:" + slug, messageId);

		// Generate AI greeting
		String greeting = aiService.generateResponse(merchant, customer, conversation,
				"Customer just opened the store. Greet them and show the menu highlights.");

		saveMessage(conversation, "bot", greeting, null);

		sender.sendText(to, greeting);
	}

	private Conversation findActiveConversation(Customer customer) {
		// Find any active conversation for this customer
		// In a real app, you'd scope this better
		return conversationRepository.findTopByCustomerIdAndStatusOrderByLastMessageAtDesc(customer.getId(), "active")
				.orElse(null);
	}

	private void saveMessage(Conversation conversation, String senderType, String content, String waMessageId) {
		Message message = Message.builder().conversation(conversation).senderType(senderType).content(content)
				.waMessageId(waMessageId).build();
		messageRepository.save(message);
	}
}