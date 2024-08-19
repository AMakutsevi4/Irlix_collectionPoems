package ru.irlix_collectionpoems.security;

import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtTokenProvider {

    /* Получение секрета из конфигурационного файла */
    /* Это уникальный ключ, который знает только мой сервер.
    Он показывает, что токен действительно создан моим сервером и не был изменен кем-то другим.
    При работе с клиент-сервером всегда проверяется секрет для дополнительной безопасности*/
    @Value("${app.jwt-secret}")
    private String jwtSecret;

    /* Получение времени жизни токена из конфигурационного файла 7 дней */
    @Value("${app.jwt-expiration-milliseconds}")
    private int jwtExpirationInMs;

    /* Метод для генерации нового JWT на основе информации об аутентификации */
    public String generateToken(Authentication authentication) {
        /* Получение имени пользователя из объекта аутентификации */
        String username = authentication.getName();
        /* Создание текущего времени */
        var now = new Date();
        /* Создание даты окончания действия токена */
        var expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        /* Построение и создание JWT */
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .signWith(SignatureAlgorithm.HS512, jwtSecret)
                .compact();
    }

}