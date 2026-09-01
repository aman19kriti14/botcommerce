package com.botcommerce.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "merchants")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 15)
    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(length = 50)
    private String category;

    private String description;

    @Column(length = 100)
    private String city;

    private String address;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "upi_id", length = 100)
    private String upiId;

    @Column(name = "whatsapp_phone", length = 15)
    private String whatsappPhone;

    @Column(name = "bot_tone", length = 20)
    @Builder.Default
    private String botTone = "friendly";

    @Column(name = "bot_language", length = 20)
    @Builder.Default
    private String botLanguage = "english";

    @Column(name = "bot_rules")
    private String botRules;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "operating_hours", columnDefinition = "jsonb")
    private Map<String, Object> operatingHours;

    @Column(name = "delivery_areas")
    private String deliveryAreas;

    @Column(name = "min_order_amount")
    @Builder.Default
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    @Column(name = "delivery_fee")
    @Builder.Default
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(name = "wallet_balance")
    @Builder.Default
    private BigDecimal walletBalance = BigDecimal.ZERO;

    @Column(name = "free_orders_remaining")
    @Builder.Default
    private Integer freeOrdersRemaining = 100;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_online")
    @Builder.Default
    private Boolean isOnline = true;

    @Column(name = "total_orders")
    @Builder.Default
    private Integer totalOrders = 0;

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
