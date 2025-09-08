package ru.irlix_collectionpoems;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;


@SpringBootApplication
public class IrlixCollectionPoemsApplication {

    public static void main(String[] args) {
        SpringApplication.run(IrlixCollectionPoemsApplication.class, args);

        String passwordAdmin = "passwordAdmin";
        String passwordUser = "passwordUser";
        String passwordPoet = "passwordPoet";

        System.out.println("Хешированный пароль: " + encoder(passwordAdmin));
        System.out.println("Хешированный пароль: " + encoder(passwordUser));
        System.out.println("Хешированный пароль: " + encoder(passwordPoet));

    }

    public static String encoder(String password) {
        var encoder = new BCryptPasswordEncoder();
        return encoder.encode(password);
    }
1. Конфигурация (application.yml)

```yaml
app:
  scanner:
    # Путь к сканируемой папке (можно задать через переменную окружения SCAN_FOLDER)
    folder-path: ${SCAN_FOLDER:./scan-folder}
    
    # Расписание сканирования в формате cron (каждые 10 секунд)
    cron: "*/10 * * * * *"
    
    # Включить/выключить сканер
    enabled: true
    
    # Паттерн для фильтрации файлов
    file-pattern: "*.*"
    
    # Количество попыток обработки файла при ошибках
    max-retries: 3
    
    # Задержка между попытками в миллисекундах
    retry-delay: 1000

# Настройки логирования
logging:
  level:
    com.example.scanner: INFO
```

2. Класс конфигурационных свойств

```java
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Конфигурационные свойства для сканера папок
 * Префикс 'app.scanner' означает, что свойства берутся из application.yml
 * под секцией app.scanner
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.scanner")
public class ScannerProperties {
    
    /**
     * Путь к папке для сканирования
     * Аннотация @NotBlank гарантирует, что значение не будет пустым
     */
    @NotBlank
    private String folderPath = "./scan-folder";
    
    /**
     * Cron-выражение для расписания сканирования
     * Например: "0 * * * * *" - каждую минуту
     */
    @NotBlank
    private String cron = "*/10 * * * * *";
    
    /**
     * Включен ли сканер
     */
    private boolean enabled = true;
    
    /**
     * Паттерн для фильтрации файлов (например: "*.txt", "*.csv")
     */
    private String filePattern = "*.*";
    
    /**
     * Максимальное количество попыток обработки файла при ошибках
     */
    private int maxRetries = 3;
    
    /**
     * Задержка между попытками в миллисекундах
     */
    private long retryDelay = 1000;
}
```

3. Интерфейс обработчика файлов

```java
import java.io.IOException;
import java.nio.file.Path;

/**
 * Интерфейс для обработки файлов
 * Позволяет легко менять логику обработки (удаление, перемещение, обработка и т.д.)
 */
public interface FileHandler {
    
    /**
     * Обработать файл
     * @param filePath путь к файлу для обработки
     * @throws IOException если произошла ошибка при обработке
     */
    void handleFile(Path filePath) throws IOException;
}
```

4. Реализация обработчика для удаления файлов

```java
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Реализация FileHandler для удаления файлов
 * Компонент Spring - автоматически создается и управляется контейнером
 */
@Slf4j
@Component
public class FileDeletionHandler implements FileHandler {
    
    /**
     * Метод для обработки (удаления) файла
     * @param filePath путь к файлу, который нужно удалить
     * @throws IOException если не удалось удалить файл
     */
    @Override
    public void handleFile(Path filePath) throws IOException {
        // Получаем размер файла перед удалением для логирования
        long fileSize = Files.size(filePath);
        
        // Удаляем файл
        Files.delete(filePath);
        
        // Логируем успешное удаление
        log.info("Файл удален: {} (размер: {} байт)", 
                filePath.getFileName(), fileSize);
    }
}
```

5. Основной сервис сканера

```java
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Сервис для сканирования папки и обработки файлов
 * Использует Spring Scheduler для периодического выполнения
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FolderScannerService {
    
    private final ScannerProperties properties;
    private final FileHandler fileHandler;
    
    /**
     * Метод сканирования папки, выполняется по расписанию
     * Аннотация @Scheduled указывает, когда метод должен выполняться
     * fixedRate - фиксированный интервал между запусками
     * cron - более гибкое расписание (в данном случае используется из конфига)
     */
    @Scheduled(cron = "${app.scanner.cron}")
    public void scanFolder() {
        // Проверяем, включен ли сканер
        if (!properties.isEnabled()) {
            log.debug("Сканер отключен в конфигурации");
            return;
        }
        
        try {
            // Создаем путь к папке
            Path folderPath = Paths.get(properties.getFolderPath());
            
            // Создаем папку, если она не существует
            if (!Files.exists(folderPath)) {
                Files.createDirectories(folderPath);
                log.info("Создана папка для сканирования: {}", folderPath);
                return;
            }
            
            // Проверяем, что путь ведет к папке, а не к файлу
            if (!Files.isDirectory(folderPath)) {
                log.error("Указанный путь не является папкой: {}", properties.getFolderPath());
                return;
            }
            
            // Сканируем и обрабатываем файлы
            processFilesInFolder(folderPath);
            
        } catch (Exception e) {
            log.error("Ошибка при сканировании папки: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Обрабатывает все файлы в указанной папке
     * @param folderPath путь к папке для обработки
     */
    private void processFilesInFolder(Path folderPath) throws IOException {
        // Счетчики для статистики
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        log.debug("Начинаем сканирование папки: {}", folderPath);
        
        // Используем try-with-resources для автоматического закрытия потоков
        try (var files = Files.list(folderPath)) {
            files
                // Фильтруем только обычные файлы (не папки)
                .filter(Files::isRegularFile)
                // Фильтруем по паттерну имени файла
                .filter(this::matchesFilePattern)
                // Обрабатываем каждый файл
                .forEach(file -> {
                    try {
                        // Обрабатываем файл с повторными попытками при ошибках
                        processFileWithRetry(file);
                        processedCount.incrementAndGet();
                    } catch (Exception e) {
                        log.error("Не удалось обработать файл: {}", file.getFileName(), e);
                        errorCount.incrementAndGet();
                    }
                });
        }
        
        // Логируем результаты сканирования
        if (processedCount.get() > 0 || errorCount.get() > 0) {
            log.info("Сканирование завершено. Обработано: {}, Ошибок: {}", 
                    processedCount, errorCount);
        }
    }
    
    /**
     * Проверяет, соответствует ли файл заданному паттерну
     * @param file путь к файлу для проверки
     * @return true если файл соответствует паттерну
     */
    private boolean matchesFilePattern(Path file) {
        String fileName = file.getFileName().toString();
        String pattern = properties.getFilePattern();
        
        // Простая проверка паттерна
        if ("*.*".equals(pattern)) {
            return true; // Принимаем все файлы
        }
        
        // Более сложная проверка для других паттернов
        return fileName.matches(pattern.replace(".", "\\.").replace("*", ".*"));
    }
    
    /**
     * Обрабатывает файл с несколькими попытками при ошибках
     * @param filePath путь к файлу для обработки
     */
    private void processFileWithRetry(Path filePath) {
        int attempt = 0;
        int maxRetries = properties.getMaxRetries();
        
        while (attempt <= maxRetries) {
            try {
                fileHandler.handleFile(filePath);
                return; // Успешно обработано, выходим из метода
                
            } catch (Exception e) {
                attempt++;
                
                if (attempt > maxRetries) {
                    // Превышено максимальное количество попыток
                    throw new RuntimeException(
                        "Не удалось обработать файл после " + maxRetries + " попыток: " + 
                        filePath.getFileName(), e);
                }
                
                // Логируем предупреждение и ждем перед следующей попыткой
                log.warn("Попытка {} из {} не удалась для файла: {}", 
                        attempt, maxRetries, filePath.getFileName());
                
                try {
                    Thread.sleep(properties.getRetryDelay());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Прервано во время ожидания повтора", ie);
                }
            }
        }
    }
}
```

6. Конфигурация Spring приложения

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Главный класс Spring Boot приложения
 * Аннотация @SpringBootApplication включает автоконфигурацию Spring Boot
 * Аннотация @EnableScheduling включает поддержку планировщика задач
 * Аннотация @EnableConfigurationProperties регистрирует наши свойства
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(ScannerProperties.class)
public class FolderScannerApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(FolderScannerApplication.class, args);
    }
}
```
}



1. Конфигурация (application.yml)

```yaml
app:
  scanner:
    # Мониторим рабочий стол пользователя
    folder-path: ${USER_HOME}/Desktop
    
    # Имя файла для отслеживания (без расширения)
    target-filename: book-nice
    
    # Расширение файла (можно оставить пустым для любого расширения)
    target-extension: .txt
    
    # Расписание сканирования (каждые 5 секунд)
    cron: "*/5 * * * * *"
    
    # Включить/выключить сканер
    enabled: true
    
    # Действие с файлом: DELETE, MOVE, LOG
    action: DELETE
    
    # Папка для перемещения (если action = MOVE)
    move-folder: ${USER_HOME}/Desktop/processed
    
    # Количество попыток обработки
    max-retries: 3
    
    # Задержка между попытками
    retry-delay: 1000

