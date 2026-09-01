package com.botcommerce.service;

import com.botcommerce.dto.product.ProductDtos.*;
import com.botcommerce.model.Merchant;
import com.botcommerce.model.Product;
import com.botcommerce.model.ProductCategory;
import com.botcommerce.repository.MerchantRepository;
import com.botcommerce.repository.ProductCategoryRepository;
import com.botcommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final MerchantRepository merchantRepository;

    // ===== Categories =====

    @Transactional
    public CategoryResponse createCategory(UUID merchantId, CategoryRequest request) {
        Merchant merchant = getMerchant(merchantId);

        ProductCategory category = ProductCategory.builder()
                .merchant(merchant)
                .name(request.getName())
                .sortOrder(request.getSortOrder())
                .build();

        category = categoryRepository.save(category);
        return toCategoryResponse(category);
    }

    public List<CategoryResponse> getCategories(UUID merchantId) {
        return categoryRepository.findByMerchantIdOrderBySortOrder(merchantId)
                .stream()
                .map(this::toCategoryResponse)
                .toList();
    }

    @Transactional
    public void deleteCategory(UUID merchantId, UUID categoryId) {
        ProductCategory category = categoryRepository.findByIdAndMerchantId(categoryId, merchantId)
                .orElseThrow(() -> new RuntimeException("Category not found"));
        categoryRepository.delete(category);
    }

    // ===== Products =====

    @Transactional
    public ProductResponse createProduct(UUID merchantId, CreateProductRequest request) {
        Merchant merchant = getMerchant(merchantId);

        ProductCategory category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findByIdAndMerchantId(
                    UUID.fromString(request.getCategoryId()), merchantId)
                    .orElseThrow(() -> new RuntimeException("Category not found"));
        }

        Product product = Product.builder()
                .merchant(merchant)
                .category(category)
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .compareAtPrice(request.getCompareAtPrice())
                .imageUrl(request.getImageUrl())
                .isAvailable(request.getIsAvailable())
                .sortOrder(request.getSortOrder())
                .tags(request.getTags())
                .build();

        product = productRepository.save(product);
        return toProductResponse(product);
    }

    public List<ProductResponse> getProducts(UUID merchantId) {
        return productRepository.findByMerchantId(merchantId)
                .stream()
                .map(this::toProductResponse)
                .toList();
    }

    public ProductResponse getProduct(UUID merchantId, UUID productId) {
        Product product = productRepository.findByIdAndMerchantId(productId, merchantId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        return toProductResponse(product);
    }

    @Transactional
    public ProductResponse updateProduct(UUID merchantId, UUID productId, UpdateProductRequest request) {
        Product product = productRepository.findByIdAndMerchantId(productId, merchantId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (request.getName() != null) product.setName(request.getName());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getPrice() != null) product.setPrice(request.getPrice());
        if (request.getCompareAtPrice() != null) product.setCompareAtPrice(request.getCompareAtPrice());
        if (request.getImageUrl() != null) product.setImageUrl(request.getImageUrl());
        if (request.getIsAvailable() != null) product.setIsAvailable(request.getIsAvailable());
        if (request.getSortOrder() != null) product.setSortOrder(request.getSortOrder());
        if (request.getTags() != null) product.setTags(request.getTags());

        if (request.getCategoryId() != null) {
            ProductCategory category = categoryRepository.findByIdAndMerchantId(
                    UUID.fromString(request.getCategoryId()), merchantId)
                    .orElseThrow(() -> new RuntimeException("Category not found"));
            product.setCategory(category);
        }

        product = productRepository.save(product);
        return toProductResponse(product);
    }

    @Transactional
    public void deleteProduct(UUID merchantId, UUID productId) {
        Product product = productRepository.findByIdAndMerchantId(productId, merchantId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        productRepository.delete(product);
    }

    // ===== Helpers =====

    private Merchant getMerchant(UUID merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new RuntimeException("Merchant not found"));
    }

    private CategoryResponse toCategoryResponse(ProductCategory category) {
        CategoryResponse res = new CategoryResponse();
        res.setId(category.getId().toString());
        res.setName(category.getName());
        res.setSortOrder(category.getSortOrder());
        res.setActive(category.getIsActive());
        return res;
    }

    private ProductResponse toProductResponse(Product product) {
        ProductResponse res = new ProductResponse();
        res.setId(product.getId().toString());
        res.setName(product.getName());
        res.setDescription(product.getDescription());
        res.setPrice(product.getPrice());
        res.setCompareAtPrice(product.getCompareAtPrice());
        res.setImageUrl(product.getImageUrl());
        res.setAvailable(product.getIsAvailable());
        res.setSortOrder(product.getSortOrder());
        res.setTags(product.getTags());
        res.setCreatedAt(product.getCreatedAt().toString());

        if (product.getCategory() != null) {
            res.setCategory(toCategoryResponse(product.getCategory()));
        }

        return res;
    }
}
