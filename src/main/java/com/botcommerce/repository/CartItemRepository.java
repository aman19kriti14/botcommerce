package com.botcommerce.repository;

import com.botcommerce.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

	Optional<CartItem> findByCartIdAndProductId(UUID cartId, UUID productId);

	@Query("SELECT ci FROM CartItem ci JOIN FETCH ci.product WHERE ci.cart.id = :cartId")
	List<CartItem> findByCartIdWithProduct(UUID cartId);

	@Modifying
	void deleteByCartIdAndProductId(UUID cartId, UUID productId);

	@Modifying
	void deleteByCartId(UUID cartId);
}