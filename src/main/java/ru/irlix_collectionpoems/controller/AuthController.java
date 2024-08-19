package ru.irlix_collectionpoems.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import ru.irlix_collectionpoems.dto.LoginDto;
import ru.irlix_collectionpoems.dto.SignUpDto;
import ru.irlix_collectionpoems.model.Poem;
import ru.irlix_collectionpoems.model.Role;
import ru.irlix_collectionpoems.model.User;
import ru.irlix_collectionpoems.repository.RoleRepository;
import ru.irlix_collectionpoems.repository.UserRepository;
import ru.irlix_collectionpoems.security.JwtAuthResponse;
import ru.irlix_collectionpoems.security.JwtTokenProvider;
import ru.irlix_collectionpoems.service.PoemService;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    /* Внедрение AuthenticationManager для аутентификации */
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PoemService poemService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    /* Обработка POST-запроса для входа пользователя */
    @PostMapping("/signin")
    public ResponseEntity<JwtAuthResponse> authenticateUser(@RequestBody LoginDto loginDto) {
        /* Аутентификация пользователя с использованием usernameOrEmail и пароля */
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                loginDto.getUsernameOrEmail(), loginDto.getPassword()));

        /* Установка аутентифицированного пользователя в контекст безопасности */
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String token = tokenProvider.generateToken(authentication);
        return ResponseEntity.ok(new JwtAuthResponse(token));

    }

    /* Обработка POST-запроса для регистрации нового пользователя */
    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@RequestBody SignUpDto signUpDto) {

        /* Проверка, существует ли пользователь с данным именем пользователя в базе данных */
        if (userRepository.existsByUsername(signUpDto.getUsername())) {
            /* Возвращает ответ с ошибкой, если имя пользователя уже занято */
            return new ResponseEntity<>("Имя пользователя уже занято!", HttpStatus.BAD_REQUEST);
        }

        /* Проверка, существует ли пользователь с данным email в базе данных */
        if (userRepository.existsByEmail(signUpDto.getEmail())) {
            /* Возвращает ответ с ошибкой, если email уже занят */
            return new ResponseEntity<>("Email в системе уже существует!", HttpStatus.BAD_REQUEST);
        }

        /* Создание нового объекта User на основе данных, переданных в запросе */
        User user = new User();
        user.setName(signUpDto.getName());
        user.setUsername(signUpDto.getUsername());
        user.setEmail(signUpDto.getEmail());
        /* Шифрование пароля перед его сохранением в базе данных */
        user.setPassword(passwordEncoder.encode(signUpDto.getPassword()));

        /* Присваивание роли пользователю. В данном случае роль "ROLE_ADMIN" добавляется по умолчанию */
        Role roles = roleRepository.findByName("ROLE_ADMIN").get();

        user.setRoles(Collections.singleton(roles));

        userRepository.save(user);
        // Возвращает ответ с сообщением об успешной регистрации
        return new ResponseEntity<>("Пользователь успешно зарегистрировался", HttpStatus.OK);
    }

    /* Метод для изменения роли пользователя (для администратора) */
    @PutMapping("/admin/update-role")
    public ResponseEntity<?> updateUserRole(@RequestParam String username, @RequestParam String roleName) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Роль не найдена"));

        user.setRoles(Collections.singleton(role));
        userRepository.save(user);

        return new ResponseEntity<>("Роль пользователя успешно обновлена", HttpStatus.OK);
    }

    /* Метод для удаления пользователя (для администратора) */
    @DeleteMapping("/admin/delete-user")
    public ResponseEntity<String> deleteUser(@RequestParam String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("ьзователь не найден"));
        /* Удаление всех стихов пользователя перед удалением самого пользователя */
        List<Poem> poems = poemService.searchPoemsByAuthor(user);
        for (Poem poem : poems) {
            poemService.deletePoem(poem.getId());
        }
        userRepository.delete(user);

        return new ResponseEntity<>("Пользователь успешно удалён", HttpStatus.OK);
    }

    /* Метод для получения списка всех пользователей (для администратора) */
    @GetMapping("/admin/users")
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userRepository.findAll();
        return ResponseEntity.ok(users);
    }
}