package mz.ebooks.catalog.repository;

import mz.ebooks.catalog.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Page<Review> findByBookId(UUID bookId, Pageable pageable);

    boolean existsByUserIdAndBookId(UUID userId, UUID bookId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.bookId = :bookId")
    Double findAverageRatingByBookId(@Param("bookId") UUID bookId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.bookId = :bookId")
    long countByBookId(@Param("bookId") UUID bookId);
}
