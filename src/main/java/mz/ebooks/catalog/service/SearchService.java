package mz.ebooks.catalog.service;

import co.elastic.clients.elasticsearch._types.query_dsl.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mz.ebooks.catalog.dto.BookSummary;
import mz.ebooks.catalog.dto.SearchResult;
import mz.ebooks.catalog.dto.SuggestResult;
import org.springframework.scheduling.annotation.Async;
import mz.ebooks.catalog.entity.Book;
import mz.ebooks.catalog.repository.BookSearchRepository;
import mz.ebooks.catalog.search.BookDocument;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final BookSearchRepository bookSearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    public SearchResult search(String query, String type, int page, int size) {
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder()
                .must(MultiMatchQuery.of(m -> m
                        .query(query)
                        .fields(List.of("title^3", "isbn^4", "authorNames^2", "description^1",
                                "tags^1.5", "categoryNames^1.5"))
                        .type(TextQueryType.BestFields)
                )._toQuery())
                .filter(TermQuery.of(t -> t.field("isActive").value(true))._toQuery());

        if (type != null && !type.isBlank()) {
            boolBuilder.filter(TermQuery.of(t -> t.field("type").value(type))._toQuery());
        }

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(boolBuilder.build()._toQuery())
                .withPageable(PageRequest.of(page, size))
                .build();

        SearchHits<BookDocument> hits = elasticsearchOperations.search(nativeQuery, BookDocument.class);

        List<BookSummary> items = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(this::toBookSummary)
                .collect(Collectors.toList());

        return SearchResult.builder()
                .items(items)
                .total(hits.getTotalHits())
                .page(page)
                .size(size)
                .build();
    }

    public SuggestResult suggest(String query, int limit) {
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(BoolQuery.of(b -> b
                        .must(PrefixQuery.of(p -> p.field("title").value(query.toLowerCase()))._toQuery())
                        .filter(TermQuery.of(t -> t.field("isActive").value(true))._toQuery())
                        .filter(TermQuery.of(t -> t.field("status").value("PUBLISHED"))._toQuery())
                )._toQuery())
                .withPageable(PageRequest.of(0, Math.min(limit, 10)))
                .build();

        SearchHits<BookDocument> hits = elasticsearchOperations.search(nativeQuery, BookDocument.class);

        List<SuggestResult.SuggestItem> items = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(doc -> SuggestResult.SuggestItem.builder()
                        .text(doc.getTitle())
                        .slug(doc.getSlug())
                        .coverImage(doc.getCoverImage())
                        .build())
                .collect(Collectors.toList());

        return SuggestResult.builder().suggestions(items).build();
    }

    @Async
    public void indexBook(Book book) {
        try {
            BookDocument document = toBookDocument(book);
            bookSearchRepository.save(document);
            log.debug("Indexed book {} in Elasticsearch", book.getId());
        } catch (Exception e) {
            log.error("Failed to index book {}: {}", book.getId(), e.getMessage(), e);
        }
    }

    public void removeFromIndex(String bookId) {
        try {
            bookSearchRepository.deleteById(bookId);
            log.debug("Removed book {} from Elasticsearch index", bookId);
        } catch (Exception e) {
            log.error("Failed to remove book {} from index: {}", bookId, e.getMessage(), e);
        }
    }

    private BookDocument toBookDocument(Book book) {
        return BookDocument.builder()
                .id(book.getId().toString())
                .title(book.getTitle())
                .slug(book.getSlug())
                .coverImage(book.getCoverImage())
                .status(book.getStatus())
                .description(book.getDescription())
                .authorNames(book.getAuthors().stream()
                        .map(a -> a.getName())
                        .collect(Collectors.toList()))
                .categoryNames(book.getCategories().stream()
                        .map(c -> c.getName())
                        .collect(Collectors.toList()))
                .tags(book.getTags().stream()
                        .map(t -> t.getName())
                        .collect(Collectors.toList()))
                .isbn(book.getIsbn())
                .publisher(book.getPublisher())
                .language(book.getLanguage())
                .type(book.getType())
                .price(book.getPrice() != null ? book.getPrice().doubleValue() : null)
                .subscriptionOnly(book.isSubscriptionOnly())
                .isActive(book.isActive())
                .isFeatured(book.isFeatured())
                .averageRating(book.getAverageRating() != null
                        ? book.getAverageRating().doubleValue() : 0.0)
                .publishedAt(book.getPublishedAt())
                .build();
    }

    private BookSummary toBookSummary(BookDocument doc) {
        return BookSummary.builder()
                .id(doc.getId() != null ? java.util.UUID.fromString(doc.getId()) : null)
                .title(doc.getTitle())
                .price(doc.getPrice() != null ? BigDecimal.valueOf(doc.getPrice()) : null)
                .type(doc.getType())
                .authorNames(doc.getAuthorNames())
                .averageRating(BigDecimal.valueOf(doc.getAverageRating()))
                .subscriptionOnly(doc.isSubscriptionOnly())
                .build();
    }
}
