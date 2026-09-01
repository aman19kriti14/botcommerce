package com.botcommerce.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.botcommerce.model.Cart;
import com.botcommerce.model.Customer;
import com.botcommerce.model.Merchant;
import com.botcommerce.model.Order;
import com.botcommerce.model.OrderItem;
import com.botcommerce.model.Product;
import com.botcommerce.model.WalletTransaction;
import com.botcommerce.repository.CustomerMerchantRepository;
import com.botcommerce.repository.MerchantRepository;
import com.botcommerce.repository.OrderItemRepository;
import com.botcommerce.repository.OrderRepository;
import com.botcommerce.repository.WalletTransactionRepository;
import com.botcommerce.whatsapp.WhatsAppSender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final CartService cartService;
	private final MerchantRepository merchantRepository;
	private final CustomerMerchantRepository customerMerchantRepository;
	private final WalletTransactionRepository walletTransactionRepository;
	private final WhatsAppSender whatsAppSender;

	@Transactional
	public Order createOrder(Cart cart, Customer customer, Merchant merchant, String deliveryAddress,
			String deliveryType, String customerNote) {

		CartService.CartSummary summary = cartService.getCartSummary(cart);

		if (summary.getItems().isEmpty()) {
			throw new RuntimeException("Cart is empty");
		}

		BigDecimal subtotal = summary.getSubtotal();
		BigDecimal deliveryFee = "delivery".equals(deliveryType) ? merchant.getDeliveryFee() : BigDecimal.ZERO;
		BigDecimal total = subtotal.add(deliveryFee);

		// Generate order number
		String orderNumber = "ORD-" + (ThreadLocalRandom.current().nextInt(10000, 99999));

		Order order = Order.builder().orderNumber(orderNumber).merchant(merchant).customer(customer).status("placed")
				.subtotal(subtotal).deliveryFee(deliveryFee).total(total).deliveryAddress(deliveryAddress)
				.deliveryType(deliveryType).customerNote(customerNote).placedAt(OffsetDateTime.now()).build();

		order = orderRepository.save(order);

		// Create order items from cart
		for (CartService.CartSummary.ItemLine item : summary.getItems()) {
			OrderItem orderItem = OrderItem.builder().order(order)
					.product(Product.builder().id(item.getProductId()).build()).productName(item.getProductName())
					.quantity(item.getQuantity()).unitPrice(item.getUnitPrice()).totalPrice(item.getTotal()).build();
			orderItemRepository.save(orderItem);
		}

		// Mark cart as converted
		cartService.markConverted(cart);

		// Update merchant order count
		merchant.setTotalOrders(merchant.getTotalOrders() + 1);
		merchantRepository.save(merchant);

		// Update customer-merchant stats
		customerMerchantRepository.findByCustomerIdAndMerchantId(customer.getId(), merchant.getId()).ifPresent(cm -> {
			cm.setTotalOrders(cm.getTotalOrders() + 1);
			cm.setTotalSpent(cm.getTotalSpent().add(total));
			cm.setLastOrderAt(OffsetDateTime.now());
			customerMerchantRepository.save(cm);
		});

		// Notify merchant on WhatsApp
		notifyMerchant(order, merchant, summary);

		return order;
	}

	@Transactional
	public Order updateStatus(UUID orderId, String newStatus, String reason) {
		Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

		String oldStatus = order.getStatus();
		order.setStatus(newStatus);

		switch (newStatus) {
		case "accepted" -> {
			order.setAcceptedAt(OffsetDateTime.now());
			chargePlatformFee(order);
		}
		case "rejected" -> {
			order.setRejectionReason(reason);
			order.setCancelledAt(OffsetDateTime.now());
		}
		case "delivered" -> order.setDeliveredAt(OffsetDateTime.now());
		case "cancelled" -> order.setCancelledAt(OffsetDateTime.now());
		}

		order = orderRepository.save(order);

		// Notify customer about status change
		notifyCustomer(order, newStatus);

		return order;
	}

	private void chargePlatformFee(Order order) {
		Merchant merchant = order.getMerchant();

		// Check if free orders remaining
		if (merchant.getFreeOrdersRemaining() > 0) {
			merchant.setFreeOrdersRemaining(merchant.getFreeOrdersRemaining() - 1);
			merchantRepository.save(merchant);
			return;
		}

		// Deduct from wallet
		BigDecimal fee = order.getPlatformFee();
		merchant.setWalletBalance(merchant.getWalletBalance().subtract(fee));
		merchantRepository.save(merchant);

		WalletTransaction txn = WalletTransaction.builder().merchant(merchant).type("debit").amount(fee)
				.description("Platform fee for order " + order.getOrderNumber()).order(order)
				.balanceAfter(merchant.getWalletBalance()).build();
		walletTransactionRepository.save(txn);
	}

	private void notifyMerchant(Order order, Merchant merchant, CartService.CartSummary summary) {
		if (merchant.getWhatsappPhone() == null)
			return;

		String merchantPhone = merchant.getWhatsappPhone();
		if (!merchantPhone.startsWith("91")) {
			merchantPhone = "91" + merchantPhone;
		}

		StringBuilder msg = new StringBuilder();
		msg.append("🔔 *New Order!* ").append(order.getOrderNumber()).append("\n\n");

		for (CartService.CartSummary.ItemLine item : summary.getItems()) {
			msg.append("• ").append(item.getProductName()).append(" × ").append(item.getQuantity()).append(" — ₹")
					.append(item.getTotal().stripTrailingZeros().toPlainString()).append("\n");
		}

		msg.append("\n*Total: ₹").append(order.getTotal().stripTrailingZeros().toPlainString()).append("*");
		msg.append("\n📍 ").append(order.getDeliveryAddress());

		if (order.getCustomerNote() != null) {
			msg.append("\n📝 ").append(order.getCustomerNote());
		}

		// Send with accept/reject buttons
		whatsAppSender.sendButtons(merchantPhone, msg.toString(),
				List.of(Map.of("id", "accept_" + order.getId(), "title", "✅ Accept"),
						Map.of("id", "reject_" + order.getId(), "title", "❌ Reject")));
	}

	private void notifyCustomer(Order order, String status) {
		String phone = order.getCustomer().getWhatsappPhone();
		String msg = switch (status) {
		case "accepted" -> "✅ *Order " + order.getOrderNumber()
				+ " accepted!*\nYour order is being prepared. We'll update you when it's ready!";
		case "rejected" -> "❌ *Order " + order.getOrderNumber() + " couldn't be processed.*\nReason: "
				+ order.getRejectionReason() + "\nSorry for the inconvenience!";
		case "preparing" -> "👨‍🍳 Your order " + order.getOrderNumber() + " is being prepared!";
		case "ready" -> "✅ Your order " + order.getOrderNumber() + " is ready for pickup/delivery!";
		case "out_for_delivery" -> "🛵 Your order " + order.getOrderNumber() + " is out for delivery!";
		case "delivered" -> "🎉 Your order " + order.getOrderNumber() + " has been delivered! Hope you enjoy it! 😊";
		default -> null;
		};

		if (msg != null) {
			whatsAppSender.sendText(phone, msg);
		}
	}
}