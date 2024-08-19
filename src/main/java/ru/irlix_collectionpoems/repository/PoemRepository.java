package ru.irlix_collectionpoems.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.irlix_collectionpoems.model.Poem;
import ru.irlix_collectionpoems.model.User;

import java.util.List;

public interface PoemRepository extends JpaRepository<Poem, Long> {

    List<Poem> findByTitle(String title);

    List<Poem> findByAuthor(User author);
}