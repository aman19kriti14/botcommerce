package com.botcommerce.repository;

import com.botcommerce.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

	Optional<Customer> findByWhatsappPhone(String whatsappPhone);
}