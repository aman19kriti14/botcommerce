package com.botcommerce.repository;

import com.botcommerce.model.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartRepository extends JpaRepository<Cart, UUID> {

	Optional<Cart> findByCustomerIdAndMerchantIdAndStatus(UUID customerId, UUID merchantId, String status);
}