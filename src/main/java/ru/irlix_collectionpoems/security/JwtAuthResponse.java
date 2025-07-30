package ru.irlix_collectionpoems.security;

import lombok.Data;

/* Класс используется для представления ответа на запрос аутентификации с использованием JSON Web Token (JWT).
Он содержит информацию, которая будет возвращена клиенту после успешного входа в систему*/
@Data
public class JwtAuthResponse {
    private String accessToken;
    private String tokenType = "Bearer";

    public JwtAuthResponse(String accessToken) {
        this.accessToken = accessToken;
    }
}
