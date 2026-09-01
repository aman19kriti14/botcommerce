package com.botcommerce.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.botcommerce.model.CustomerMerchant;

@Repository
public interface CustomerMerchantRepository extends JpaRepository<CustomerMerchant, UUID> {

    Optional<CustomerMerchant> findByCustomerIdAndMerchantId(UUID customerId, UUID merchantId);
}