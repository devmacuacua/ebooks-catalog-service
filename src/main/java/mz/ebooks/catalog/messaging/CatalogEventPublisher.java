package mz.ebooks.catalog.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mz.ebooks.catalog.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishBookPublished(Map<String, Object> bookData) {
        publish("book.published", bookData);
    }

    public void publishBookUpdated(Map<String, Object> bookData) {
        publish("book.updated", bookData);
    }

    public void publishBookDeleted(UUID bookId) {
        publish("book.deleted", Map.of("bookId", bookId.toString()));
    }

    public void publishStockLow(UUID bookId, int currentStock) {
        publish("book.stock-low", Map.of(
                "bookId", bookId.toString(),
                "stockQuantity", currentStock
        ));
    }

    private void publish(String routingKey, Object payload) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, routingKey, payload);
            log.debug("Published event [{}] to exchange [{}]", routingKey, RabbitMQConfig.EXCHANGE_NAME);
        } catch (Exception e) {
            log.error("Failed to publish event [{}]: {}", routingKey, e.getMessage(), e);
        }
    }
}
