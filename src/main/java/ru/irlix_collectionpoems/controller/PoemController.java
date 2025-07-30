package ru.irlix_collectionpoems.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import ru.irlix_collectionpoems.model.Poem;
import ru.irlix_collectionpoems.repository.UserRepository;
import ru.irlix_collectionpoems.service.PoemService;

import java.util.List;

@RestController
@RequestMapping("/api/poems")
public class PoemController {

    private final PoemService poemService;
    private final UserRepository userRepository;

    @Autowired
    public PoemController(PoemService poemService, UserRepository userRepository) {
        this.poemService = poemService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<Poem> getAllPoems() {
        return poemService.getAllPoems();
    }

    @GetMapping("/search")
    public List<Poem> searchPoemsByTitle(@RequestParam String title) {
        return poemService.searchPoemsByTitle(title);
    }

    @GetMapping("/author/{authorId}")
    public List<Poem> searchPoemsByAuthor(@PathVariable Long authorId) {
        var author = userRepository.findById(authorId)
                .orElseThrow(() -> new RuntimeException("Автор не найден"));
        return poemService.searchPoemsByAuthor(author);
    }

    @GetMapping("/{poemId}")
    public ResponseEntity<Poem> readPoem(@PathVariable Long poemId) {
        var poem = poemService.getPoemById(poemId)
                .orElseThrow(() -> new RuntimeException("Стихотворение не найдено"));
        return ResponseEntity.ok(poem);
    }

    @DeleteMapping("/delete-account")
    public ResponseEntity<String> deleteAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        var currentUsername = authentication.getName();
        var currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        /* Удаляем все стихи пользователя */
        List<Poem> poems = poemService.searchPoemsByAuthor(currentUser);
        for (Poem poem : poems) {
            poemService.deletePoem(poem.getId());
        }

        // Удаляем самого пользователя
        userRepository.delete(currentUser);

        return new ResponseEntity<>("Учетная запись успешно удалена", HttpStatus.OK);
    }
}