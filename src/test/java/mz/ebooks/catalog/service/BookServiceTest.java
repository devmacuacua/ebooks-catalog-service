package mz.ebooks.catalog.service;

import mz.ebooks.catalog.dto.BookInternalDto;
import mz.ebooks.catalog.dto.BookResponse;
import mz.ebooks.catalog.dto.BookSummary;
import mz.ebooks.catalog.dto.CreateBookRequest;
import mz.ebooks.catalog.entity.Book;
import mz.ebooks.catalog.messaging.CatalogEventPublisher;
import mz.ebooks.catalog.repository.AuthorRepository;
import mz.ebooks.catalog.repository.BookRepository;
import mz.ebooks.catalog.repository.CategoryRepository;
import mz.ebooks.catalog.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock BookRepository bookRepository;
    @Mock AuthorRepository authorRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock TagRepository tagRepository;
    @Mock SearchService searchService;
    @Mock CatalogEventPublisher eventPublisher;
    @Mock MinioService minioService;

    @InjectMocks BookService bookService;

    private Book sampleBook;

    @BeforeEach
    void setUp() {
        sampleBook = Book.builder()
                .id(UUID.randomUUID())
                .title("Clean Code")
                .slug("clean-code")
                .description("A handbook of agile software craftsmanship")
                .type("EBOOK")
                .price(new BigDecimal("250.00"))
                .status("PUBLISHED")
                .isActive(true)
                .build();
    }

    @Test
    void getBookById_returnsBook() {
        when(bookRepository.findById(sampleBook.getId())).thenReturn(Optional.of(sampleBook));

        BookResponse result = bookService.getBookById(sampleBook.getId());

        assertThat(result.getId()).isEqualTo(sampleBook.getId());
        assertThat(result.getTitle()).isEqualTo("Clean Code");
        assertThat(result.getSlug()).isEqualTo("clean-code");
        assertThat(result.getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    void getBookById_throwsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(bookRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.getBookById(id))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void getBookInternal_returnsInternalDto() {
        when(bookRepository.findById(sampleBook.getId())).thenReturn(Optional.of(sampleBook));

        BookInternalDto result = bookService.getBookInternal(sampleBook.getId());

        assertThat(result.getId()).isEqualTo(sampleBook.getId());
        assertThat(result.getType()).isEqualTo("EBOOK");
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void getBatchInternal_returnsMappedDtos() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Book b1 = Book.builder().id(id1).title("Book 1").slug("book-1").type("EBOOK").price(BigDecimal.TEN).status("PUBLISHED").isActive(true).build();
        Book b2 = Book.builder().id(id2).title("Book 2").slug("book-2").type("PHYSICAL").price(BigDecimal.TEN).status("PUBLISHED").isActive(true).build();
        when(bookRepository.findAllById(List.of(id1, id2))).thenReturn(List.of(b1, b2));

        List<BookInternalDto> result = bookService.getBatchInternal(List.of(id1, id2));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("Book 1");
        assertThat(result.get(1).getTitle()).isEqualTo("Book 2");
    }

    @Test
    void submitForReview_changesDraftToPendingReview() {
        sampleBook.setStatus("DRAFT");
        when(bookRepository.findById(sampleBook.getId())).thenReturn(Optional.of(sampleBook));
        when(bookRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BookResponse result = bookService.submitForReview(sampleBook.getId(), "user-123");

        assertThat(result.getStatus()).isEqualTo("PENDING_REVIEW");
        verify(eventPublisher).publishBookUpdated(any());
    }

    @Test
    void submitForReview_throwsWhenNotDraft() {
        sampleBook.setStatus("PUBLISHED");
        when(bookRepository.findById(sampleBook.getId())).thenReturn(Optional.of(sampleBook));

        assertThatThrownBy(() -> bookService.submitForReview(sampleBook.getId(), "user-123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    void approveBook_changesPendingToPublished() {
        sampleBook.setStatus("PENDING_REVIEW");
        when(bookRepository.findById(sampleBook.getId())).thenReturn(Optional.of(sampleBook));
        when(bookRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BookResponse result = bookService.approveBook(sampleBook.getId());

        assertThat(result.getStatus()).isEqualTo("PUBLISHED");
        assertThat(result.isActive()).isTrue();
        verify(searchService).indexBook(sampleBook);
    }

    @Test
    void rejectBook_returnsBookToDraft() {
        sampleBook.setStatus("PENDING_REVIEW");
        when(bookRepository.findById(sampleBook.getId())).thenReturn(Optional.of(sampleBook));
        when(bookRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BookResponse result = bookService.rejectBook(sampleBook.getId(), "Poor description");

        assertThat(result.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void deleteBook_softDeletesAndRemovesFromIndex() {
        when(bookRepository.findById(sampleBook.getId())).thenReturn(Optional.of(sampleBook));
        when(bookRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        bookService.deleteBook(sampleBook.getId());

        assertThat(sampleBook.isActive()).isFalse();
        verify(searchService).removeFromIndex(sampleBook.getId().toString());
        verify(eventPublisher).publishBookDeleted(sampleBook.getId());
    }
}
