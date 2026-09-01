package com.botcommerce.service;

import com.botcommerce.dto.auth.AuthDtos.*;
import com.botcommerce.model.Merchant;
import com.botcommerce.repository.MerchantRepository;
import com.botcommerce.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        // Check if phone already exists
        if (merchantRepository.existsByPhone(request.getPhone())) {
            throw new RuntimeException("Phone number already registered");
        }

        // Generate unique slug from business name
        String slug = generateSlug(request.getBusinessName());

        Merchant merchant = Merchant.builder()
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .businessName(request.getBusinessName())
                .slug(slug)
                .category(request.getCategory())
                .city(request.getCity())
                .upiId(request.getUpiId())
                .whatsappPhone(request.getPhone())
                .build();

        merchant = merchantRepository.save(merchant);

        return buildAuthResponse(merchant);
    }

    public AuthResponse login(LoginRequest request) {
        Merchant merchant = merchantRepository.findByPhone(request.getPhone())
                .orElseThrow(() -> new RuntimeException("Invalid phone or password"));

        if (!passwordEncoder.matches(request.getPassword(), merchant.getPasswordHash())) {
            throw new RuntimeException("Invalid phone or password");
        }

        if (!merchant.getIsActive()) {
            throw new RuntimeException("Account is deactivated");
        }

        return buildAuthResponse(merchant);
    }

    public AuthResponse getMe(java.util.UUID merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new RuntimeException("Merchant not found"));
        return buildAuthResponse(merchant);
    }

    private AuthResponse buildAuthResponse(Merchant merchant) {
        AuthResponse response = new AuthResponse();
        response.setToken(jwtUtil.generateToken(merchant.getId(), merchant.getPhone()));

        AuthResponse.MerchantInfo info = new AuthResponse.MerchantInfo();
        info.setId(merchant.getId().toString());
        info.setPhone(merchant.getPhone());
        info.setBusinessName(merchant.getBusinessName());
        info.setSlug(merchant.getSlug());
        info.setCategory(merchant.getCategory());
        info.setCity(merchant.getCity());
        info.setLogoUrl(merchant.getLogoUrl());
        info.setActive(merchant.getIsActive());

        response.setMerchant(info);
        return response;
    }

    private String generateSlug(String businessName) {
        // Normalize and convert to slug
        String slug = Normalizer.normalize(businessName, Normalizer.Form.NFD);
        slug = Pattern.compile("[^\\p{ASCII}]").matcher(slug).replaceAll("");
        slug = slug.toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");

        // Ensure uniqueness
        String baseSlug = slug;
        int counter = 1;
        while (merchantRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + counter;
            counter++;
        }

        return slug;
    }
}
