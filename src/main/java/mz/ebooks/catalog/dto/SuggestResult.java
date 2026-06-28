package mz.ebooks.catalog.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SuggestResult {
    private List<SuggestItem> suggestions;

    @Data
    @Builder
    public static class SuggestItem {
        private String text;
        private String slug;
        private String coverImage;
    }
}
