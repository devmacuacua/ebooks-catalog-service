package mz.ebooks.catalog.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
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
public class UpdateBookRequest {

    private String title;
    private String slug;
    private String description;
    private String type;

    @DecimalMin(value = "0.00", message = "Price must be non-negative")
    private BigDecimal price;

    private List<UUID> authorIds;
    private List<UUID> categoryIds;
    private List<String> tags;

    private String isbn;
    @JsonAlias("pageCount")
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
    private Boolean subscriptionOnly;
    private Boolean isActive;
    @JsonAlias("featured")
    private Boolean isFeatured;
    private Integer stockQuantity;
}
