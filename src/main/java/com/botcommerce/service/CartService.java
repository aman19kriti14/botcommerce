package com.botcommerce.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.botcommerce.model.Cart;
import com.botcommerce.model.CartItem;
import com.botcommerce.model.Customer;
import com.botcommerce.model.Merchant;
import com.botcommerce.model.Product;
import com.botcommerce.repository.CartItemRepository;
import com.botcommerce.repository.CartRepository;
import com.botcommerce.repository.ProductRepository;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CartService {

	private final CartRepository cartRepository;
	private final CartItemRepository cartItemRepository;
	private final ProductRepository productRepository;

	@Transactional
	public Cart getOrCreateCart(Customer customer, Merchant merchant) {
		return cartRepository.findByCustomerIdAndMerchantIdAndStatus(customer.getId(), merchant.getId(), "active")
				.orElseGet(() -> {
					Cart cart = Cart.builder().customer(customer).merchant(merchant).build();
					return cartRepository.save(cart);
				});
	}

	@Transactional
	public CartSummary addItem(Cart cart, UUID productId, int quantity) {
		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new RuntimeException("Product not found"));

		// Check if item already in cart
		Optional<CartItem> existing = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId);

		if (existing.isPresent()) {
			CartItem item = existing.get();
			item.setQuantity(item.getQuantity() + quantity);
			cartItemRepository.save(item);
		} else {
			CartItem item = CartItem.builder().cart(cart).product(product).quantity(quantity)
					.unitPrice(product.getPrice()).build();
			cartItemRepository.save(item);
		}

		return getCartSummary(cart);
	}

	@Transactional
	public CartSummary removeItem(Cart cart, UUID productId) {
		cartItemRepository.deleteByCartIdAndProductId(cart.getId(), productId);
		return getCartSummary(cart);
	}

	@Transactional
	public CartSummary clearCart(Cart cart) {
		cartItemRepository.deleteByCartId(cart.getId());
		return getCartSummary(cart);
	}

	public CartSummary getCartSummary(Cart cart) {
		List<CartItem> items = cartItemRepository.findByCartIdWithProduct(cart.getId());

		CartSummary summary = new CartSummary();
		summary.setCartId(cart.getId());
		summary.setItems(items.stream().map(item -> {
			CartSummary.ItemLine line = new CartSummary.ItemLine();
			line.setProductId(item.getProduct().getId());
			line.setProductName(item.getProduct().getName());
			line.setQuantity(item.getQuantity());
			line.setUnitPrice(item.getUnitPrice());
			line.setTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
			return line;
		}).toList());

		summary.setSubtotal(summary.getItems().stream().map(CartSummary.ItemLine::getTotal).reduce(BigDecimal.ZERO,
				BigDecimal::add));

		summary.setItemCount(items.size());
		return summary;
	}

	@Transactional
	public void markConverted(Cart cart) {
		cart.setStatus("converted");
		cartRepository.save(cart);
	}

	@Data
	public static class CartSummary {
		private UUID cartId;
		private List<ItemLine> items;
		private BigDecimal subtotal;
		private int itemCount;

		@Data
		public static class ItemLine {
			private UUID productId;
			private String productName;
			private int quantity;
			private BigDecimal unitPrice;
			private BigDecimal total;
		}

		public String toDisplayString() {
			if (items.isEmpty())
				return "Your cart is empty.";

			StringBuilder sb = new StringBuilder("🛒 *Your Cart:*\n\n");
			for (int i = 0; i < items.size(); i++) {
				ItemLine item = items.get(i);
				sb.append(String.format("%d. %s × %d — ₹%s\n", i + 1, item.productName, item.quantity,
						item.total.stripTrailingZeros().toPlainString()));
			}
			sb.append(String.format("\n*Total: ₹%s*", subtotal.stripTrailingZeros().toPlainString()));
			return sb.toString();
		}
	}
}