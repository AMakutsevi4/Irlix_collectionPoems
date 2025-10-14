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
1️⃣ Excel-шаблон cards_template.xlsx

Создаём обычный файл в Excel и оформляем его.
Содержание (я показываю текстом, в реальности просто размести так ячейки):

┌──────────────────────────────┐
│   ОТЧЁТ ПО КРЕДИТНЫМ КАРТАМ  │  (ячейка A1, крупный шрифт, по центру, можно объединить A1:D1)
├──────────────────────────────┤
│ Дата выгрузки:    [B2 пусто] │  (подписи в A2, а B2 оставляем пустой)
│ Сформировал:      [B3 пусто] │  (подписи в A3, а B3 пустой)
├──────────────────────────────┤
│ № карты | Баланс | Статус | Лимит │  (строка 5 — заголовок таблицы, можно выделить жирным)


Строка 5 — начало таблицы карт.
Картинку/логотип можно вставить в верх (A1).

2️⃣ Сервис генерации отчёта
package com.example.report;

import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CreditCardReportService {

    private final DataControllerEntityRepository repo;

    /**
     * Генерация отчёта по шаблону cards_template.xlsx
     *
     * @param currentUserFullName имя пользователя из rp.getUser().getFullName()
     */
    public byte[] generateReport(String currentUserFullName) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ClassPathResource("templates/cards_template.xlsx").getInputStream()
        )) {
            XSSFSheet sheet = workbook.getSheetAt(0);

            // 1. Заполняем шапку: дата и пользователь
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            sheet.getRow(1).createCell(1).setCellValue(today);                 // B2
            sheet.getRow(2).createCell(1).setCellValue(currentUserFullName);   // B3

            // 2. Подготавливаем список карт
            List<CreditCard> cards = repo.findAll(CreditCard.class);

            int rowIndex = 5; // таблица начинается с 6-й строки (индекс 5)
            for (CreditCard card : cards) {
                XSSFRow row = sheet.createRow(rowIndex++);

                row.createCell(0).setCellValue(card.getNumber());          // № карты
                BigDecimal bal = card.getBalance() == null ? BigDecimal.ZERO : card.getBalance();
                row.createCell(1).setCellValue(bal.doubleValue());         // Баланс
                row.createCell(2).setCellValue(card.isBlocked() ? "Заблокирована" : "Активна");
                row.createCell(3).setCellValue(card.getLimit().doubleValue());
            }

            // 3. Возвращаем байты для скачивания
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            }
        } catch (IOException e) {
            throw new RuntimeException("Ошибка генерации Excel-отчёта", e);
        }
    }
}

3️⃣ Операция, вызываемая кнопкой формы
package com.example.operation;

import com.example.report.CreditCardReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DownloadCardsReportOperation extends AutoInitOperation<CreditCard> {

    private final CreditCardReportService reportService;

    @Override
    public OperationResponse execute(Request rp) {
        // Имя текущего пользователя из формы
        String currentUser = rp.getUser().getFullName();

        // Генерация отчёта
        byte[] report = reportService.generateReport(currentUser);

        // Отправляем файл на скачивание
        return OperationResponse.download(
                "credit_cards_report.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                report
        );
    }
}

Как это работает

Пользователь нажимает кнопку на форме.

Платформа вызывает execute().

Берём имя текущего пользователя rp.getUser().getFullName().

Открываем готовый Excel-шаблон:

в B2 пишем дату выгрузки,

в B3 — ФИО пользователя,

начиная со строки 6 заполняем таблицу данными карт.

Возвращаем OperationResponse.download → браузер скачивает готовый файл.
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>



🧾 Техническое задание / Backlog
Проект: Автоматизация процесса “Заказ карт с сайта банка”
Цель: Уменьшение ручного труда операционистов IBSO и Динамики при выпуске карт.
📘 Эпик 1. Автоматическая выгрузка реестров из IBSO
📍 Цель
Сделать так, чтобы система Динамика могла автоматически получать Excel-реестры из IBSO за выбранную дату, без участия операциониста.
🔹 Задача 1.1 — Реализация обработчика REGISTRY_EXPORT в IBSO
Описание:
На стороне IBSO реализовать новый PSQL-обработчик, принимающий XML-запрос в формате:
<Document product="CARD_REGISTRY">
  <ReqRegistry>
    <dateFrom>2025-02-01</dateFrom>
    <dateTo>2025-02-05</dateTo>
  </ReqRegistry>
</Document>
Выход:
Возврат XML с данными о найденных реестрах и их содержимом (Base64 Excel):
<AnsRegistry>
  <registry>
    <id>123</id>
    <fileName>registry_2025-02-01.xlsx</fileName>
    <content>base64...</content>
  </registry>
