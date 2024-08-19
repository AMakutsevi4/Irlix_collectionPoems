package ru.irlix_collectionpoems.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import ru.irlix_collectionpoems.repository.UserRepository;

import java.util.Set;
import java.util.stream.Collectors;

/*
* Логика для загрузки сведений о пользователе по имени или электронной почте из базы данных.
*
Создаём CustomUserDetailsService, который реализует интерфейс UserDetailsService
(встроенный интерфейс Spring security) и предоставляет реализацию для метода loadUserByUername():
*
* Spring Security использует интерфейс UserDetailsService, который содержит метод loadUserByUsername(строковое имя пользователя)
 для поиска пользовательских данных для данного имени пользователя.
*
* Интерфейс UserDetails представляет собой объект аутентифицированного пользователя,
а Spring Security предоставляет готовую реализацию org.springframework.security.core.userdetails.User.*/
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        /* Поиск пользователя по имени или email */
        var user = userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found with username or email: " + usernameOrEmail));

        /* Получение ролей пользователя и преобразование их в SimpleGrantedAuthority */
        Set<GrantedAuthority> authorities = user
                .getRoles()
                .stream()
                .map((role) -> new SimpleGrantedAuthority(role.getName())).collect(Collectors.toSet());

        /* Возвращение объекта UserDetails, который будет использоваться для аутентификации */
        return new org.springframework.security.core.userdetails.User(user.getEmail(),
                user.getPassword(),
                authorities);
    }
}
