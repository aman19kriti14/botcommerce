package com.botcommerce.dto.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

public class ProductDtos {

    // ===== Category =====

    @Data
    public static class CategoryRequest {
        @NotBlank(message = "Category name is required")
        private String name;
        private Integer sortOrder = 0;
    }

    @Data
    public static class CategoryResponse {
        private String id;
        private String name;
        private Integer sortOrder;
        private boolean active;
    }

    // ===== Product =====

    @Data
    public static class CreateProductRequest {
        @NotBlank(message = "Product name is required")
        private String name;

        private String description;

        @NotNull(message = "Price is required")
        @Positive(message = "Price must be positive")
        private BigDecimal price;

        private BigDecimal compareAtPrice;
        private String categoryId;
        private String imageUrl;
        private Boolean isAvailable = true;
        private Integer sortOrder = 0;
        private String tags;
    }

    @Data
    public static class UpdateProductRequest {
        private String name;
        private String description;
        private BigDecimal price;
        private BigDecimal compareAtPrice;
        private String categoryId;
        private String imageUrl;
        private Boolean isAvailable;
        private Integer sortOrder;
        private String tags;
    }

    @Data
    public static class ProductResponse {
        private String id;
        private String name;
        private String description;
        private BigDecimal price;
        private BigDecimal compareAtPrice;
        private String imageUrl;
        private boolean available;
        private Integer sortOrder;
        private String tags;
        private CategoryResponse category;
        private String createdAt;
    }
}
