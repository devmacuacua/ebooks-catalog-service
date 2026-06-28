package mz.ebooks.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorDto {

    private UUID id;

    @NotBlank(message = "Author name is required")
    private String name;

    private String bio;
    private String avatar;
    private LocalDateTime createdAt;
}
