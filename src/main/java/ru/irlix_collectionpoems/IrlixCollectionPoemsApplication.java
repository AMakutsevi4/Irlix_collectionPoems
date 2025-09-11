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
Ок 👍 тогда давай сделаем так: я подготовлю тебе полностью готовую реализацию, которая:
берёт шаблон Excel из resources/templates/
подставляет туда данные по твоим кредитным картам
автоматически скачивает отчёт при нажатии ОК в форме
учитывает, что в шаблоне может быть картинка и заголовки колонок
🔹 Шаг 1. Подготовка шаблона Excel
Создай файл CreditCardsReportTemplate.xlsx и положи в src/main/resources/templates/.
В шаблоне сделай так:
Вставь картинку (например, логотип компании).
На первой строке напиши названия колонок, например:
| Номер карты (number) | Баланс (balance) | Заблокирована (blocked) |
👉 Названия в скобках (number, balance, blocked) должны совпадать с полями сущности CreditCard.
🔹 Шаг 2. Сервис генерации отчёта
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ResourceLoader resourceLoader;
    private final DataControllerEntityRepository repo;

    /**
     * Генерация Excel-отчёта по шаблону
     */
    public byte[] generateCreditCardReport() {
        try (InputStream templateStream = loadTemplate("CreditCardsReportTemplate.xlsx");
             Workbook workbook = new XSSFWorkbook(templateStream);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // Берём первый лист
            Sheet sheet = workbook.getSheetAt(0);

            // Загружаем все карты из БД
            Iterable<CreditCard> creditCards = repo.findAll(CreditCard[].class);

            int rowIndex = 1; // начинаем со 2-й строки, первая занята заголовками

            for (CreditCard card : creditCards) {
                Row row = sheet.createRow(rowIndex++);

                // Колонка 1: номер карты
                row.createCell(0).setCellValue(card.getNumber());

                // Колонка 2: баланс
                if (card.getBalance() != null) {
                    row.createCell(1).setCellValue(card.getBalance().doubleValue());
                } else {
                    row.createCell(1).setCellValue(0);
                }

                // Колонка 3: статус блокировки
                row.createCell(2).setCellValue(Boolean.TRUE.equals(card.getBlocked()) ? "Да" : "Нет");
            }

            workbook.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Ошибка при формировании отчёта: " + e.getMessage(), e);
        }
    }

    private InputStream loadTemplate(String templateName) throws IOException {
        return resourceLoader.getResource("classpath:templates/" + templateName).getInputStream();
    }
}
🔹 Шаг 3. Операция (вызов отчёта из формы)
@Component
@RequiredArgsConstructor
public class CreditCardReportOperation extends AutoInitOperation<CreditCard> {

    private final ReportService reportService;

    @Override
    public OperationResponse execute(Request rp) {
        byte[] file = reportService.generateCreditCardReport();

        // Скачивание файла пользователю
        return OperationResponse.download("Отчет_по_картам.xlsx", file);
    }
}
🔹 Как это работает
Ты создаёшь шаблон Excel с колонками:
number (Номер карты)
balance (Баланс)
blocked (Заблокирована)
Когда пользователь жмёт ОК в форме → срабатывает метод execute.
Сервис ReportService подгружает шаблон, добавляет в него строки с данными из БД.
Готовый отчёт скачивается как Отчет_по_картам.xlsx.
📌 Важное:
Шаблон Excel можно стилизовать (цвета, шрифты, лого). Данные будут вставляться ниже первой строки.
Если нужно, можно добавить дополнительные колонки (например, дата создания карты, владелец).