</AnsRegistry>
Комментарий:
Обработчик в IBSO выполняет SQL-запрос к таблице PRI_Заявка_на_выпуск_карты и формирует Excel-файлы (один файл = один день).
Оценка: 3 дня (IBSO PSQL + тестирование)
🔹 Задача 1.2 — Создание Java-сущностей и сервиса IbsoRegistryService
Описание:
В Динамике реализовать Java-сервис, который:
формирует XML-запрос ReqRegistry,
маршаллит его (через JAXB),
вызывает directABSService.request(document),
парсит XML-ответ и сохраняет Excel-файлы на диск (C:\IBSO-DOK\incoming),
сохраняет метаданные в PostgreSQL (registry_id, file_name, date, status).
Оценка: 2 дня
🔹 Задача 1.3 — Добавить форму в Динамике
Описание:
Создать экранную форму “Загрузка реестров IBSO”:
Поле ввода даты (LocalDate),
Кнопка “Загрузить реестры”,
Отображение списка полученных файлов с флагом “OK / Ошибка”.
Оценка: 1 день
🔹 Задача 1.4 — Написать тесты для IbsoRegistryService
Мок directABSService, мок DocumentMarshaller,
Тест на успешную выгрузку,
Тест на ошибку IBSO (например, <failure> в ответе).
Оценка: 0.5 дня
✅ Результат Эпика 1:
Реестры за выбранную дату выгружаются автоматически из IBSO, сохраняются в файловую систему и в БД.
📗 Эпик 2. Обработка реестров и создание карт
🔹 Задача 2.1 — Парсинг Excel-реестра
Описание:
Реализовать RegistryParserService, который читает Excel-файл (Apache POI), извлекает строки и мапит их в DTO:
public class RegistryRow {
  private String inn;
  private String fio;
  private String fioLat;
  private String cardType;
  private String tariff;
  private String phone;
}
Оценка: 1.5 дня
🔹 Задача 2.2 — Автоматическая обработка строк реестра
Описание:
Создать сервис RegistryProcessorService, который:
проходит по каждой строке реестра,
вызывает IBSO-операцию OPEN_ACCOUNT_AND_ISSUE_CARD,
сохраняет результат (SUCCESS / FAILED) в БД.
Формат XML-запроса:
<Document product="OPEN_CARD">
  <ReqCard>
    <clientInn>...</clientInn>
    <fioLat>...</fioLat>
    <depositType>...</depositType>
  </ReqCard>
</Document>
Оценка: 3 дня
🔹 Задача 2.3 — IBSO обработчик “Открытие счёта и выпуск карты”
Описание:
На стороне IBSO реализовать процедуру, которая:
открывает досье,
создаёт счёт и карту,
возвращает XML-ответ с ID карты и статусом.
Оценка: 3 дня (IBSO)
✅ Результат Эпика 2:
Реестры автоматически читаются и обрабатываются, карты создаются без участия операциониста.
📙 Эпик 3. Автозаполнение ФИО на латинице
🔹 Задача 3.1 — Добавить поддержку латиницы в реестрах
Описание:
В RegistryParserService добавить поля firstNameLat, lastNameLat, брать из справочника PRI Заявка на выпуск карты.
Если значения отсутствуют — использовать транслитерацию:
public String transliterate(String cyrillic) { ... }
Оценка: 1 день
🔹 Задача 3.2 — Передавать FIO_LAT в IBSO при выпуске карты
Модифицировать XML-запросы к IBSO:
<fioLat>AKHMEDOV ILKHOM</fioLat>
Оценка: 0.5 дня
✅ Результат Эпика 3:
Для именных карт FIO_LAT подставляется автоматически.
📒 Эпик 4. Определение депозитного договора по тарифу
🔹 Задача 4.1 — Создать справочник соответствия “тариф ↔ договор”
Описание:
В PostgreSQL создать таблицу:
CREATE TABLE card_type_mapping (
  tariff_name TEXT,
  deposit_type TEXT
);
Оценка: 0.5 дня
🔹 Задача 4.2 — Автоматический выбор договора
В RegistryProcessorService по полю tariff подставлять соответствующий depositType.
Оценка: 0.5 дня
✅ Результат Эпика 4:
Тип депозитного договора определяется автоматически.
📕 Эпик 5. Статусы согласования
🔹 Задача 5.1 — Автоматическое проставление статусов
В зависимости от типа карты:
Моментальная → Согласовано,
Именная → На согласовании.
Оценка: 0.5 дня
✅ Результат Эпика 5:
Операционист не проставляет статусы вручную.
📘 Эпик 6. Автозагрузка документов клиента
🔹 Задача 6.1 — Реализовать DocumentUploadService
Собирает сканы документов из папки заказа,
Конвертирует в Base64,
Отправляет в IBSO обработчик UPLOAD_CLIENT_DOCUMENTS.
Оценка: 2 дня
✅ Результат Эпика 6:
Документы автоматически подгружаются в анкету клиента.
📗 Эпик 7. Автоматизация финальных операций
🔹 Задача 7.1 — Подписание, выдача карты, открытие ДБО
Описание:
Реализовать последовательный сценарий:
signContract → issueCard → activateDbo
Оценка: 2 дня
✅ Результат Эпика 7:
После создания карты весь процесс завершается автоматически.
🧩 Итого по оценке
Эпик	Название	Оценка (чистого времени)
1	Автовыгрузка реестров	6.5 дней
2	Обработка и создание карт	7.5 дней
3	ФИО латиницей	1.5 дня
4	Маппинг тариф ↔ договор	1 день
5	Статусы согласования	0.5 дня
6	Загрузка документов	2 дня
7	Подписание / выдача / ДБО	2 дня
Итого:		~21 день (≈1 календарный месяц)

