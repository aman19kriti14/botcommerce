package com.botcommerce.repository;

import com.botcommerce.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    @Query("SELECT p FROM Product p JOIN FETCH p.merchant LEFT JOIN FETCH p.category WHERE p.merchant.id = :merchantId ORDER BY p.sortOrder, p.name")
    List<Product> findByMerchantId(UUID merchantId);

    @Query("SELECT p FROM Product p JOIN FETCH p.merchant LEFT JOIN FETCH p.category WHERE p.merchant.id = :merchantId AND p.category.id = :categoryId ORDER BY p.sortOrder")
    List<Product> findByMerchantIdAndCategoryId(UUID merchantId, UUID categoryId);

    @Query("SELECT p FROM Product p JOIN FETCH p.merchant LEFT JOIN FETCH p.category WHERE p.merchant.id = :merchantId AND p.isAvailable = true ORDER BY p.sortOrder, p.name")
    List<Product> findAvailableByMerchantId(UUID merchantId);

    Optional<Product> findByIdAndMerchantId(UUID id, UUID merchantId);
}
