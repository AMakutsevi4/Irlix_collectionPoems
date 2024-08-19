package ru.irlix_collectionpoems.model;

import lombok.Data;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

@Data
/* Класс является сущностью JPA */
@Entity
/* Имя таблицы в базе данных и добавляет уникальные ограничения на поля username и email */
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"username"}),
        @UniqueConstraint(columnNames = {"email"})
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String name;
    private String username;
    private String email;
    private String password;

    /* Связь "многие ко многим" между пользователями и ролями */
    @ManyToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    /* Параметры для связывающей таблицы между пользователями и ролями */
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id"))
    private Set<Role> roles = new HashSet<>();

}
