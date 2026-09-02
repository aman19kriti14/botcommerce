package com.botcommerce.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.botcommerce.model.Order;
import com.botcommerce.model.OrderItem;
import com.botcommerce.repository.OrderItemRepository;
import com.botcommerce.repository.OrderRepository;
import com.botcommerce.service.OrderService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final OrderService orderService;

	@GetMapping
	public ResponseEntity<List<Map<String, Object>>> getOrders(Authentication auth) {
		UUID merchantId = (UUID) auth.getPrincipal();
		List<Order> orders = orderRepository.findByMerchantId(merchantId);
		List<Map<String, Object>> result = orders.stream().map(this::toMap).toList();
		return ResponseEntity.ok(result);
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<Map<String, String>> updateStatus(Authentication auth, @PathVariable UUID id,
			@RequestBody Map<String, String> body) {
		String status = body.get("status");
		String reason = body.get("reason");
		orderService.updateStatus(id, status, reason);
		return ResponseEntity.ok(Map.of("status", status));
	}

	private Map<String, Object> toMap(Order o) {
		List<OrderItem> items = orderItemRepository.findByOrderId(o.getId());

		Map<String, Object> map = new LinkedHashMap<>();
		map.put("id", o.getId().toString());
		map.put("orderNumber", o.getOrderNumber());
		map.put("status", o.getStatus());
		map.put("subtotal", o.getSubtotal());
		map.put("deliveryFee", o.getDeliveryFee());
		map.put("total", o.getTotal());
		map.put("paymentMethod", o.getPaymentMethod());
		map.put("paymentStatus", o.getPaymentStatus());
		map.put("deliveryAddress", o.getDeliveryAddress());
		map.put("deliveryType", o.getDeliveryType());
		map.put("customerNote", o.getCustomerNote());
		map.put("customerName", o.getCustomer().getName());
		map.put("customerPhone", o.getCustomer().getWhatsappPhone());
		map.put("placedAt", o.getPlacedAt() != null ? o.getPlacedAt().toString() : null);
		map.put("acceptedAt", o.getAcceptedAt() != null ? o.getAcceptedAt().toString() : null);
		map.put("deliveredAt", o.getDeliveredAt() != null ? o.getDeliveredAt().toString() : null);
		map.put("items", items.stream().map(i -> Map.of("productName", i.getProductName(), "quantity", i.getQuantity(),
				"unitPrice", i.getUnitPrice(), "totalPrice", i.getTotalPrice())).toList());
		return map;
	}
}