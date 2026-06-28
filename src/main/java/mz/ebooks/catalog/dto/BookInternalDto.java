package mz.ebooks.catalog.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class BookInternalDto {
    private UUID id;
    private String title;
    private String slug;
    private String coverImage;
    private BigDecimal price;
    private String type;
    private String format;
    private String fileKey;
    private Integer totalPages;
    private boolean subscriptionOnly;
    private boolean active;
    private String status;
    private List<String> authorNames;
}
