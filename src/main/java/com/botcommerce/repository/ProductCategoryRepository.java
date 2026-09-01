package com.botcommerce.repository;

import com.botcommerce.model.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {

    List<ProductCategory> findByMerchantIdOrderBySortOrder(UUID merchantId);

    Optional<ProductCategory> findByIdAndMerchantId(UUID id, UUID merchantId);
}
