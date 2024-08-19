package ru.irlix_collectionpoems.dto;

import lombok.Data;

/*Этот класс представляет данные, которые отправляются при регистрации пользователя.*/
@Data
public class SignUpDto {
    private String name;
    private String username;
    private String email;
    private String password;
}
