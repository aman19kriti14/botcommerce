package com.botcommerce.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

public class AuthDtos {

    @Data
    public static class SignupRequest {
        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid Indian mobile number")
        private String phone;

        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        private String password;

        @NotBlank(message = "Business name is required")
        private String businessName;

        private String category;
        private String city;
        private String upiId;
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "Phone is required")
        private String phone;

        @NotBlank(message = "Password is required")
        private String password;
    }

    @Data
    public static class AuthResponse {
        private String token;
        private MerchantInfo merchant;

        @Data
        public static class MerchantInfo {
            private String id;
            private String phone;
            private String businessName;
            private String slug;
            private String category;
            private String city;
            private String logoUrl;
            private boolean isActive;
        }
    }
}
