package mz.ebooks.catalog.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mz.ebooks.catalog.dto.*;
import mz.ebooks.catalog.dto.BookInternalDto;
import mz.ebooks.catalog.entity.*;
import mz.ebooks.catalog.messaging.CatalogEventPublisher;
import mz.ebooks.catalog.repository.*;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static mz.ebooks.catalog.config.CacheConfig.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BookService {

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final ReviewRepository reviewRepository;
    private final SearchService searchService;
    private final CatalogEventPublisher eventPublisher;
    private final MinioService minioService;

    private static final int LOW_STOCK_THRESHOLD = 5;

    // ───────── Internal (partner-service, delivery-service) ─────────

    @Transactional(readOnly = true)
    public BookInternalDto getBookInternal(UUID id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Book not found: " + id));
        return toInternalDto(book);
    }

    @Transactional(readOnly = true)
    public List<BookInternalDto> getBatchInternal(List<UUID> ids) {
        return bookRepository.findAllById(ids).stream()
                .map(this::toInternalDto)
                .collect(Collectors.toList());
    }

    public BookInternalDto toInternalDto(Book book) {
        return BookInternalDto.builder()
                .id(book.getId())
                .title(book.getTitle())
                .slug(book.getSlug())
                .coverImage(book.getCoverImage())
                .price(book.getPrice())
                .type(book.getType())
                .format(book.getFormat())
                .fileKey(book.getFileKey())
                .totalPages(book.getPages())
                .subscriptionOnly(book.isSubscriptionOnly())
                .active(book.isActive())
                .status(book.getStatus())
                .authorNames(book.getAuthors().stream()
                        .map(Author::getName)
                        .collect(Collectors.toList()))
                .build();
    }

    // ───────── Approval workflow ─────────

    @CacheEvict(cacheNames = {CACHE_BOOK_BY_ID, CACHE_BOOK_BY_SLUG}, allEntries = true)
    public BookResponse submitForReview(UUID id, String userId) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Book not found: " + id));
        if (!"DRAFT".equals(book.getStatus())) {
            throw new IllegalStateException("Only DRAFT books can be submitted for review");
        }
        book.setStatus("PENDING_REVIEW");
        book = bookRepository.save(book);

        Map<String, Object> evt = new HashMap<>();
        evt.put("bookId", book.getId().toString());
        evt.put("title", book.getTitle());
        evt.put("submittedBy", userId);
        eventPublisher.publishBookUpdated(evt);

        return toBookResponse(book);
    }

    @CacheEvict(cacheNames = {CACHE_BOOK_BY_ID, CACHE_BOOK_BY_SLUG, CACHE_FEATURED_BOOKS}, allEntries = true)
    public BookResponse approveBook(UUID id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Book not found: " + id));
        if (!"PENDING_REVIEW".equals(book.getStatus())) {
            throw new IllegalStateException("Only PENDING_REVIEW books can be approved");
        }
        book.setStatus("PUBLISHED");
        book.setActive(true);
        book = bookRepository.save(book);
        searchService.indexBook(book);
        publishBookEvent("book.published", book);
        return toBookResponse(book);
    }

    @CacheEvict(cacheNames = {CACHE_BOOK_BY_ID, CACHE_BOOK_BY_SLUG}, allEntries = true)
    public BookResponse rejectBook(UUID id, String reason) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Book not found: " + id));
        book.setStatus("DRAFT");
        book = bookRepository.save(book);

        Map<String, Object> evt = new HashMap<>();
        evt.put("bookId", book.getId().toString());
        evt.put("title", book.getTitle());
        evt.put("reason", reason);
        eventPublisher.publishBookUpdated(evt);

        return toBookResponse(book);
    }

    @Transactional(readOnly = true)
    public Page<BookSummary> listPendingReview(Pageable pageable) {
        return bookRepository.findByStatus("PENDING_REVIEW", pageable)
                .map(this::toBookSummary);
    }

    // ───────── Create ─────────

    public BookResponse createBook(CreateBookRequest req) {
        Set<Author> authors = resolveAuthors(req.getAuthorIds());
        Set<Category> categories = resolveCategories(req.getCategoryIds());
        Set<Tag> tags = resolveTags(req.getTags());

        String rawSlug = (req.getSlug() != null && !req.getSlug().isBlank())
                ? req.getSlug() : req.getTitle();
        String slug = generateUniqueSlug(rawSlug, null);

        int pages = req.getPages() != null ? req.getPages()
                : (req.getPageCount() != null ? req.getPageCount() : 0);

        Book book = Book.builder()
                .title(req.getTitle())
                .slug(slug)
                .description(req.getDescription())
                .isbn(req.getIsbn())
                .publishedAt(req.getPublishedAt())
                .language(req.getLanguage() != null ? req.getLanguage() : "pt")
                .pages(pages)
                .publisher(req.getPublisher())
                .edition(req.getEdition())
                .type(req.getType())
                .price(req.getPrice())
                .subscriptionOnly(req.isSubscriptionOnly())
                .isFeatured(req.isFeatured())
                .stockQuantity(req.getStockQuantity() != null ? req.getStockQuantity() : 0)
                .fileKey(req.getFileKey())
                .fileSizeBytes(req.getFileSizeBytes())
                .format(req.getFormat())
                .weight(req.getWeight())
                .dimensions(req.getDimensions())
                .authors(authors)
                .categories(categories)
                .tags(tags)
                .build();

        book = bookRepository.save(book);

        searchService.indexBook(book);
        publishBookEvent("book.published", book);

        return toBookResponse(book);
    }

    // ───────── Update ─────────

    @CacheEvict(cacheNames = {CACHE_BOOK_BY_ID, CACHE_BOOK_BY_SLUG, CACHE_FEATURED_BOOKS}, allEntries = true)
    public BookResponse updateBook(UUID id, UpdateBookRequest req) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Book not found: " + id));

        if (req.getTitle() != null) book.setTitle(req.getTitle());
        if (req.getSlug() != null) book.setSlug(generateUniqueSlug(req.getSlug(), id));
        if (req.getDescription() != null) book.setDescription(req.getDescription());
        if (req.getType() != null) book.setType(req.getType());
        if (req.getPrice() != null) book.setPrice(req.getPrice());
        if (req.getIsbn() != null) book.setIsbn(req.getIsbn());
        if (req.getCoverImage() != null) book.setCoverImage(req.getCoverImage());
        if (req.getPages() != null) book.setPages(req.getPages());
        if (req.getPublisher() != null) book.setPublisher(req.getPublisher());
        if (req.getEdition() != null) book.setEdition(req.getEdition());
        if (req.getLanguage() != null) book.setLanguage(req.getLanguage());
        if (req.getFormat() != null) book.setFormat(req.getFormat());
        if (req.getFileKey() != null) book.setFileKey(req.getFileKey());
        if (req.getFileSizeBytes() != null) book.setFileSizeBytes(req.getFileSizeBytes());
        if (req.getWeight() != null) book.setWeight(req.getWeight());
        if (req.getDimensions() != null) book.setDimensions(req.getDimensions());
        if (req.getPublishedAt() != null) book.setPublishedAt(req.getPublishedAt());
        if (req.getSubscriptionOnly() != null) book.setSubscriptionOnly(req.getSubscriptionOnly());
        if (req.getIsActive() != null) book.setActive(req.getIsActive());
        if (req.getIsFeatured() != null) book.setFeatured(req.getIsFeatured());
        if (req.getStockQuantity() != null) book.setStockQuantity(req.getStockQuantity());

        if (req.getAuthorIds() != null) book.setAuthors(resolveAuthors(req.getAuthorIds()));
        if (req.getCategoryIds() != null) book.setCategories(resolveCategories(req.getCategoryIds()));
        if (req.getTags() != null) book.setTags(resolveTags(req.getTags()));

        book = bookRepository.save(book);

        searchService.indexBook(book);
        publishBookEvent("book.updated", book);

        return toBookResponse(book);
    }

    // ───────── Read ─────────

    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_BOOK_BY_ID, key = "#id")
    public BookResponse getBookById(UUID id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Book not found: " + id));
        return toBookResponse(book);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_BOOK_BY_SLUG, key = "#slug")
    public BookResponse getBookBySlug(String slug) {
        Book book = bookRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Book not found with slug: " + slug));
        BookResponse response = toBookResponse(book);
        List<ReviewDto> reviews = reviewRepository
                .findByBookId(book.getId(), PageRequest.of(0, 10, Sort.by("createdAt").descending()))
                .getContent()
                .stream()
                .map(r -> ReviewDto.builder()
                        .id(r.getId()).userId(r.getUserId()).userName(r.getUserName())
                        .rating(r.getRating()).comment(r.getComment()).createdAt(r.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
        response.setReviews(reviews);
        List<BookSummary> related = bookRepository
                .findRelatedBooks(book.getId(), PageRequest.of(0, 6))
                .stream()
                .map(this::toBookSummary)
                .collect(Collectors.toList());
        response.setRelatedBooks(related);
        return response;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_FEATURED_BOOKS)
    public List<BookSummary> getFeaturedBooks() {
        return bookRepository.findByIsFeaturedTrueAndIsActiveTrue()
                .stream()
                .map(this::toBookSummary)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BookSummary> getNewArrivals() {
        return bookRepository.findTop12ByIsActiveTrueOrderByCreatedAtDesc()
                .stream()
                .map(this::toBookSummary)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<BookSummary> listBooks(String type, UUID categoryId, BigDecimal minPrice,
                                       BigDecimal maxPrice, Boolean subscriptionOnly,
                                       String search, boolean adminMode, Pageable pageable) {
        Specification<Book> spec = buildSpec(type, categoryId, minPrice, maxPrice, subscriptionOnly, search, adminMode);
        Page<Book> books = bookRepository.findAll(spec, pageable);
        return books.map(this::toBookSummary);
    }

    @Transactional(readOnly = true)
    public List<BookSummary> getRelatedBooks(UUID bookId) {
        if (!bookRepository.existsById(bookId)) {
            throw new EntityNotFoundException("Book not found: " + bookId);
        }
        return bookRepository.findRelatedBooks(bookId, PageRequest.of(0, 10))
                .stream()
                .map(this::toBookSummary)
                .collect(Collectors.toList());
    }

    // ───────── Stock ─────────

    @CacheEvict(cacheNames = {CACHE_BOOK_BY_ID, CACHE_BOOK_BY_SLUG}, allEntries = true)
    public void updateStock(UUID bookId, int delta) {
        if (!bookRepository.existsById(bookId)) {
            throw new EntityNotFoundException("Book not found: " + bookId);
        }
        bookRepository.updateStock(bookId, delta);

        // Check for low stock after decrease
        if (delta < 0) {
            bookRepository.findById(bookId).ifPresent(book -> {
                if (book.getStockQuantity() < LOW_STOCK_THRESHOLD) {
                    eventPublisher.publishStockLow(bookId, book.getStockQuantity());
                }
            });
        }
    }

    // ───────── Delete ─────────

    @CacheEvict(cacheNames = {CACHE_BOOK_BY_ID, CACHE_BOOK_BY_SLUG, CACHE_FEATURED_BOOKS}, allEntries = true)
    public void deleteBook(UUID id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Book not found: " + id));
        book.setActive(false);
        bookRepository.save(book);
        searchService.removeFromIndex(id.toString());
        eventPublisher.publishBookDeleted(id);
    }

    // ───────── Cover Image ─────────

    @CacheEvict(cacheNames = {CACHE_BOOK_BY_ID, CACHE_BOOK_BY_SLUG}, allEntries = true)
    public String uploadCoverImage(UUID bookId, MultipartFile file) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new EntityNotFoundException("Book not found: " + bookId));

        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals(MediaType.IMAGE_JPEG_VALUE)
                && !contentType.equals(MediaType.IMAGE_PNG_VALUE)
                && !contentType.equals("image/webp"))) {
            throw new IllegalArgumentException("Only JPEG, PNG, and WebP images are allowed");
        }

        String extension = contentType.equals(MediaType.IMAGE_JPEG_VALUE) ? ".jpg"
                : contentType.equals(MediaType.IMAGE_PNG_VALUE) ? ".png" : ".webp";
        String objectName = "covers/" + bookId + extension;

        try {
            String url = minioService.uploadFile(objectName, file.getInputStream(),
                    file.getSize(), contentType);
            book.setCoverImage(url);
            bookRepository.save(book);
            return url;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file", e);
        }
    }

    // ───────── Mappers ─────────

    public BookResponse toBookResponse(Book book) {
        return BookResponse.builder()
                .id(book.getId())
                .title(book.getTitle())
                .slug(book.getSlug())
                .description(book.getDescription())
                .isbn(book.getIsbn())
                .coverImage(book.getCoverImage())
                .previewImages(book.getPreviewImages())
                .publishedAt(book.getPublishedAt())
                .language(book.getLanguage())
                .pages(book.getPages())
                .publisher(book.getPublisher())
                .edition(book.getEdition())
                .type(book.getType())
                .price(book.getPrice())
                .subscriptionOnly(book.isSubscriptionOnly())
                .fileKey(book.getFileKey())
                .fileSizeBytes(book.getFileSizeBytes())
                .format(book.getFormat())
                .stockQuantity(book.getStockQuantity())
                .weight(book.getWeight())
                .dimensions(book.getDimensions())
                .isActive(book.isActive())
                .isFeatured(book.isFeatured())
                .status(book.getStatus())
                .averageRating(book.getAverageRating())
                .reviewCount(book.getReviewCount())
                .createdAt(book.getCreatedAt())
                .updatedAt(book.getUpdatedAt())
                .authorNames(book.getAuthors().stream()
                        .map(Author::getName)
                        .collect(Collectors.toList()))
                .authors(book.getAuthors().stream()
                        .map(a -> AuthorDto.builder()
                                .id(a.getId())
                                .name(a.getName())
                                .bio(a.getBio())
                                .avatar(a.getAvatar())
                                .createdAt(a.getCreatedAt())
                                .build())
                        .collect(Collectors.toList()))
                .categoryNames(book.getCategories().stream()
                        .map(Category::getName)
                        .collect(Collectors.toList()))
                .categories(book.getCategories().stream()
                        .map(c -> CategoryDto.builder()
                                .id(c.getId())
                                .name(c.getName())
                                .slug(c.getSlug())
                                .icon(c.getIcon())
                                .parentId(c.getParent() != null ? c.getParent().getId() : null)
                                .build())
                        .collect(Collectors.toList()))
                .tags(book.getTags().stream()
                        .map(Tag::getName)
                        .collect(Collectors.toList()))
                .build();
    }

    public BookSummary toBookSummary(Book book) {
        return BookSummary.builder()
                .id(book.getId())
                .title(book.getTitle())
                .slug(book.getSlug())
                .coverImage(book.getCoverImage())
                .price(book.getPrice())
                .type(book.getType())
                .authorNames(book.getAuthors().stream()
                        .map(Author::getName)
                        .collect(Collectors.toList()))
                .averageRating(book.getAverageRating())
                .reviewCount(book.getReviewCount())
                .subscriptionOnly(book.isSubscriptionOnly())
                .isFeatured(book.isFeatured())
                .build();
    }

    // ───────── Private helpers ─────────

    private Set<Author> resolveAuthors(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return new HashSet<>();
        return new HashSet<>(authorRepository.findAllById(ids));
    }

    private Set<Category> resolveCategories(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return new HashSet<>();
        return new HashSet<>(categoryRepository.findAllById(ids));
    }

    private Set<Tag> resolveTags(List<String> names) {
        if (names == null || names.isEmpty()) return new HashSet<>();
        List<Tag> existing = tagRepository.findByNameIn(names);
        Set<String> existingNames = existing.stream().map(Tag::getName).collect(Collectors.toSet());
        List<Tag> newTags = names.stream()
                .filter(n -> !existingNames.contains(n))
                .map(n -> tagRepository.save(Tag.builder().name(n).build()))
                .collect(Collectors.toList());
        Set<Tag> all = new HashSet<>(existing);
        all.addAll(newTags);
        return all;
    }

    private String generateUniqueSlug(String baseSlug, UUID excludeId) {
        String candidate = baseSlug;
        int suffix = 2;
        while (true) {
            boolean exists = excludeId == null
                    ? bookRepository.existsBySlug(candidate)
                    : bookRepository.existsBySlugAndIdNot(candidate, excludeId);
            if (!exists) return candidate;
            candidate = baseSlug + "-" + suffix++;
        }
    }

    private Specification<Book> buildSpec(String type, UUID categoryId,
                                          BigDecimal minPrice, BigDecimal maxPrice,
                                          Boolean subscriptionOnly, String search, boolean adminMode) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!adminMode) {
                predicates.add(cb.isTrue(root.get("isActive")));
                predicates.add(cb.equal(root.get("status"), "PUBLISHED"));
            }
            if (type != null && !type.isBlank()) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (categoryId != null) {
                Join<Book, Category> categories = root.join("categories", JoinType.INNER);
                predicates.add(cb.equal(categories.get("id"), categoryId));
                query.distinct(true);
            }
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }
            if (subscriptionOnly != null) {
                predicates.add(cb.equal(root.get("subscriptionOnly"), subscriptionOnly));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("isbn")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void publishBookEvent(String eventType, Book book) {
        Map<String, Object> data = new HashMap<>();
        data.put("bookId", book.getId().toString());
        data.put("title", book.getTitle());
        data.put("slug", book.getSlug());
        data.put("type", book.getType());
        data.put("price", book.getPrice());
        data.put("isActive", book.isActive());

        if ("book.published".equals(eventType)) {
            eventPublisher.publishBookPublished(data);
        } else if ("book.updated".equals(eventType)) {
            eventPublisher.publishBookUpdated(data);
        }
    }
}
