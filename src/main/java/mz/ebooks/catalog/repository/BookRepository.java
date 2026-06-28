package mz.ebooks.catalog.repository;

import mz.ebooks.catalog.entity.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookRepository extends JpaRepository<Book, UUID>, JpaSpecificationExecutor<Book> {

    Optional<Book> findBySlug(String slug);

    Page<Book> findByIsActiveTrue(Pageable pageable);

    List<Book> findByIsFeaturedTrueAndIsActiveTrue();

    Page<Book> findByTypeAndIsActiveTrue(String type, Pageable pageable);

    @Query("SELECT DISTINCT b FROM Book b JOIN b.categories c WHERE c.id = :categoryId AND b.isActive = true")
    Page<Book> findByCategoryId(@Param("categoryId") UUID categoryId, Pageable pageable);

    @Modifying
    @Query("UPDATE Book b SET b.stockQuantity = b.stockQuantity + :delta WHERE b.id = :id")
    void updateStock(@Param("id") UUID id, @Param("delta") int delta);

    @Query("""
            SELECT DISTINCT b FROM Book b
            JOIN b.categories c
            WHERE c IN (
                SELECT c2 FROM Book b2 JOIN b2.categories c2 WHERE b2.id = :bookId
            )
            AND b.id <> :bookId
            AND b.isActive = true
            """)
    List<Book> findRelatedBooks(@Param("bookId") UUID bookId, Pageable pageable);

    List<Book> findTop12ByIsActiveTrueOrderByCreatedAtDesc();

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    Page<Book> findByStatus(String status, Pageable pageable);
}
