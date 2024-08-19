package ru.irlix_collectionpoems.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.irlix_collectionpoems.model.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /* Поиск пользователя по имени пользователя или email */
    Optional<User> findByUsernameOrEmail(String username, String email);
    /* Поиск пользователя по имени пользователя */
    Optional<User> findByUsername(String username);
    /* Проверка, существует ли пользователь с данным именем пользователя */
    Boolean existsByUsername(String username);
    /* Проверка, существует ли пользователь с данным email */
    Boolean existsByEmail(String email);
}