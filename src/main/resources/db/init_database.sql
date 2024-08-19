CREATE TABLE roles
(
    id   SERIAL PRIMARY KEY,
    name VARCHAR(60) NOT NULL
);

CREATE TABLE users
(
    id       SERIAL PRIMARY KEY,
    name     VARCHAR(255),
    username VARCHAR(255) NOT NULL UNIQUE,
    email    VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255),
    UNIQUE (username),
    UNIQUE (email)
);

CREATE TABLE user_roles
(
    user_id BIGINT,
    role_id BIGINT,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users (id),
    FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE TABLE poems
(
    id        SERIAL PRIMARY KEY,
    title     VARCHAR(255) NOT NULL,
    content   TEXT         NOT NULL,
    author_id BIGINT       NOT NULL,
    FOREIGN KEY (author_id) REFERENCES users (id)
);

INSERT INTO roles (name)
VALUES ('ADMIN');
INSERT INTO roles (name)
VALUES ('POET');

INSERT INTO users (name, username, email, password)
VALUES ('Александр М', 'alexAlone', 'alexAlone@mail.ru', 'пароль1');
INSERT INTO users (name, username, email, password)
VALUES ('Мария М', 'mariaFirst', 'mariaFirst@mail.com', 'пароль2');

INSERT INTO user_roles (user_id, role_id)
VALUES (1, 1);

INSERT INTO user_roles (user_id, role_id)
VALUES (2, 2);

INSERT INTO poems (title, content, author_id)
VALUES ('Вечер', 'Данный стих очень красивый на тему Вечер.', 1);

INSERT INTO poems (title, content, author_id)
VALUES ('Утро', 'Утренний стих для поднятия настроения.', 1);

INSERT INTO poems (title, content, author_id)
VALUES ('Зима', 'Зимний согревающий стих для поднятия настроения.', 2);

INSERT INTO poems (title, content, author_id)
VALUES ('Лето', 'Солнечный стих для лучшего загара и выработки витамина Д.', 2);
