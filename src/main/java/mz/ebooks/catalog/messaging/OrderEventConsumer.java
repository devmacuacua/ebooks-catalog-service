package mz.ebooks.catalog.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mz.ebooks.catalog.config.RabbitMQConfig;
import mz.ebooks.catalog.service.BookService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final BookService bookService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_ORDER_PAID)
    public void handleOrderPaid(Map<String, Object> event) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) event.get("items");
            if (items == null || items.isEmpty()) {
                log.warn("Order paid event has no items: {}", event.get("orderId"));
                return;
            }
            for (Map<String, Object> item : items) {
                String bookType = (String) item.get("bookType");
                if (!"PHYSICAL".equals(bookType) && !"BOTH".equals(bookType)) continue;

                String bookIdStr = (String) item.get("bookId");
                Object quantityObj = item.get("quantity");
                if (bookIdStr == null || quantityObj == null) continue;

                UUID bookId = UUID.fromString(bookIdStr);
                int quantity = ((Number) quantityObj).intValue();
                bookService.updateStock(bookId, -quantity);
                log.info("Stock decreased by {} for book {}", quantity, bookId);
            }
        } catch (Exception e) {
            log.error("Failed to process order.paid event: {}", e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_ORDER_CANCELLED)
    public void handleOrderCancelled(Map<String, Object> event) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) event.get("items");
            if (items == null || items.isEmpty()) {
                log.warn("Order cancelled event has no items: {}", event.get("orderId"));
                return;
            }
            for (Map<String, Object> item : items) {
                String bookType = (String) item.get("bookType");
                if (!"PHYSICAL".equals(bookType) && !"BOTH".equals(bookType)) continue;

                String bookIdStr = (String) item.get("bookId");
                Object quantityObj = item.get("quantity");
                if (bookIdStr == null || quantityObj == null) continue;

                UUID bookId = UUID.fromString(bookIdStr);
                int quantity = ((Number) quantityObj).intValue();
                bookService.updateStock(bookId, +quantity);
                log.info("Stock restored by {} for book {}", quantity, bookId);
            }
        } catch (Exception e) {
            log.error("Failed to process order.cancelled event: {}", e.getMessage(), e);
        }
    }
}
