package mz.ebooks.catalog.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookSummary {

    private UUID id;
    private String title;
    private String slug;
    private String coverImage;
    private BigDecimal price;
    private String type;
    private List<String> authorNames;
    private BigDecimal averageRating;
    private int reviewCount;
    private boolean subscriptionOnly;
    private boolean isFeatured;
}
