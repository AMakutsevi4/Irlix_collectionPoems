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
