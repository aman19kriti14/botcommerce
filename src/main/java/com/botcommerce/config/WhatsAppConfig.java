package com.botcommerce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WhatsAppConfig {

	@Value("${whatsapp.api-url}")
	private String apiUrl;

	@Value("${whatsapp.phone-number-id}")
	private String phoneNumberId;

	@Value("${whatsapp.access-token}")
	private String accessToken;

	@Value("${whatsapp.verify-token}")
	private String verifyToken;

	@Bean
	public WebClient whatsappWebClient() {
		return WebClient.builder().baseUrl(apiUrl + "/" + phoneNumberId)
				.defaultHeader("Authorization", "Bearer " + accessToken)
				.defaultHeader("Content-Type", "application/json").build();
	}

	@Bean
	public String whatsappVerifyToken() {
		return verifyToken;
	}
}