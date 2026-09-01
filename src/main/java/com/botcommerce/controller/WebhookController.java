package com.botcommerce.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.botcommerce.whatsapp.WebhookProcessor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

	@Qualifier("whatsappVerifyToken")
	private final String verifyToken;
	private final WebhookProcessor webhookProcessor;

	// WhatsApp verification (GET)
	@GetMapping("/whatsapp")
	public ResponseEntity<String> verify(@RequestParam("hub.mode") String mode,
			@RequestParam("hub.verify_token") String token, @RequestParam("hub.challenge") String challenge) {

		if ("subscribe".equals(mode) && verifyToken.equals(token)) {
			log.info("Webhook verified successfully");
			return ResponseEntity.ok(challenge);
		}

		log.warn("Webhook verification failed");
		return ResponseEntity.status(403).body("Forbidden");
	}

	// Incoming messages (POST)
	@PostMapping("/whatsapp")
	public ResponseEntity<String> handleMessage(@RequestBody Map<String, Object> payload) {
		log.info("Webhook received: {}", payload);
		webhookProcessor.process(payload);
		return ResponseEntity.ok("OK");
	}
}