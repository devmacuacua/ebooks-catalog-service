package mz.ebooks.catalog.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDto {
    private UUID id;
    private UUID userId;
    private String userName;
    private int rating;
    private String comment;
    private LocalDateTime createdAt;
}
