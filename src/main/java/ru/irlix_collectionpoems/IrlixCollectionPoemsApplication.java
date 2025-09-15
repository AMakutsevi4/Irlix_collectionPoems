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














🔹 Шаблон CreditCardsReport.xlsx.ftl
Файл кладём в resources/templates/.
Внутри можно писать прямо Excel-таблицу с подстановками:
Номер карты	Владелец	Баланс	Валюта	Заблокирована
<#list cards as card>				
${card.number}	${card.owner}	${card.balance}	${card.currency}	${card.isBlocked?string("Да","Нет")}
</#list>				
👉 То есть у тебя одна строка — это шаблон для FreeMarker, он повторится для всех карт.
🔹 Сервис
@Service
@RequiredArgsConstructor
public class CreditCardReportService {

    private final CreditCardRepository repo; // твой репозиторий
    private final ResourceLoader resourceLoader;

    public byte[] generateReport(User currentUser) {
        try {
            // получаем список карт из базы
            List<CreditCard> cards = repo.findAll();

            // параметры для шаблона
            Map<String, Object> params = new HashMap<>();
            params.put("cards", cards);
            params.put("currentDate", LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
            params.put("currentUser", currentUser.getFullName());

            // генерируем Excel через FreeMarker
            return FreemarkerWrapper.generateXlsx(
                    loadTemplate("CreditCardsReport.xlsx.ftl"),
                    params
            );
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при генерации Excel отчета", e);
        }
    }

    private InputStream loadTemplate(String templateName) throws IOException {
        return resourceLoader.getResource("classpath:templates/" + templateName).getInputStream();
    }
}
🔹 Вызов из операции
@Component
@RequiredArgsConstructor
public class CreditCardReportOperation extends AutoInitOperation<Void> {

    private final CreditCardReportService reportService;

    @Override
    public OperationResponse execute(Request rp) {
        User currentUser = getCurrentUser(); // возьми из сессии или контекста
        byte[] report = reportService.generateReport(currentUser);
        return OperationResponse.download("CreditCardsReport.xlsx", report);
    }
}
🔹 Резюме
Ты создаёшь шаблон Excel (CreditCardsReport.xlsx.ftl), кладёшь его в resources/templates/.
В шаблоне используешь ${card.number}, ${card.balance}, ${currentDate}, ${currentUser} и т.д.
Сервис CreditCardReportService подставляет данные из БД.
При нажатии кнопки «ОК» на форме пользователь скачивает отчёт.
Хочешь, я подготовлю готовый Excel-шаблон с FreeMarker-разметкой под твою сущность CreditCard, чтобы тебе осталось только скопировать и положить в resources/templates?
Вы сказали:
Нет покажи содержимое шаблона 
ChatGPT сказал:
Ниже пример содержимого FreeMarker-шаблона Excel для вашей задачи.
Файл нужно сохранить как, например,
src/main/resources/templates/CreditCardsReport.xlsx.ftl.
⚠️ Важно: это не обычный .xlsx, а FreeMarker-шаблон.
Его можно редактировать в Excel (как макет), а в нужных ячейках прописать ${...} — при генерации FreeMarker подставит значения.
🗂 Содержимое CreditCardsReport.xlsx.ftl
Номер карты	Владелец	Баланс	Валюта	Заблокирована
<#list cards as card>				
${card.number}	${card.owner}	${card.balance?string["0.00"]}	${card.currency}	${card.isBlocked?string("Да","Нет")}
</#list>				
Как редактировать шаблон в Excel
Создай новый файл в Excel.
Первая строка — заголовки: Номер карты, Владелец, Баланс, Валюта, Заблокирована.
Со второй строки вместо данных вставь плейсхолдеры:
${card.number}    ${card.owner}    ${card.balance?string["0.00"]}    ${card.currency}    ${card.isBlocked?string("Да","Нет")}
Каждое выражение в своей ячейке.
Чтобы повторять строку для каждой карты, оберни диапазон строк директивами:
<#list cards as card>
…строка с плейсхолдерами…
</#list>
Сохрани файл как обычный .xlsx, но добавь суффикс .ftl
(например, CreditCardsReport.xlsx.ftl).
Параметры, которые будет подставлять сервис
cards — List<CreditCard> (каждый объект имеет number, owner, balance, currency, isBlocked).
currentDate — ${currentDate} (если захочешь вывести дату формирования отчёта — можно добавить в отдельную ячейку, например в заголовке: Отчёт от ${currentDate}).
currentUser — ${currentUser} (для подписи, тоже можно вставить в отдельную ячейку).
Мини-пример верхней части файла
Отчёт по картам на ${currentDate}				
Номер карты	Владелец	Баланс	Валюта	Заблокирована
<#list cards as card>				
${card.number}	${card.owner}	${card.balance?string["0.00"]}	${card.currency}	${card.isBlocked?string("Да","Нет")}
</#list>				
💡 Теперь, когда сервис подставит cards, FreeMarker сгенерирует готовый .xlsx с данными из вашей базы.












1️⃣ Шаблон
В src/main/resources/templates/CreditCardsReport.xlsx
Первая строка — заголовок, стили, картинка — всё оформляешь прямо в Excel.
Начиная со 2-й строки оставь пустую «образцовую» строку, в которую будем копировать данные.
2️⃣ Сервис генерации
@Service
@RequiredArgsConstructor
public class CreditCardReportService {

