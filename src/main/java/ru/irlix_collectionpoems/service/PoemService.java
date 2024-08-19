package ru.irlix_collectionpoems.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.irlix_collectionpoems.model.Poem;
import ru.irlix_collectionpoems.model.User;
import ru.irlix_collectionpoems.repository.PoemRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PoemService {

    private final PoemRepository poemRepository;

    public List<Poem> getAllPoems() {
        return poemRepository.findAll();
    }

    public List<Poem> searchPoemsByTitle(String title) {
        return poemRepository.findByTitle(title);
    }

    public List<Poem> searchPoemsByAuthor(User author) {
        return poemRepository.findByAuthor(author);
    }

    public void deletePoem(Long poemId) {
        poemRepository.deleteById(poemId);
    }

    public Optional<Poem> getPoemById(Long poemId) {
        return poemRepository.findById(poemId);
    }
}
