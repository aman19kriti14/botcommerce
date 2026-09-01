package com.botcommerce.controller;

import com.botcommerce.dto.product.ProductDtos.*;
import com.botcommerce.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // ===== Categories =====

    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> createCategory(
            Authentication auth,
            @Valid @RequestBody CategoryRequest request) {
        UUID merchantId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(productService.createCategory(merchantId, request));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getCategories(Authentication auth) {
        UUID merchantId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(productService.getCategories(merchantId));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(Authentication auth, @PathVariable UUID id) {
        UUID merchantId = (UUID) auth.getPrincipal();
        productService.deleteCategory(merchantId, id);
        return ResponseEntity.noContent().build();
    }

    // ===== Products =====

    @PostMapping("/products")
    public ResponseEntity<ProductResponse> createProduct(
            Authentication auth,
            @Valid @RequestBody CreateProductRequest request) {
        UUID merchantId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(productService.createProduct(merchantId, request));
    }

    @GetMapping("/products")
    public ResponseEntity<List<ProductResponse>> getProducts(Authentication auth) {
        UUID merchantId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(productService.getProducts(merchantId));
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<ProductResponse> getProduct(Authentication auth, @PathVariable UUID id) {
        UUID merchantId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(productService.getProduct(merchantId, id));
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            Authentication auth,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request) {
        UUID merchantId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(productService.updateProduct(merchantId, id, request));
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(Authentication auth, @PathVariable UUID id) {
        UUID merchantId = (UUID) auth.getPrincipal();
        productService.deleteProduct(merchantId, id);
        return ResponseEntity.noContent().build();
    }
}
