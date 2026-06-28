package mz.ebooks.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String slug;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Type is required")
    private String type;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.00", message = "Price must be non-negative")
    private BigDecimal price;

    private List<UUID> authorIds;
    private List<UUID> categoryIds;
    private List<String> tags;

    private String isbn;
    private Integer pages;
    private String publisher;
    private String edition;
    private String language;
    private String format;
    private String fileKey;
    private Long fileSizeBytes;
    private BigDecimal weight;
    private String dimensions;
    private LocalDateTime publishedAt;
    private boolean subscriptionOnly;
    private boolean featured;
    private Integer stockQuantity;
    private Integer pageCount;
}
