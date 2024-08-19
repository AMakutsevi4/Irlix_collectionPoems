package ru.irlix_collectionpoems.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/*Аннотация, указывающая, что этот класс является конфигурационным классом Spring*/
@Configuration
/*Включает поддержку аннотаций безопасности на уровне методов*/
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    /* UserDetailsService — это интерфейс в Spring Security, который используется для загрузки данных о пользователе.
     * Когда пользователь пытается аутентифицироваться в системе (например, вводит свои имя пользователя и пароль),
     * Spring Security использует UserDetailsService для загрузки данных о пользователе из базы данных. */
    private final UserDetailsService userDetailsService;

    /*Определяет BCryptPasswordEncoder как способ шифрования паролей*/
    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    /* Это основной компонент, который проверяет учетные данные пользователя
    и решает, может ли пользователь получить доступ к приложению */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /* Настройка фильтра безопасности для обработки запросов*/
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        /*Отключение защиты CSRF (необходимо, если используем JWT или токены)*/
        http.csrf().disable()
                .authorizeHttpRequests((authorize) ->
                        authorize
                                /* Разрешить доступ к страницам авторизации и регистрации */
                                .requestMatchers("/api/auth/signin", "/api/auth/signup").permitAll()
                                /* Разрешить доступ к административным функциям только пользователям с ролью ADMIN */
                                .requestMatchers("/api/auth/admin/**").hasRole("ADMIN")
                                /* Разрешить POST запросы на добавление стихов пользователям с ролями POET или ADMIN */
                                .requestMatchers(HttpMethod.POST, "/api/poems").hasAnyRole("POET", "ADMIN")
                                /* Разрешить PUT запросы на изменение стихов пользователям с ролями POET или ADMIN */
                                .requestMatchers(HttpMethod.PUT, "/api/poems/**").hasAnyRole("POET", "ADMIN")
                                /* Разрешить DELETE запросы на удаление стихов пользователям с ролями POET или ADMIN */
                                .requestMatchers(HttpMethod.DELETE, "/api/poems/**").hasAnyRole("POET", "ADMIN")
                                /* Все остальные запросы требуют аутентификации */
                                .anyRequest().authenticated()
                )
                .userDetailsService(userDetailsService)
                .httpBasic(Customizer.withDefaults());
        // Построение и возврат конфигурации безопасности
        return http.build();
    }
}