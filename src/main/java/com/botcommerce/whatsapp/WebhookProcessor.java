package com.botcommerce.whatsapp;

import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookProcessor {

	private final ChatHandler chatHandler;

	@Async
	@SuppressWarnings("unchecked")
	public void process(Map<String, Object> payload) {
		try {
			List<Map<String, Object>> entries = (List<Map<String, Object>>) payload.get("entry");
			if (entries == null)
				return;

			for (Map<String, Object> entry : entries) {
				List<Map<String, Object>> changes = (List<Map<String, Object>>) entry.get("changes");
				if (changes == null)
					continue;

				for (Map<String, Object> change : changes) {
					Map<String, Object> value = (Map<String, Object>) change.get("value");
					if (value == null)
						continue;

					List<Map<String, Object>> messages = (List<Map<String, Object>>) value.get("messages");
					if (messages == null)
						continue;

					for (Map<String, Object> message : messages) {
						String from = (String) message.get("from");
						String type = (String) message.get("type");
						String messageId = (String) message.get("id");

						String text = null;
						if ("text".equals(type)) {
							Map<String, Object> textObj = (Map<String, Object>) message.get("text");
							text = (String) textObj.get("body");
						} else if ("interactive".equals(type)) {
							Map<String, Object> interactive = (Map<String, Object>) message.get("interactive");
							String interactiveType = (String) interactive.get("type");
							if ("button_reply".equals(interactiveType)) {
								Map<String, Object> reply = (Map<String, Object>) interactive.get("button_reply");
								text = (String) reply.get("id");
							} else if ("list_reply".equals(interactiveType)) {
								Map<String, Object> reply = (Map<String, Object>) interactive.get("list_reply");
								text = (String) reply.get("id");
							}
						}

						if (text != null && from != null) {
							log.info("Message from {}: {}", from, text);
							chatHandler.handle(from, text, messageId);
						}
					}
				}
			}
		} catch (Exception e) {
			log.error("Error processing webhook", e);
		}
	}
}