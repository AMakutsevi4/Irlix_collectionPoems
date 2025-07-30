package ru.irlix_collectionpoems.dto;

import lombok.Data;

/*Этот класс представляет данные, которые отправляются при авторизации пользователя.*/
@Data
public class LoginDto {
    private String usernameOrEmail;
    private String password;
}
