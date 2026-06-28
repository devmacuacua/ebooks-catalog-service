package mz.ebooks.catalog.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

    private List<BookSummary> items;
    private long total;
    private int page;
    private int size;
}
