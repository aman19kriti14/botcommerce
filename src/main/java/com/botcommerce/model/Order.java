package com.botcommerce.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_number", nullable = false, unique = true, length = 20)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "pending_payment";

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(name = "delivery_fee")
    @Builder.Default
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal total;

    @Column(name = "payment_method", length = 20)
    @Builder.Default
    private String paymentMethod = "upi";

    @Column(name = "payment_status", length = 20)
    @Builder.Default
    private String paymentStatus = "pending";

    @Column(name = "delivery_address")
    private String deliveryAddress;

    @Column(name = "delivery_type", length = 20)
    @Builder.Default
    private String deliveryType = "delivery";

    @Column(name = "customer_note")
    private String customerNote;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "platform_fee")
    @Builder.Default
    private BigDecimal platformFee = new BigDecimal("5.00");

    @Column(name = "placed_at")
    private OffsetDateTime placedAt;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "created_at")
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
