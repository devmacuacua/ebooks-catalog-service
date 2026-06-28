package mz.ebooks.catalog.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BatchBooksRequest {

    @NotEmpty
    @Size(max = 100)
    private List<UUID> ids;
}