logging:
  level:
    com.example.scanner: INFO
```

2. Класс конфигурационных свойств

```java
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Конфигурационные свойства для сканера рабочего стола
 * Специализирован для отслеживания конкретного файла на рабочем столе
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.scanner")
public class DesktopScannerProperties {
    
    /**
     * Путь к рабочему столу пользователя
     * По умолчанию используется системная переменная USER_HOME
     */
    @NotBlank
    private String folderPath = System.getProperty("user.home") + "/Desktop";
    
    /**
     * Имя целевого файла без расширения
     */
    @NotBlank
    private String targetFilename = "book-nice";
    
    /**
     * Расширение целевого файла (например: .txt, .pdf)
     * Если пустое - будет искать файл с любым расширением
     */
    private String targetExtension = ".txt";
    
    /**
     * Cron-выражение для расписания сканирования
     * Каждые 5 секунд для быстрого реагирования
     */
    @NotBlank
    private String cron = "*/5 * * * * *";
    
    /**
     * Включен ли сканер
     */
    private boolean enabled = true;
    
    /**
     * Действие с найденным файлом
     * DELETE - удалить, MOVE - переместить, LOG - только залогировать
     */
    private String action = "DELETE";
    
    /**
     * Папка для перемещения файлов (если action = MOVE)
     */
    private String moveFolder = System.getProperty("user.home") + "/Desktop/processed";
    
