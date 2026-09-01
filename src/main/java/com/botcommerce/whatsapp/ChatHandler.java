package com.botcommerce.whatsapp;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.botcommerce.model.Customer;
import com.botcommerce.model.Merchant;
import com.botcommerce.model.Product;
import com.botcommerce.repository.CustomerRepository;
import com.botcommerce.repository.MerchantRepository;
import com.botcommerce.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatHandler {

	private final WhatsAppSender sender;
	private final CustomerRepository customerRepository;
	private final MerchantRepository merchantRepository;
	private final ProductRepository productRepository;

	@Transactional
	public void handle(String from, String text, String messageId) {
		// Get or create customer
		Customer customer = customerRepository.findByWhatsappPhone(from).orElseGet(() -> {
			Customer c = Customer.builder().whatsappPhone(from).build();
			return customerRepository.save(c);
		});

		String lowerText = text.toLowerCase().trim();

		// For now: basic command-based responses
		// Later: AI will handle this
		if (lowerText.startsWith("shop:")) {
			// Customer clicked a store link: "shop:priya-cakes"
			String slug = lowerText.substring(5).trim();
			handleShopGreeting(from, slug);
		} else if (lowerText.equals("menu") || lowerText.equals("catalog") || lowerText.equals("products")) {
			sender.sendText(from,
					"Please visit a store first by clicking a store link. You'll see the full catalog there!");
		} else {
			// Default response — will be replaced by AI
			sender.sendText(from,
					"Hey! 👋 Welcome to BotCommerce. To browse a store, click on a store link shared by the shop owner.");
		}
	}

	private void handleShopGreeting(String to, String slug) {
		var merchantOpt = merchantRepository.findBySlug(slug);
		if (merchantOpt.isEmpty()) {
			sender.sendText(to, "Sorry, couldn't find that store. Please check the link and try again.");
			return;
		}

		Merchant merchant = merchantOpt.get();
		List<Product> products = productRepository.findAvailableByMerchantId(merchant.getId());

		if (products.isEmpty()) {
			sender.sendText(to, "Welcome to " + merchant.getBusinessName()
					+ "! 🏪\n\nThe store is setting up their catalog. Please check back soon!");
			return;
		}

		// Build product list message
		StringBuilder msg = new StringBuilder();
		msg.append("Welcome to *").append(merchant.getBusinessName()).append("*! 🏪\n\n");
		msg.append("Here's what we have:\n\n");

		for (int i = 0; i < products.size(); i++) {
			Product p = products.get(i);
			msg.append(i + 1).append(". *").append(p.getName()).append("*");
			msg.append(" — ₹").append(p.getPrice().stripTrailingZeros().toPlainString());
			if (p.getDescription() != null) {
				msg.append("\n   ").append(p.getDescription());
			}
			msg.append("\n\n");
		}

		msg.append("To order, just type the product name or number! 🛒");

		sender.sendText(to, msg.toString());
	}
}