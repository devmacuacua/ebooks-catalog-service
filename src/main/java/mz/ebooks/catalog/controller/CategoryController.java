package mz.ebooks.catalog.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mz.ebooks.catalog.dto.CategoryDto;
import mz.ebooks.catalog.entity.Category;
import mz.ebooks.catalog.repository.CategoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;

    @GetMapping
    public ResponseEntity<List<CategoryDto>> listCategories() {
        return ResponseEntity.ok(categoryRepository.findAll()
                .stream().map(c -> toDto(c, false)).collect(Collectors.toList()));
    }

    @GetMapping("/tree")
    public ResponseEntity<List<CategoryDto>> getCategoryTree() {
        List<Category> roots = categoryRepository.findByParentIdIsNull();
        return ResponseEntity.ok(roots.stream()
                .map(c -> toDto(c, true))
                .collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryDto> getCategory(@PathVariable UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category not found: " + id));
        return ResponseEntity.ok(toDto(category, true));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<CategoryDto> getCategoryBySlug(@PathVariable String slug) {
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Category not found with slug: " + slug));
        return ResponseEntity.ok(toDto(category, true));
    }

    @PostMapping
    public ResponseEntity<CategoryDto> createCategory(
            @Valid @RequestBody CategoryDto dto,
            @RequestHeader("X-User-Role") String userRole) {
        requireAdmin(userRole);

        Category parent = null;
        if (dto.getParentId() != null) {
            parent = categoryRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Parent category not found: " + dto.getParentId()));
        }

        Category category = Category.builder()
                .name(dto.getName())
                .slug(dto.getSlug())
                .icon(dto.getIcon())
                .parent(parent)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toDto(categoryRepository.save(category), false));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryDto> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryDto dto,
            @RequestHeader("X-User-Role") String userRole) {
        requireAdmin(userRole);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category not found: " + id));

        if (dto.getName() != null) category.setName(dto.getName());
        if (dto.getSlug() != null) category.setSlug(dto.getSlug());
        if (dto.getIcon() != null) category.setIcon(dto.getIcon());
        if (dto.getParentId() != null) {
            Category parent = categoryRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Parent category not found: " + dto.getParentId()));
            category.setParent(parent);
        }

        return ResponseEntity.ok(toDto(categoryRepository.save(category), true));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable UUID id,
            @RequestHeader("X-User-Role") String userRole) {
        requireAdmin(userRole);
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category not found: " + id));
        categoryRepository.delete(category);
        return ResponseEntity.noContent().build();
    }

    private CategoryDto toDto(Category c, boolean includeChildren) {
        List<CategoryDto> children = null;
        if (includeChildren && c.getChildren() != null && !c.getChildren().isEmpty()) {
            children = c.getChildren().stream()
                    .map(child -> toDto(child, true))
                    .collect(Collectors.toList());
        }
        return CategoryDto.builder()
                .id(c.getId())
                .name(c.getName())
                .slug(c.getSlug())
                .icon(c.getIcon())
                .parentId(c.getParent() != null ? c.getParent().getId() : null)
                .children(children)
                .build();
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requires ADMIN role");
        }
    }
}
