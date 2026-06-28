package mz.ebooks.catalog.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mz.ebooks.catalog.dto.ReviewRequest;
import mz.ebooks.catalog.entity.Book;
import mz.ebooks.catalog.entity.Review;
import mz.ebooks.catalog.repository.BookRepository;
import mz.ebooks.catalog.repository.ReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookRepository bookRepository;

    public Review addReview(UUID bookId, String userId, String userName, ReviewRequest req) {
        UUID userUuid = UUID.fromString(userId);

        if (reviewRepository.existsByUserIdAndBookId(userUuid, bookId)) {
            throw new IllegalStateException("User has already reviewed this book");
        }

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new EntityNotFoundException("Book not found: " + bookId));

        Review review = Review.builder()
                .userId(userUuid)
                .userName(userName)
                .bookId(bookId)
                .rating(req.getRating())
                .comment(req.getComment())
                .build();

        review = reviewRepository.save(review);
        recalculateBookRating(book);

        return review;
    }

    @Transactional(readOnly = true)
    public Page<Review> getReviews(UUID bookId, Pageable pageable) {
        return reviewRepository.findByBookId(bookId, pageable);
    }

    public void deleteReview(UUID reviewId, String requestingUserId, String userRole) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Review not found: " + reviewId));

        boolean isAdmin = "ADMIN".equalsIgnoreCase(userRole);
        boolean isOwner = review.getUserId().toString().equals(requestingUserId);

        if (!isAdmin && !isOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: you can only delete your own reviews");
        }

        UUID bookId = review.getBookId();
        reviewRepository.delete(review);

        bookRepository.findById(bookId).ifPresent(this::recalculateBookRating);
    }

    private void recalculateBookRating(Book book) {
        Double avg = reviewRepository.findAverageRatingByBookId(book.getId());
        long count = reviewRepository.countByBookId(book.getId());

        book.setAverageRating(avg != null
                ? BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        book.setReviewCount((int) count);
        bookRepository.save(book);
    }
}