    /**
     * Максимальное количество попыток обработки
     */
    private int maxRetries = 3;
    
    /**
     * Задержка между попытками в миллисекундах
     */
    private long retryDelay = 1000;
}
```

3. Перечисление действий с файлом

```java
/**
 * Enum для типов действий с найденным файлом
 * Обеспечивает типобезопасность при выборе действия
 */
public enum FileAction {
    /**
     * Удалить файл после обнаружения
     */
    DELETE,
    
    /**
     * Переместить файл в указанную папку
     */
    MOVE,
    
    /**
     * Только залогировать факт обнаружения (файл остается на месте)
     */
    LOG,
    
    /**
     * Архивировать файл перед удалением/перемещением
     */
    ARCHIVE
}
```

4. Интерфейс обработчика файлов

```java
import java.io.IOException;
import java.nio.file.Path;

/**
 * Интерфейс для обработки конкретного файла на рабочем столе
 * Специализирован для работы с файлом "book-nice"
 */
public interface DesktopFileHandler {
    
    /**
     * Обработать целевой файл на рабочем столе
     * @param filePath путь к файлу "book-nice"
     * @throws IOException если произошла ошибка при обработке
     */
    void handleDesktopFile(Path filePath) throws IOException;
    
    /**
     * Проверить, является ли файл целевым (book-nice)
     * @param filePath путь к проверяемому файлу
     * @return true если это файл book-nice
     */
    boolean isTargetFile(Path filePath);
}
```

5. Реализация обработчика для файла book-nice

```java
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;

