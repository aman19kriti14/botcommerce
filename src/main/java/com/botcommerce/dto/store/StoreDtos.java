package com.botcommerce.dto.store;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

public class StoreDtos {

	@Data
	public static class StoreResponse {
		private String id;
		private String businessName;
		private String slug;
		private String category;
		private String description;
		private String city;
		private String logoUrl;
		private boolean online;
		private BigDecimal minOrderAmount;
		private BigDecimal deliveryFee;
		private String deliveryAreas;
		private Integer totalOrders;
		private List<StoreCategoryResponse> categories;
		private List<StoreProductResponse> products;
	}

	@Data
	public static class StoreCategoryResponse {
		private String id;
		private String name;
		private Integer sortOrder;
	}

	@Data
	public static class StoreProductResponse {
		private String id;
		private String name;
		private String description;
		private BigDecimal price;
		private BigDecimal compareAtPrice;
		private String imageUrl;
		private boolean available;
		private String tags;
		private String categoryId;
		private String categoryName;
	}
}