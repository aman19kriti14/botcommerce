package com.botcommerce.whatsapp;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppSender {

	private final WebClient whatsappWebClient;

	public void sendText(String to, String message) {
		Map<String, Object> body = Map.of("messaging_product", "whatsapp", "to", to, "type", "text", "text",
				Map.of("body", message));
		send(body);
	}

	public void sendButtons(String to, String bodyText, List<Map<String, String>> buttons) {
		List<Map<String, Object>> buttonList = buttons.stream().map(b -> Map.<String, Object>of("type", "reply",
				"reply", Map.of("id", b.get("id"), "title", b.get("title")))).toList();

		Map<String, Object> body = Map.of("messaging_product", "whatsapp", "to", to, "type", "interactive",
				"interactive",
				Map.of("type", "button", "body", Map.of("text", bodyText), "action", Map.of("buttons", buttonList)));
		send(body);
	}

	public void sendList(String to, String bodyText, String buttonTitle, List<Map<String, Object>> sections) {
		Map<String, Object> body = Map.of("messaging_product", "whatsapp", "to", to, "type", "interactive",
				"interactive", Map.of("type", "list", "body", Map.of("text", bodyText), "action",
						Map.of("button", buttonTitle, "sections", sections)));
		send(body);
	}

	private void send(Map<String, Object> body) {
		try {
			whatsappWebClient.post().uri("/messages").bodyValue(body).retrieve().bodyToMono(String.class)
					.doOnSuccess(res -> log.info("WA message sent: {}", res))
					.doOnError(err -> log.error("WA send failed: {}", err.getMessage())).subscribe();
		} catch (Exception e) {
			log.error("Failed to send WhatsApp message", e);
		}
	}
}