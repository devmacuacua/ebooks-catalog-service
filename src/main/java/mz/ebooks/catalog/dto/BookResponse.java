package mz.ebooks.catalog.dto;

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
    private String coverImage;
    private String[] previewImages;
    private LocalDateTime publishedAt;
    private String language;
    private Integer pages;
    private String publisher;
    private String edition;
    private String type;
    private BigDecimal price;
    private boolean subscriptionOnly;
    private String fileKey;
    private Long fileSizeBytes;
    private String format;
    private int stockQuantity;
    private BigDecimal weight;
    private String dimensions;
    private boolean isActive;
    private boolean isFeatured;
    private String status;
    private BigDecimal averageRating;
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
