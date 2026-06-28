package mz.ebooks.catalog.repository;

import mz.ebooks.catalog.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TagRepository extends JpaRepository<Tag, UUID> {

    List<Tag> findByNameIn(List<String> names);

    Optional<Tag> findByName(String name);
}