    private final CreditCardRepository repo;          // ваш репозиторий
    private final ResourceLoader resourceLoader;      // чтобы достать шаблон

    public byte[] generateReport(User currentUser) {
        try (InputStream template =
                     resourceLoader.getResource("classpath:templates/CreditCardsReport.xlsx").getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(template);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // 1. Получаем данные из БД
            List<CreditCard> cards = repo.findAll();

            // 2. Берём первый лист
            XSSFSheet sheet = workbook.getSheetAt(0);

            // 3. Начинаем со второй строки (индекс 1, т.к. 0 – шапка)
            int rowIndex = 1;

            for (CreditCard card : cards) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(card.getNumber());
                row.createCell(1).setCellValue(card.getOwner());
                row.createCell(2).setCellValue(card.getBalance().doubleValue());
                row.createCell(3).setCellValue(card.getCurrency());
                row.createCell(4).setCellValue(card.getIsBlocked() ? "Да" : "Нет");
            }

            // 4. Доп. инфо о пользователе / дате
            sheet.createRow(rowIndex + 1)
                 .createCell(0)
                 .setCellValue("Отчёт сформирован: "
                               + currentUser.getFullName()
                               + " — "
                               + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));

            // 5. Пишем в поток
            workbook.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Ошибка при создании отчёта", e);
        }
    }
}
Здесь никаких сторонних обёрток:
– шаблон берём из resources/templates/CreditCardsReport.xlsx;
– наполняем данными напрямую через Apache POI;
– на выходе — готовый byte[].
3️⃣ Операция/контроллер
@Component
@RequiredArgsConstructor
public class CreditCardReportOperation extends AutoInitOperation<Void> {

    private final CreditCardReportService reportService;

    @Override
    public OperationResponse execute(Request rp) {
        User currentUser = getCurrentUser(); // возьми из контекста
        byte[] report = reportService.generateReport(currentUser);
        return OperationResponse.download("CreditCardsReport.xlsx", report);
    }
}
При нажатии кнопки ОК на форме этот код:
достаёт шаблон,
вписывает все карты из БД,
возвращает файл как скачиваемый.
Что изменилось по сравнению с «FreemarkerWrapper»
Никаких внешних зависимостей: только org.apache.poi:poi-ooxml и spring-core (для ResourceLoader).
Шаблон остаётся «живым» Excel-файлом: можно нарисовать логотип, задать шрифты, стили, формулы — всё сохранится.
Добавь зависимость в pom.xml, если ещё нет:
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
