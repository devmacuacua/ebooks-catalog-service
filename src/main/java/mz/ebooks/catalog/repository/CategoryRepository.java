package mz.ebooks.catalog.repository;

import mz.ebooks.catalog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByParentIdIsNull();

    Optional<Category> findBySlug(String slug);
}
