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
