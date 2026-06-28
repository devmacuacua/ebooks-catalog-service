package mz.ebooks.catalog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mz.ebooks.catalog.dto.*;
import mz.ebooks.catalog.entity.Review;
import mz.ebooks.catalog.service.BookService;
import mz.ebooks.catalog.service.ReviewService;
import mz.ebooks.catalog.service.SearchService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/books")
@RequiredArgsConstructor
@Tag(name = "Books", description = "Book catalogue endpoints")
public class BookController {

    private final BookService bookService;
    private final SearchService searchService;
    private final ReviewService reviewService;

    // ───────── List / Search ─────────

    @GetMapping
    public ResponseEntity<Page<BookSummary>> listBooks(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean subscriptionOnly,
            @RequestParam(required = false) String search,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PageableDefault(size = 20) Pageable pageable) {
        boolean adminMode = "ADMIN".equalsIgnoreCase(userRole) || "EDITOR".equalsIgnoreCase(userRole);
        return ResponseEntity.ok(
                bookService.listBooks(type, categoryId, minPrice, maxPrice, subscriptionOnly, search, adminMode, pageable));
    }

    @GetMapping("/featured")
    public ResponseEntity<List<BookSummary>> getFeaturedBooks() {
        return ResponseEntity.ok(bookService.getFeaturedBooks());
    }

    @GetMapping("/new-arrivals")
    public ResponseEntity<List<BookSummary>> getNewArrivals() {
        return ResponseEntity.ok(bookService.getNewArrivals());
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResult> search(
            @RequestParam String q,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(searchService.search(q, type, page, size));
    }

    // ───────── Single Book ─────────

    @GetMapping("/{id}")
    public ResponseEntity<BookResponse> getBookById(@PathVariable UUID id) {
        return ResponseEntity.ok(bookService.getBookById(id));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<BookResponse> getBookBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(bookService.getBookBySlug(slug));
    }

    @GetMapping("/{id}/related")
    public ResponseEntity<List<BookSummary>> getRelatedBooks(@PathVariable UUID id) {
        return ResponseEntity.ok(bookService.getRelatedBooks(id));
    }

    // ───────── Write (ADMIN / EDITOR) ─────────

    @PostMapping
    public ResponseEntity<BookResponse> createBook(
            @Valid @RequestBody CreateBookRequest req,
            @RequestHeader("X-User-Role") String userRole) {
        requireEditorOrAdmin(userRole);
        return ResponseEntity.status(HttpStatus.CREATED).body(bookService.createBook(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BookResponse> updateBook(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBookRequest req,
            @RequestHeader("X-User-Role") String userRole) {
        requireEditorOrAdmin(userRole);
        return ResponseEntity.ok(bookService.updateBook(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBook(
            @PathVariable UUID id,
            @RequestHeader("X-User-Role") String userRole) {
        requireAdmin(userRole);
        bookService.deleteBook(id);
        return ResponseEntity.noContent().build();
    }

    // ───────── Suggest / Autocomplete ─────────

    @GetMapping("/search/suggest")
    @Operation(summary = "Autocomplete suggestions for book titles")
    public ResponseEntity<SuggestResult> suggest(
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(searchService.suggest(q, limit));
    }

    // ───────── Internal endpoints (partner-service, delivery-service) ─────────

    @GetMapping("/{id}/internal")
    @Operation(summary = "Internal book data — for use by other services only")
    public ResponseEntity<BookInternalDto> getBookInternal(@PathVariable UUID id) {
        return ResponseEntity.ok(bookService.getBookInternal(id));
    }

    @PostMapping("/batch")
    @Operation(summary = "Batch fetch books by IDs — internal use")
    public ResponseEntity<List<BookInternalDto>> getBatch(@Valid @RequestBody BatchBooksRequest req) {
        return ResponseEntity.ok(bookService.getBatchInternal(req.getIds()));
    }

    // ───────── Approval workflow ─────────

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit a DRAFT book for admin review")
    public ResponseEntity<BookResponse> submitForReview(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(bookService.submitForReview(id, userId));
    }

    @GetMapping("/pending")
    @Operation(summary = "List books pending admin review (ADMIN only)")
    public ResponseEntity<Page<BookSummary>> listPending(
            @RequestHeader("X-User-Role") String userRole,
            @PageableDefault(size = 20) Pageable pageable) {
        requireAdmin(userRole);
        return ResponseEntity.ok(bookService.listPendingReview(pageable));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a book (ADMIN only)")
    public ResponseEntity<BookResponse> approveBook(
            @PathVariable UUID id,
            @RequestHeader("X-User-Role") String userRole) {
        requireAdmin(userRole);
        return ResponseEntity.ok(bookService.approveBook(id));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a book back to DRAFT (ADMIN only)")
    public ResponseEntity<BookResponse> rejectBook(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            @RequestHeader("X-User-Role") String userRole) {
        requireAdmin(userRole);
        return ResponseEntity.ok(bookService.rejectBook(id, body.getOrDefault("reason", "")));
    }

    // ───────── Stock (internal) ─────────

    @PatchMapping("/{id}/stock")
    public ResponseEntity<Void> updateStock(
            @PathVariable UUID id,
            @RequestBody Map<String, Integer> body) {
        Integer delta = body.get("delta");
        if (delta == null) {
            return ResponseEntity.badRequest().build();
        }
        bookService.updateStock(id, delta);
        return ResponseEntity.ok().build();
    }

    // ───────── Cover Image ─────────

    @PostMapping("/{id}/cover")
    public ResponseEntity<Map<String, String>> uploadCoverImage(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestHeader("X-User-Role") String userRole) {
        requireEditorOrAdmin(userRole);
        String url = bookService.uploadCoverImage(id, file);
        return ResponseEntity.ok(Map.of("url", url));
    }

    // ───────── Reviews ─────────

    @GetMapping("/{id}/reviews")
    public ResponseEntity<Page<Review>> getReviews(
            @PathVariable UUID id,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(reviewService.getReviews(id, pageable));
    }

    @PostMapping("/{id}/reviews")
    public ResponseEntity<Review> addReview(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewRequest req,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Name", defaultValue = "Anonymous") String userName) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.addReview(id, userId, userName, req));
    }

    @DeleteMapping("/{id}/reviews/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @PathVariable UUID id,
            @PathVariable UUID reviewId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String userRole) {
        reviewService.deleteReview(reviewId, userId, userRole);
        return ResponseEntity.noContent().build();
    }

    // ───────── Guard helpers ─────────

    private void requireAdmin(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requires ADMIN role");
        }
    }

    private void requireEditorOrAdmin(String role) {
        if (!"ADMIN".equalsIgnoreCase(role) && !"EDITOR".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requires ADMIN or EDITOR role");
        }
    }
}
