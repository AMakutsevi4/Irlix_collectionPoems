package ru.irlix_collectionpoems;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class IrlixCollectionPoemsApplicationTests {

    @Test
    void contextLoads() {
    }

}package ru.pskb.integrator.entity;

import jakarta.persistence.*;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "application_history")
@Schema(description = "История изменений по заявке")
public class ApplicationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "application_id", nullable = false)
    @Schema(description = "ID основной заявки", example = "c3e3f52c-8b01-4feb-8304-a3a2df2846e8")
    private String applicationId;

    @Column(name = "status", nullable = false)
    @Schema(description = "Статус заявки в момент изменения", example = "PENDING")
    private String status;

    @Column(name = "credit_sum")
    @Schema(description = "Сумма кредита в момент изменения", example = "500000")
    private BigDecimal creditSum;

    @Column(name = "term_months")
    @Schema(description = "Срок кредита в месяцах", example = "60")
    private Integer termMonths;

    @Column(name = "confirmed_income")
    @Schema(description = "Подтверждённый доход клиента", example = "85000.00")
    private BigDecimal confirmedIncome;

    @Column(name = "comment")
    @Schema(description = "Комментарий к изменению", example = "Client uploaded 2-NDFL document")
    private String comment;

    @Column(name = "changed_at", nullable = false)
    @Schema(description = "Дата и время изменения")
    private LocalDateTime changedAt;
}


--liquibase formatted sql
--changeset alexandr:2025-11-09--create-application-history-table

CREATE TABLE IF NOT EXISTS application_history (
    id UUID PRIMARY KEY,
    application_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    credit_sum NUMERIC(15,2),
    term_months INTEGER,
    confirmed_income NUMERIC(15,2),
    comment TEXT,
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);


package ru.pskb.integrator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.pskb.integrator.entity.ApplicationHistory;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApplicationHistoryRepository extends JpaRepository<ApplicationHistory, UUID> {
    List<ApplicationHistory> findByApplicationIdOrderByChangedAtAsc(String applicationId);
}


package ru.pskb.integrator.service.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.pskb.integrator.entity.ApplicationHistory;
import ru.pskb.integrator.repository.ApplicationHistoryRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ApplicationHistoryLogger {

    private final ApplicationHistoryRepository repository;

    public void logChange(String applicationId,
                          String status,
                          BigDecimal creditSum,
                          Integer term,
                          BigDecimal confirmedIncome,
                          String comment) {

        ApplicationHistory entry = ApplicationHistory.builder()
                .applicationId(applicationId)
                .status(status)
                .creditSum(creditSum)
                .termMonths(term)
                .confirmedIncome(confirmedIncome)
                .comment(comment)
                .changedAt(LocalDateTime.now())
                .build();

        repository.save(entry);
    }
}



@Service
@RequiredArgsConstructor
public class ProofOfIncomeService {

    private final CreditApplicationRepository applicationRepository;
    private final ApplicationHistoryLogger historyLogger;
    private final IbsoClient ibsoClient;

    @Transactional
    public void confirmIncome(String requestId, BigDecimal confirmedIncome) {
        var app = applicationRepository.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Application not




cation not found"));

        app.setConfirmedIncome(confirmedIncome);
        app.setStatus(ApplicationStatus.PENDING);
        app.setUpdatedAt(LocalDateTime.now());
        applicationRepository.save(app);

        // отправляем уточнённые данные в ИБСО
        ibsoClient.sendIncomeUpdate(app);

        // сохраняем историю
        historyLogger.logChange(
                app.getRequestId(),
                app.getStatus().name(),
                app.getCreditSum(),
                app.getTermMonths(),
                confirmedIncome,
                "Подтверждён доход клиента (2-НДФЛ)"
        );
    }
}



package ru.pskb.integrator.dto.history;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Элемент истории заявки")
public record ApplicationHistoryResponse(

        @Schema(description = "ID основной заявки", example = "c3e3f52c-8b01-4feb-8304-a3a2df2846e8")
        String applicationId,

        @Schema(description = "Статус заявки в момент изменения", example = "PENDING")
        String status,

        @Schema(description = "Сумма кредита", example = "500000")
        BigDecimal creditSum,

        @Schema(description = "Срок кредита (в месяцах)", example = "60")
        Integer termMonths,

        @Schema(description = "Подтверждённый доход клиента", example = "85000.00")
        BigDecimal confirmedIncome,

        @Schema(description = "Комментарий к изменению", example = "Client uploaded 2-NDFL document")
        String comment,

        @Schema(description = "Дата и время изменения")
        LocalDateTime changedAt
) {}




package ru.pskb.integrator.service.history;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.pskb.integrator.dto.history.ApplicationHistoryResponse;
import ru.pskb.integrator.repository.ApplicationHistoryRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ApplicationHistoryService {

    private final ApplicationHistoryRepository repository;

    @Transactional(readOnly = true)
    public List<ApplicationHistoryResponse> getHistoryByRequestId(String requestId) {
        return repository.findByApplicationIdOrderByChangedAtAsc(requestId)
                .stream()
                .map(entity -> new ApplicationHistoryResponse(
                        entity.getApplicationId(),
                        entity.getStatus(),
                        entity.getCreditSum(),
                        entity.getTermMonths(),
                        entity.getConfirmedIncome(),
                        entity.getComment(),
                        entity.getChangedAt()
                ))
                .toList();
    }
}




package ru.pskb.integrator.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.pskb.integrator.dto.history.ApplicationHistoryResponse;
import ru.pskb.integrator.service.history.ApplicationHistoryService;

import java.util.List;

@Validated
@RestController
@RequestMapping("/application")
@RequiredArgsConstructor
@Tag(name = "История заявок", description = "Позволяет получить все изменения по заявке")
public class ApplicationHistoryController {

    private final ApplicationHistoryService historyService;

    @GetMapping("/{requestId}/history")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Получить историю изменений заявки",
            description = "Возвращает список всех изменений статусов, сумм, сроков и подтверждённого дохода по заявке")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "История найдена"),
            @ApiR

esponse(responseCode = "404", description = "Заявка не найдена"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав")
    })
    public List<ApplicationHistoryResponse> getApplicationHistory(@PathVariable String requestId) {
        return historyService.getHistoryByRequestId(requestId);
    }
}





