package mz.ebooks.catalog.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mz.ebooks.catalog.config.RabbitMQConfig;
import mz.ebooks.catalog.service.BookService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final BookService bookService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_ORDER_COMPLETED)
    public void handleOrderCompleted(Map<String, Object> event) {
        try {
            String bookIdStr = (String) event.get("bookId");
            Integer quantity = (Integer) event.get("quantity");
            if (bookIdStr == null || quantity == null) {
                log.warn("Order completed event missing bookId or quantity: {}", event);
                return;
            }
            UUID bookId = UUID.fromString(bookIdStr);
            bookService.updateStock(bookId, -quantity);
            log.info("Stock decreased by {} for book {}", quantity, bookId);
        } catch (Exception e) {
            log.error("Failed to process order.completed event: {}", e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_ORDER_CANCELLED)
    public void handleOrderCancelled(Map<String, Object> event) {
        try {
            String bookIdStr = (String) event.get("bookId");
            Integer quantity = (Integer) event.get("quantity");
            if (bookIdStr == null || quantity == null) {
                log.warn("Order cancelled event missing bookId or quantity: {}", event);
                return;
            }
            UUID bookId = UUID.fromString(bookIdStr);
            bookService.updateStock(bookId, +quantity);
            log.info("Stock restored by {} for book {}", quantity, bookId);
        } catch (Exception e) {
            log.error("Failed to process order.cancelled event: {}", e.getMessage(), e);
        }
    }
}
