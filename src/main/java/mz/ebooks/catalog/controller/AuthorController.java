package mz.ebooks.catalog.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mz.ebooks.catalog.dto.AuthorDto;
import mz.ebooks.catalog.entity.Author;
import mz.ebooks.catalog.repository.AuthorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/authors")
@RequiredArgsConstructor
public class AuthorController {

    private final AuthorRepository authorRepository;

    @GetMapping
    public ResponseEntity<List<AuthorDto>> listAuthors(
            @RequestParam(required = false) String name) {
        List<Author> authors = (name != null && !name.isBlank())
                ? authorRepository.findByNameContainingIgnoreCase(name)
                : authorRepository.findAll();
        return ResponseEntity.ok(authors.stream().map(this::toDto).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuthorDto> getAuthor(@PathVariable UUID id) {
        Author author = authorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Author not found: " + id));
        return ResponseEntity.ok(toDto(author));
    }

    @PostMapping
    public ResponseEntity<AuthorDto> createAuthor(
            @Valid @RequestBody AuthorDto dto,
            @RequestHeader("X-User-Role") String userRole) {
        requireEditorOrAdmin(userRole);
        Author author = Author.builder()
                .name(dto.getName())
                .bio(dto.getBio())
                .avatar(dto.getAvatar())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(authorRepository.save(author)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AuthorDto> updateAuthor(
            @PathVariable UUID id,
            @Valid @RequestBody AuthorDto dto,
            @RequestHeader("X-User-Role") String userRole) {
        requireEditorOrAdmin(userRole);
        Author author = authorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Author not found: " + id));
        if (dto.getName() != null) author.setName(dto.getName());
        if (dto.getBio() != null) author.setBio(dto.getBio());
        if (dto.getAvatar() != null) author.setAvatar(dto.getAvatar());
        return ResponseEntity.ok(toDto(authorRepository.save(author)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAuthor(
            @PathVariable UUID id,
            @RequestHeader("X-User-Role") String userRole) {
        requireAdmin(userRole);
        Author author = authorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Author not found: " + id));
        authorRepository.delete(author);
        return ResponseEntity.noContent().build();
    }

    private AuthorDto toDto(Author a) {
        return AuthorDto.builder()
                .id(a.getId())
                .name(a.getName())
                .bio(a.getBio())
                .avatar(a.getAvatar())
                .createdAt(a.getCreatedAt())
                .build();
    }

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
