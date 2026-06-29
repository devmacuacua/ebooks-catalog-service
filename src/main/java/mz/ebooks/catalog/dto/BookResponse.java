package mz.ebooks.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class BookResponse {

    private UUID id;
    private String title;
    private String slug;
    private String description;
    private String isbn;

    @JsonProperty("coverImageUrl")
    private String coverImage;

    private String[] previewImages;
    private LocalDateTime publishedAt;
    private String language;

    @JsonProperty("pageCount")
    private Integer pages;

    private String publisher;
    private String edition;
    private String type;
    private BigDecimal price;
    private boolean subscriptionOnly;
    private String fileKey;

    @JsonProperty("ebookSizeBytes")
    private Long fileSizeBytes;

    private String format;
    private int stockQuantity;
    private BigDecimal weight;
    private String dimensions;
    private boolean isActive;
    private boolean isFeatured;
    private String status;
    @JsonProperty("parecer")
    private String rejectionReason;
    private UUID partnerId;
    private String partnerName;
    private LocalDateTime submittedAt;
    private BigDecimal averageRating;

    @JsonProperty("totalReviews")
    private int reviewCount;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<String> authorNames;
    private List<AuthorDto> authors;
    private List<String> categoryNames;
    private List<CategoryDto> categories;
    private List<String> tags;
    private List<ReviewDto> reviews;
    private List<BookSummary> relatedBooks;
}
