package com.botcommerce.repository;

import com.botcommerce.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

	@Query("SELECT o FROM Order o JOIN FETCH o.customer WHERE o.merchant.id = :merchantId ORDER BY o.createdAt DESC")
	List<Order> findByMerchantId(UUID merchantId);

	@Query("SELECT o FROM Order o JOIN FETCH o.customer WHERE o.merchant.id = :merchantId AND o.status = :status ORDER BY o.createdAt DESC")
	List<Order> findByMerchantIdAndStatus(UUID merchantId, String status);
}