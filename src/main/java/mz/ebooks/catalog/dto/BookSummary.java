package mz.ebooks.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("coverImageUrl")
    private String coverImage;

    private BigDecimal price;
    private String type;
    private List<String> authorNames;
    private BigDecimal averageRating;

    @JsonProperty("totalReviews")
    private int reviewCount;

    private boolean subscriptionOnly;

    private boolean isFeatured;
}
