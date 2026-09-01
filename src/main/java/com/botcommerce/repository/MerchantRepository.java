package com.botcommerce.repository;

import com.botcommerce.model.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    Optional<Merchant> findByPhone(String phone);

    Optional<Merchant> findBySlug(String slug);

    boolean existsByPhone(String phone);

    boolean existsBySlug(String slug);
}