/**
 * Обработчик конкретного файла book-nice на рабочем столе
 * Поддерживает различные действия: удаление, перемещение, логирование
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookNiceFileHandler implements DesktopFileHandler {
    
    private final DesktopScannerProperties properties;
    
    /**
     * Проверяет, является ли файл целевым (book-nice)
     * Сравнивает имя файла без расширения с targetFilename
     */
    @Override
    public boolean isTargetFile(Path filePath) {
        String filename = filePath.getFileName().toString();
        
        // Извлекаем имя файла без расширения
        String nameWithoutExtension = getFileNameWithoutExtension(filename);
        
        // Проверяем совпадение с целевым именем
        boolean nameMatches = nameWithoutExtension.equalsIgnoreCase(properties.getTargetFilename());
        
        // Если указано конкретное расширение - проверяем и его
        boolean extensionMatches = properties.getTargetExtension().isEmpty() ||
                filename.toLowerCase().endsWith(properties.getTargetExtension().toLowerCase());
        
        return nameMatches && extensionMatches;
    }
    
    /**
     * Обрабатывает файл book-nice в зависимости от настроенного действия
     */
    @Override
    public void handleDesktopFile(Path filePath) throws IOException {
        log.info("Обнаружен целевой файл: {}", filePath.getFileName());
        
        // Определяем действие на основе конфигурации
        String action = properties.getAction().toUpperCase();
        
        switch (action) {
            case "DELETE":
                deleteFile(filePath);
                break;
                
            case "MOVE":
                moveFile(filePath);
                break;
                
            case "LOG":
                logFile(filePath);
                break;
                
            default:
                log.warn("Неизвестное действие: {}. Файл останется без изменений", action);
        }
    }
    
    /**
     * Удаляет файл book-nice
     */
    private void deleteFile(Path filePath) throws IOException {
        long fileSize = Files.size(filePath);
        Files.delete(filePath);
        log.info("Файл удален: {} (размер: {} байт)", filePath.getFileName(), fileSize);
    }
    
    /**
     * Перемещает файл book-nice в папку processed
     */
    private void moveFile(Path filePath) throws IOException {
        // Создаем папку для перемещения, если она не существует
        Path moveFolder = Paths.get(properties.getMoveFolder());
        if (!Files.exists(moveFolder)) {
            Files.createDirectories(moveFolder);
            log.info("Создана папка для перемещения: {}", moveFolder);
        }
        
        // Формируем путь для перемещения
        Path destination = moveFolder.resolve(filePath.getFileName());
        
        // Перемещаем файл
        Files.move(filePath, destination, StandardCopyOption.REPLACE_EXISTING);
        
        log.info("Файл перемещен: {} -> {}", filePath.getFileName(), destination);
    }
    
    /**
     * Только логирует информацию о файле book-nice (файл остается на месте)
     */
    private void logFile(Path filePath) throws IOException {
        long fileSize = Files.size(filePath);
        String fileInfo = String.format("Файл: %s, Размер: %d байт, Путь: %s",
                filePath.getFileName(), fileSize, filePath.toAbsolutePath());
        
        log.info("Обнаружен файл (логирование): {}", fileInfo);
    }
    
    /**
     * Извлекает имя файла без расширения
     */
    private String getFileNameWithoutExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) {
            return filename.substring(0, dotIndex);
        }
        return filename;
    }
}
```

6. Основной сервис сканера рабочего стола

```java
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Специализированный сервис для мониторинга рабочего стола
 * и обнаружения файла с названием "book-nice"
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DesktopScannerService {
    
    private final DesktopScannerProperties properties;
    private final BookNiceFileHandler fileHandler;
    
    // Флаг для отслеживания первого обнаружения файла
    private final AtomicBoolean firstDetection = new AtomicBoolean(false);
    
    /**
     * Метод сканирования рабочего стола, выполняется каждые 5 секунд
     * Оптимизирован для быстрого реагирования на появление файла
     */
    @Scheduled(cron = "${app.scanner.cron}")
    public void scanDesktop() {
        if (!properties.isEnabled()) {
            log.debug("Сканер рабочего стола отключен");
            return;
        }
        
        try {
            Path desktopPath = Paths.get(properties.getFolderPath());
            
            // Проверяем существование рабочего стола
            if (!Files.exists(desktopPath)) {
                log.warn("Рабочий стол не найден: {}", desktopPath);
                return;
            }
            
            if (!Files.isDirectory(desktopPath)) {
                log.error("Путь не является папкой: {}", desktopPath);
                return;
            }
            
            // Сканируем рабочий стол на наличие файла book-nice
            scanForTargetFile(desktopPath);
            
        } catch (Exception e) {
            log.error("Ошибка при сканировании рабочего стола: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Сканирует папку на наличие целевого файла book-nice
     */
    private void scanForTargetFile(Path folderPath) throws IOException {
        log.debug("Сканируем рабочий стол для поиска файла '{}'", 
                 properties.getTargetFilename() + properties.getTargetExtension());
        
        try (var files = Files.list(folderPath)) {
            boolean fileFound = files
                .filter(Files::isRegularFile) // Только файлы, не папки
                .filter(fileHandler::isTargetFile) // Проверяем, это наш файл
                .findFirst() // Нам нужен только первый подходящий файл
                .map(file -> {
                    try {
                        // Обрабатываем файл с повторными попытками
                        processTargetFileWithRetry(file);
                        return true; // Файл найден и обработан
                    } catch (Exception e) {
                        log.error("Ошибка обработки файла: {}", file.getFileName(), e);
                        return false;
                    }
                })
                .orElse(false); // Если файл не найден - возвращаем false
            
            // Логируем первое обнаружение файла
            if (fileFound && firstDetection.compareAndSet(false, true)) {
                log.info("ПЕРВОЕ ОБНАРУЖЕНИЕ: Файл '{}' найден на рабочем столе!", 
                        properties.getTargetFilename());
            }
        }
    }
    
    /**
     * Обрабатывает целевой файл с несколькими попытками
     */
    private void processTargetFileWithRetry(Path filePath) {
        int attempt = 0;
        int maxRetries = properties.getMaxRetries();
        
        while (attempt <= maxRetries) {
            try {
                fileHandler.handleDesktopFile(filePath);
                log.debug("Файл успешно обработан с попытки {}", attempt + 1);
                return;
                
            } catch (Exception e) {
                attempt++;
                
                if (attempt > maxRetries) {
                    throw new RuntimeException(String.format(
                        "Не удалось обработать файл '%s' после %d попыток",
                        filePath.getFileName(), maxRetries), e);
                }
                
                log.warn("Попытка {}/{} не удалась для файла: {}",
                        attempt, maxRetries, filePath.getFileName());
                
                try {
                    Thread.sleep(properties.getRetryDelay());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Прервано во время ожидания повтора", ie);
                }
            }
        }
    }
}
```

7. Главный класс приложения

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Приложение для мониторинга рабочего стола и обработки файла book-nice
 * Автоматически сканирует рабочий стол каждые 5 секунд
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(DesktopScannerProperties.class)
public class DesktopScannerApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(DesktopScannerApplication.class, args);
        System.out.println("🚀 Сканер рабочего стола запущен!");
        System.out.println("📁 Мониторим: " + System.getProperty("user.home") + "/Desktop");
        System.out.println("🔍 Ищем файл: book-nice");
        System.out.println("⏰ Частота проверки: каждые 5 секунд");
    }
}
```

Как использовать:

1. Создайте файл на рабочем столе с названием book-nice.txt
2. Запустите приложение - оно автоматически обнаружит и обработает файл
3. Настройте действие в application.yml (DELETE, MOVE или LOG)
4. Файл будет обработан согласно настройкам в течение 5 секунд