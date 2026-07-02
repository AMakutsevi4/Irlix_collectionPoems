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



ru.dynamika.data.developer.resttemplate.exception.DataPlatformException: 403 : "Ошибка при обращении к внешнему сервису: "
	at ru.dynamika.data.developer.resttemplate.RestTemplateResponseErrorHandler.handleError(RestTemplateResponseErrorHandler.java:28) ~[data-platform-api-1.103-20250209.153418-1.jar:1.103-SNAPSHOT]
	at org.springframework.web.client.ResponseErrorHandler.handleError(ResponseErrorHandler.java:63) ~[spring-web-5.3.22.jar:5.3.22]
	at org.springframework.web.client.RestTemplate.handleResponse(RestTemplate.java:819) ~[spring-web-5.3.22.jar:5.3.22]
	at org.springframework.web.client.RestTemplate.doExecute(RestTemplate.java:777) ~[spring-web-5.3.22.jar:5.3.22]
	at org.springframework.web.client.RestTemplate.execute(RestTemplate.java:711) ~[spring-web-5.3.22.jar:5.3.22]
	at org.springframework.web.client.RestTemplate.getForEntity(RestTemplate.java:361) ~[spring-web-5.3.22.jar:5.3.22]
	at ru.dynamika.core.commonapp.SimpleUserService.getUser(SimpleUserService.java:35) ~[core-api-1.4.215-data-platform-20250207.005507-1.jar:na]
	at ru.dynamika.core.platform_communication.ReqGetViewDataProcessor.process(ReqGetViewDataProcessor.kt:139) ~[core-api-1.4.215-data-platform-20250207.005507-1.jar:na]
	at ru.dynamika.core.platform_communication.PlatformRequestListener.exchange(PlatformRequestListener.kt:29) ~[core-api-1.4.215-data-platform-20250207.005507-1.jar:na]
	at ru.dynamika.core.platform_communication.HTTPPlatformRequestListener.listen(XMLHTTPPlatformRequestListener.kt:57) [core-api-1.4.215-data-platform-20250207.005507-1.jar:na]
	at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method) ~[na:1.8.0_441]
	at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62) ~[na:1.8.0_441]
	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43) ~[na:1.8.0_441]
	at java.lang.reflect.Method.invoke(Method.java:498) ~[na:1.8.0_441]
	at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:205) [spring-web-5.3.22.jar:5.3.22]
	at org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:150) [spring-web-5.3.22.jar:5.3.22]
	at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:117) [spring-webmvc-5.3.22.jar:5.3.22]
	at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.invokeHandlerMethod(RequestMappingHandlerAdapter.java:895) [spring-webmvc-5.3.22.jar:5.3.22]
	at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.handleInternal(RequestMappingHandlerAdapter.java:808) [spring-webmvc-5.3.22.jar:5.3.22]
	at org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter.handle(AbstractHandlerMethodAdapter.java:87) [spring-webmvc-5.3.22.jar:5.3.22]
	at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1070) [spring-webmvc-5.3.22.jar:5.3.22]
	at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:963) [spring-webmvc-5.3.22.jar:5.3.22]
	at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1006) [spring-webmvc-5.3.22.jar:5.3.22]
	at org.springframework.web.servlet.FrameworkServlet.doPost(FrameworkServlet.java:909) [spring-webmvc-5.3.22.jar:5.3.22]
	at javax.servlet.http.HttpServlet.service(HttpServlet.java:681) [tomcat-embed-core-9.0.65.jar:4.0.FR]
	at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:883) [spring-webmvc-5.3.22.jar:5.3.22]
	at javax.servlet.http.HttpServlet.service(HttpServlet.java:764) [tomcat-embed-core-9.0.65.jar:4.0.FR]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:227) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:162) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.tomcat.websocket.server.WsFilter.doFilter(WsFilter.java:53) [tomcat-embed-websocket-9.0.65.jar:9.0.65]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:189) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:162) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.springframework.web.filter.RequestContextFilter.doFilterInternal(RequestContextFilter.java:100) [spring-web-5.3.22.jar:5.3.22]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117) [spring-web-5.3.22.jar:5.3.22]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:189) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:162) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.springframework.web.filter.FormContentFilter.doFilterInternal(FormContentFilter.java:93) [spring-web-5.3.22.jar:5.3.22]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117) [spring-web-5.3.22.jar:5.3.22]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:189) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:162) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.springframework.boot.actuate.metrics.web.servlet.WebMvcMetricsFilter.doFilterInternal(WebMvcMetricsFilter.java:96) [spring-boot-actuator-2.7.2.jar:2.7.2]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117) [spring-web-5.3.22.jar:5.3.22]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:189) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:162) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.springframework.web.filter.CharacterEncodingFilter.doFilterInternal(CharacterEncodingFilter.java:201) [spring-web-5.3.22.jar:5.3.22]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117) [spring-web-5.3.22.jar:5.3.22]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:189) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:162) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.catalina.core.StandardWrapperValve.invoke(StandardWrapperValve.java:197) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.catalina.core.StandardContextValve.invoke(StandardContextValve.java:97) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.catalina.authenticator.AuthenticatorBase.invoke(AuthenticatorBase.java:541) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.catalina.core.StandardHostValve.invoke(StandardHostValve.java:135) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.catalina.valves.ErrorReportValve.invoke(ErrorReportValve.java:92) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.catalina.core.StandardEngineValve.invoke(StandardEngineValve.java:78) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.catalina.connector.CoyoteAdapter.service(CoyoteAdapter.java:360) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.coyote.http11.Http11Processor.service(Http11Processor.java:399) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.coyote.AbstractProcessorLight.process(AbstractProcessorLight.java:65) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.coyote.AbstractProtocol$ConnectionHandler.process(AbstractProtocol.java:890) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.tomcat.util.net.NioEndpoint$SocketProcessor.doRun(NioEndpoint.java:1789) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.tomcat.util.net.SocketProcessorBase.run(SocketProcessorBase.java:49) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.tomcat.util.threads.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1191) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.tomcat.util.threads.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:659) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:61) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at java.lang.Thread.run(Thread.java:750) [na:1.8.0_441]

2025-12-26 14:46:16.492 DEBUG 17712 --- [nio-9679-exec-1] r.d.core.model.DocumentMarshaller        : Marshalling document: 
<?xml version='1.0' encoding='UTF-8'?><Document product="GetViewData" user="ADMIN" platform="NF"><Failure><info>403 : "Ошибка при обращении к внешнему сервису: "</info><ShowModal>0</ShowModal></Failure></Document>
2025-12-26 14:46:16.493  INFO 17712 --- [nio-9679-exec-1] r.d.c.p.PlatformRequestListener          : Answering with <?xml version='1.0' encoding='UTF-8'?><Document product="GetViewData" user="ADMIN" platform="NF"><Failure><info>403 : "Ошибка при обращении к внешнему сервису: "</info><ShowModal>0</ShowModal></Failure></Document>
2025-12-26 14:56:34.345  INFO 17712 --- [nio-9679-exec-3] r.d.c.p.PlatformRequestListener          : Received request <?xml version='1.0' encoding='UTF-8'?><Document product="GetViewData" user="ADMIN" ver_xml="2016-05-04" platform="NF" contextId="849f77b0-971a-486f-90d9-1a085fd6daf0"><ReqGetViewData><Context><Param name="runId">849f77b0-971a-486f-90d9-1a085fd6daf0</Param><Param name="P_CLIENT_ID"/><Param name="START_FROM">1</Param><Param name="PAGE_SIZE">20</Param></Context><View name="marketplace_sravni_sravnirequest_hot_key" class="mp_sravni_sravnirequest"/><params/></ReqGetViewData></Document>
2025-12-26 14:56:34.361  INFO 17712 --- [nio-9679-exec-3] r.d.core.model.DocumentMarshaller        : Unmarshalling document: 
<?xml version='1.0' encoding='UTF-8'?><Document product="GetViewData" user="ADMIN" ver_xml="2016-05-04" platform="NF" contextId="849f77b0-971a-486f-90d9-1a085fd6daf0"><ReqGetViewData><Context><Param name="runId">849f77b0-971a-486f-90d9-1a085fd6daf0</Param><Param name="P_CLIENT_ID"/><Param name="START_FROM">1</Param><Param name="PAGE_SIZE">20</Param></Context><View name="marketplace_sravni_sravnirequest_hot_key" class="mp_sravni_sravnirequest"/><params/></ReqGetViewData></Document>
2025-12-26 14:56:37.576  INFO 17712 --- [nio-9679-exec-3] d.d.d.r.RestTemplateResponseErrorHandler : 403 : "Ошибка при обращении к внешнему сервису: "
2025-12-26 14:56:37.616 ERROR 17712 --- [nio-9679-exec-3] r.d.c.p.PlatformRequestListener          : Error processing request

ru.dynamika.data.developer.resttemplate.exception.DataPlatformException: 403 : "Ошибка при обращении к внешнему сервису: "
	at ru.dynamika.data.developer.resttemplate.RestTemplateResponseErrorHandler.handleError(RestTemplateResponseErrorHandler.java:28) ~[data-platform-api-1.103-20250209.153418-1.jar:1.103-SNAPSHOT]
	at org.springframework.web.client.ResponseErrorHandler.handleError(ResponseErrorHandler.java:63) ~[spring-web-5.3.22.jar:5.3.22]
	at org.springframework.web.client.RestTemplate.handleResponse(RestTemplate.java:819) ~[spring-web-5.3.22.jar:5.3.22]
	at org.springframework.web.client.RestTemplate.doExecute(RestTemplate.java:777) ~[spring-web-5.3.22.jar:5.3.22]
	at org.springframework.web.client.RestTemplate.execute(RestTemplate.java:711) ~[spring-web-5.3.22.jar:5.3.22]
	at org.springframework.web.client.RestTemplate.getForEntity(RestTemplate.java:361) ~[spring-web-5.3.22.jar:5.3.22]
	at ru.dynamika.core.commonapp.SimpleUserService.getUser(SimpleUserService.java:35) ~[core-api-1.4.215-data-platform-20250207.005507-1.jar:na]
	at ru.dynamika.core.platform_communication.ReqGetViewDataProcessor.process(ReqGetViewDataProcessor.kt:139) ~[core-api-1.4.215-data-platform-20250207.005507-1.jar:na]
	at ru.dynamika.core.platform_communication.PlatformRequestListener.exchange(PlatformRequestListener.kt:29) ~[core-api-1.4.215-data-platform-20250207.005507-1.jar:na]
	at ru.dynamika.core.platform_communication.HTTPPlatformRequestListener.listen(XMLHTTPPlatformRequestListener.kt:57) [core-api-1.4.215-data-platform-20250207.005507-1.jar:na]
	at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method) ~[na:1.8.0_441]
	at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62) ~[na:1.8.0_441]
	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43) ~[na:1.8.0_441]
	at java.lang.reflect.Method.invoke(Method.java:498) ~[na:1.8.0_441]
	at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:205) [spring-web-5.3.22.jar:5.3.22]
	at org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:150) [spring-web-5.3.22.jar:5.3.22]
	at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:117) [spring-webmvc-5.3.22.jar:5.3.22]
	at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.invokeHandlerMethod(RequestMappingHandlerAdapter.java:895) [spring-webmvc-5.3.22.jar:5.3.22]
	at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.handleInternal(RequestMappingHandlerAdapter.java:808) [spring-webmvc-5.3.22.jar:5.3.22]
	at org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter.handle(AbstractHandlerMethodAdapter.java:87) [spring-webmvc-5.3.22.jar:5.3.22]
	at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1070) [spring-webmvc-5.3.22.jar:5.3.22]
	at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:963) [spring-webmvc-5.3.22.jar:5.3.22]
	at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1006) [spring-webmvc-5.3.22.jar:5.3.22]
	at org.springframework.web.servlet.FrameworkServlet.doPost(FrameworkServlet.java:909) [spring-webmvc-5.3.22.jar:5.3.22]
	at javax.servlet.http.HttpServlet.service(HttpServlet.java:681) [tomcat-embed-core-9.0.65.jar:4.0.FR]
	at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:883) [spring-webmvc-5.3.22.jar:5.3.22]
	at javax.servlet.http.HttpServlet.service(HttpServlet.java:764) [tomcat-embed-core-9.0.65.jar:4.0.FR]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:227) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:162) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.tomcat.websocket.server.WsFilter.doFilter(WsFilter.java:53) [tomcat-embed-websocket-9.0.65.jar:9.0.65]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:189) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:162) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.springframework.web.filter.RequestContextFilter.doFilterInternal(RequestContextFilter.java:100) [spring-web-5.3.22.jar:5.3.22]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117) [spring-web-5.3.22.jar:5.3.22]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:189) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:162) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.springframework.web.filter.FormContentFilter.doFilterInternal(FormContentFilter.java:93) [spring-web-5.3.22.jar:5.3.22]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117) [spring-web-5.3.22.jar:5.3.22]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:189) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:162) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.springframework.boot.actuate.metrics.web.servlet.WebMvcMetricsFilter.doFilterInternal(WebMvcMetricsFilter.java:96) [spring-boot-actuator-2.7.2.jar:2.7.2]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117) [spring-web-5.3.22.jar:5.3.22]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:189) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:162) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.springframework.web.filter.CharacterEncodingFilter.doFilterInternal(CharacterEncodingFilter.java:201) [spring-web-5.3.22.jar:5.3.22]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117) [spring-web-5.3.22.jar:5.3.22]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:189) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:162) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.catalina.core.StandardWrapperValve.invoke(StandardWrapperValve.java:197) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.catalina.core.StandardContextValve.invoke(StandardContextValve.java:97) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.catalina.authenticator.AuthenticatorBase.invoke(AuthenticatorBase.java:541) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.catalina.core.StandardHostValve.invoke(StandardHostValve.java:135) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.catalina.valves.ErrorReportValve.invoke(ErrorReportValve.java:92) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.catalina.core.StandardEngineValve.invoke(StandardEngineValve.java:78) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.catalina.connector.CoyoteAdapter.service(CoyoteAdapter.java:360) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.coyote.http11.Http11Processor.service(Http11Processor.java:399) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.coyote.AbstractProcessorLight.process(AbstractProcessorLight.java:65) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.coyote.AbstractProtocol$ConnectionHandler.process(AbstractProtocol.java:890) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.tomcat.util.net.NioEndpoint$SocketProcessor.doRun(NioEndpoint.java:1789) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.tomcat.util.net.SocketProcessorBase.run(SocketProcessorBase.java:49) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.tomcat.util.threads.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1191) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.tomcat.util.threads.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:659) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:61) [tomcat-embed-core-9.0.65.jar:9.0.65]
	at java.lang.Thread.run(Thread.java:750) [na:1.8.0_441]

























26.12.2025 16:41:34,359  INFO [http-nio-1080-exec-4][IncomingRequestLogInterceptor] Incoming request: http://localhost:1080/dynamika/json/fetch_user (16 ms)
26.12.2025 16:41:34,446  INFO [http-nio-1080-exec-10][IncomingRequestLogInterceptor] Incoming request: http://localhost:1080/dynamika/json/settings (22 ms)
26.12.2025 16:41:38,303  INFO [http-nio-1080-exec-8][AbstractPwdPolicyChecker] Found password policy check method checkWrongActionLockAccount
26.12.2025 16:41:38,303  INFO [http-nio-1080-exec-8][AbstractPwdPolicyChecker] Found password policy check method checkWrongActionBlockIp
26.12.2025 16:41:38,303  INFO [http-nio-1080-exec-8][AbstractPwdPolicyChecker] Found password policy check method checkWrongActionBoth
26.12.2025 16:41:38,303  INFO [http-nio-1080-exec-8][AbstractPwdPolicyChecker] Found password policy check method checkIgnoreCase
26.12.2025 16:41:38,303  INFO [http-nio-1080-exec-8][AbstractPwdPolicyChecker] Found password policy check method checkLifetime
26.12.2025 16:41:38,303  INFO [http-nio-1080-exec-8][AbstractPwdPolicyChecker] Found password policy check method checkPassword
26.12.2025 16:41:38,377  WARN [http-nio-1080-exec-8][AbstractDaoImpl] Using paging with distinct transformer may lead to unpredictable query results!
26.12.2025 16:41:38,383  INFO [http-nio-1080-exec-8][AbstractPwdPolicyChecker] Found password policy check method checkWrongActionLockAccount
26.12.2025 16:41:38,383  INFO [http-nio-1080-exec-8][AbstractPwdPolicyChecker] Found password policy check method checkWrongActionBlockIp
26.12.2025 16:41:38,383  INFO [http-nio-1080-exec-8][AbstractPwdPolicyChecker] Found password policy check method checkWrongActionBoth
26.12.2025 16:41:38,383  INFO [http-nio-1080-exec-8][AbstractPwdPolicyChecker] Found password policy check method checkIgnoreCase
26.12.2025 16:41:38,383  INFO [http-nio-1080-exec-8][AbstractPwdPolicyChecker] Found password policy check method checkLifetime
26.12.2025 16:41:38,383  INFO [http-nio-1080-exec-8][AbstractPwdPolicyChecker] Found password policy check method checkPassword
26.12.2025 16:41:38,423  INFO [http-nio-1080-exec-8][IncomingRequestLogInterceptor] Incoming request: http://localhost:1080/dynamika/json/login (186 ms)
26.12.2025 16:41:38,456  INFO [http-nio-1080-exec-7][IncomingRequestLogInterceptor] Incoming request: http://localhost:1080/dynamika/json/fetch_user (27 ms)
26.12.2025 16:41:38,483  INFO [http-nio-1080-exec-1][IncomingRequestLogInterceptor] Incoming request: http://localhost:1080/dynamika/json/notify/getMessages (11 ms)
26.12.2025 16:41:38,484  INFO [http-nio-1080-exec-9][IncomingRequestLogInterceptor] Incoming request: http://localhost:1080/dynamika/json/settings (11 ms)
26.12.2025 16:41:38,484  INFO [http-nio-1080-exec-10][IncomingRequestLogInterceptor] Incoming request: http://localhost:1080/dynamika/json/get_operation_add_control (14 ms)
26.12.2025 16:41:38,493  INFO [http-nio-1080-exec-8][IncomingRequestLogInterceptor] Incoming request: http://localhost:1080/dynamika/json/get_ofmik_instructions (3 ms)
26.12.2025 16:41:38,518  INFO [http-nio-1080-exec-5][IncomingRequestLogInterceptor] Incoming request: http://localhost:1080/dynamika/ws (16 ms)
26.12.2025 16:41:38,519  INFO [http-nio-1080-exec-4][IncomingRequestLogInterceptor] Incoming request: http://localhost:1080/dynamika/json/user_rule (48 ms)
26.12.2025 16:41:38,527  INFO [http-nio-1080-exec-5][NotificationServiceImpl] Register user id = 2, active notification sessions: 1
26.12.2025 16:41:38,528  INFO [http-nio-1080-exec-5][NotificationServiceImpl] closeSession null
26.12.2025 16:41:38,589  INFO [lettuce-nioEventLoop-4-1][NotificationServiceImpl] got PubSub ch: USER_REGISTER msg: {"id":"2","instance":"6057ffbc-7c12-4052-93a6-e1e31ca06c9f"}
26.12.2025 16:41:38,591  INFO [lettuce-nioEventLoop-4-1][NotificationServiceImpl] ignore PubSub to self
26.12.2025 16:41:38,628  INFO [http-nio-1080-exec-6][IncomingRequestLogInterceptor] Incoming request: http://localhost:1080/dynamika/json/units_list (156 ms)
26.12.2025 16:41:38,646  INFO [http-nio-1080-exec-3][IncomingRequestLogInterceptor] Incoming request: http://localhost:1080/dynamika/json/custom_operations (175 ms)
26.12.2025 16:41:38,857  INFO [http-nio-1080-exec-2][IncomingRequestLogInterceptor] Incoming request: http://localhost:1080/dynamika/json/views_list (363 ms)
26.12.2025 16:41:38,872  INFO [http-nio-1080-exec-7][IncomingRequestLogInterceptor] Incoming request: http://localhost:1080/dynamika/json/get_ui_contexts (8 ms)
26.12.2025 16:41:39,027  INFO [http-nio-1080-exec-9][DocumentMarshaller] Marshalling document: 
<?xml version='1.0' encoding='UTF-8'?><Document product="GetViewData" user="ADMIN" ver_xml="2016-05-04" platform="NF" contextId="849f77b0-971a-486f-90d9-1a085fd6daf0"><ReqGetViewData><Context><Param name="runId">849f77b0-971a-486f-90d9-1a085fd6daf0</Param><Param name="P_CLIENT_ID"/><Param name="START_FROM">1</Param><Param name="PAGE_SIZE">20</Param></Context><View name="marketplace_sravni_sravnirequest_hot_key" class="mp_sravni_sravnirequest"/><params/></ReqGetViewData></Document>
26.12.2025 16:41:39,616  INFO [http-nio-1080-exec-1][AbstractPwdPolicyChecker] Found password policy check method checkWrongActionLockAccount
26.12.2025 16:41:39,616  INFO [http-nio-1080-exec-1][AbstractPwdPolicyChecker] Found password policy check method checkWrongActionBlockIp
26.12.2025 16:41:39,616  INFO [http-nio-1080-exec-1][AbstractPwdPolicyChecker] Found password policy check method checkWrongActionBoth
26.12.2025 16:41:39,616  INFO [http-nio-1080-exec-1][AbstractPwdPolicyChecker] Found password policy check method checkIgnoreCase
26.12.2025 16:41:39,616  INFO [http-nio-1080-exec-1][AbstractPwdPolicyChecker] Found password policy check method checkLifetime
26.12.2025 16:41:39,616  INFO [http-nio-1080-exec-1][AbstractPwdPolicyChecker] Found password policy check method checkPassword
26.12.2025 16:41:39,726  INFO [http-nio-1080-exec-1][IncomingRequestLogInterceptor] Incoming request: http://localhost:1080/dynamika/json/login (123 ms)
26.12.2025 16:41:39,746 ERROR [http-nio-1080-exec-10][GlobalControllerExceptionHandler] Access denied
ru.dynamika.web.controller.json.NotAuthorizedException: null
	at ru.dynamika.web.controller.json.JsonController.checkAdmin(JsonController.java:83) ~[classes!/:?]
	at ru.dynamika.web.controller.json.JsonAdminController.usersRoles(JsonAdminController.java:154) ~[classes!/:?]
	at sun.reflect.GeneratedMethodAccessor636.invoke(Unknown Source) ~[?:?]
	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43) ~[?:1.8.0_441]
	at java.lang.reflect.Method.invoke(Method.java:498) ~[?:1.8.0_441]
	at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:205) ~[spring-web-5.3.25.jar!/:5.3.25]
	at org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:150) ~[spring-web-5.3.25.jar!/:5.3.25]
	at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:117) ~[spring-webmvc-5.3.25.jar!/:5.3.25]
	at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.invokeHandlerMethod(RequestMappingHandlerAdapter.java:895) ~[spring-webmvc-5.3.25.jar!/:5.3.25]
	at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.handleInternal(RequestMappingHandlerAdapter.java:808) ~[spring-webmvc-5.3.25.jar!/:5.3.25]
	at org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter.handle(AbstractHandlerMethodAdapter.java:87) ~[spring-webmvc-5.3.25.jar!/:5.3.25]
	at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1071) [spring-webmvc-5.3.25.jar!/:5.3.25]
	at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:964) [spring-webmvc-5.3.25.jar!/:5.3.25]
	at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1006) [spring-webmvc-5.3.25.jar!/:5.3.25]
	at org.springframework.web.servlet.FrameworkServlet.doPost(FrameworkServlet.java:909) [spring-webmvc-5.3.25.jar!/:5.3.25]
	at javax.servlet.http.HttpServlet.service(HttpServlet.java:555) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:883) [spring-webmvc-5.3.25.jar!/:5.3.25]
	at javax.servlet.http.HttpServlet.service(HttpServlet.java:623) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:209) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.tomcat.websocket.server.WsFilter.doFilter(WsFilter.java:51) [tomcat-embed-websocket-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.springframework.security.web.session.ConcurrentSessionFilter.doFilter(ConcurrentSessionFilter.java:147) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.session.ConcurrentSessionFilter.doFilter(ConcurrentSessionFilter.java:125) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter.doFilter(AbstractAuthenticationProcessingFilter.java:223) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter.doFilter(AbstractAuthenticationProcessingFilter.java:217) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.springframework.security.web.access.intercept.FilterSecurityInterceptor.invoke(FilterSecurityInterceptor.java:106) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.access.intercept.FilterSecurityInterceptor.doFilter(FilterSecurityInterceptor.java:81) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:337) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.access.intercept.FilterSecurityInterceptor.invoke(FilterSecurityInterceptor.java:115) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.access.intercept.FilterSecurityInterceptor.doFilter(FilterSecurityInterceptor.java:81) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:122) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:116) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.session.SessionManagementFilter.doFilter(SessionManagementFilter.java:126) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.session.SessionManagementFilter.doFilter(SessionManagementFilter.java:81) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.authentication.AnonymousAuthenticationFilter.doFilter(AnonymousAuthenticationFilter.java:109) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter.doFilter(SecurityContextHolderAwareRequestFilter.java:149) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.savedrequest.RequestCacheAwareFilter.doFilter(RequestCacheAwareFilter.java:63) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter.doFilter(AbstractAuthenticationProcessingFilter.java:223) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter.doFilter(AbstractAuthenticationProcessingFilter.java:217) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.header.HeaderWriterFilter.doHeadersAfter(HeaderWriterFilter.java:90) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.header.HeaderWriterFilter.doFilterInternal(HeaderWriterFilter.java:75) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117) [spring-web-5.3.25.jar!/:5.3.25]
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter.doFilterInternal(WebAsyncManagerIntegrationFilter.java:55) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117) [spring-web-5.3.25.jar!/:5.3.25]
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.session.ConcurrentSessionFilter.doFilter(ConcurrentSessionFilter.java:147) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.session.ConcurrentSessionFilter.doFilter(ConcurrentSessionFilter.java:125) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.context.SecurityContextPersistenceFilter.doFilter(SecurityContextPersistenceFilter.java:112) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.context.SecurityContextPersistenceFilter.doFilter(SecurityContextPersistenceFilter.java:82) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.session.DisableEncodeUrlFilter.doFilterInternal(DisableEncodeUrlFilter.java:42) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117) [spring-web-5.3.25.jar!/:5.3.25]
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.FilterChainProxy.doFilterInternal(FilterChainProxy.java:221) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.FilterChainProxy.doFilter(FilterChainProxy.java:186) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.springframework.orm.jpa.support.OpenEntityManagerInViewFilter.doFilterInternal(OpenEntityManagerInViewFilter.java:186) [spring-orm-5.3.32.jar!/:5.3.32]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117) [spring-web-5.3.25.jar!/:5.3.25]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.springframework.web.filter.CharacterEncodingFilter.doFilterInternal(CharacterEncodingFilter.java:201) [spring-web-5.3.25.jar!/:5.3.25]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117) [spring-web-5.3.25.jar!/:5.3.25]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153) [tomcat-embed-core-9.0.86.jar!/:?]
	at ru.dynamika.web.filter.ContentCachingFilter.doFilter(ContentCachingFilter.java:17) [classes!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.springframework.orm.jpa.support.OpenEntityManagerInViewFilter.doFilterInternal(OpenEntityManagerInViewFilter.java:186) [spring-orm-5.3.32.jar!/:5.3.32]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117) [spring-web-5.3.25.jar!/:5.3.25]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153) [tomcat-embed-core-9.0.86.jar!/:?]
	at ru.dynamika.web.filter.CORSFilter.doFilter(CORSFilter.java:76) [classes!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153) [tomcat-embed-core-9.0.86.jar!/:?]
	at net.bull.javamelody.MonitoringFilter.doFilter(MonitoringFilter.java:239) [javamelody-core-1.91.0.jar!/:1.91.0]
	at net.bull.javamelody.MonitoringFilter.doFilter(MonitoringFilter.java:215) [javamelody-core-1.91.0.jar!/:1.91.0]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.springframework.boot.actuate.metrics.web.servlet.WebMvcMetricsFilter.doFilterInternal(WebMvcMetricsFilter.java:96) [spring-boot-actuator-2.7.18.jar!/:2.7.18]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117) [spring-web-5.3.25.jar!/:5.3.25]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.StandardWrapperValve.invoke(StandardWrapperValve.java:168) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.StandardContextValve.invoke(StandardContextValve.java:90) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.authenticator.AuthenticatorBase.invoke(AuthenticatorBase.java:481) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.StandardHostValve.invoke(StandardHostValve.java:130) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.valves.ErrorReportValve.invoke(ErrorReportValve.java:93) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.StandardEngineValve.invoke(StandardEngineValve.java:74) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.connector.CoyoteAdapter.service(CoyoteAdapter.java:346) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.coyote.http11.Http11Processor.service(Http11Processor.java:390) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.coyote.AbstractProcessorLight.process(AbstractProcessorLight.java:63) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.coyote.AbstractProtocol$ConnectionHandler.process(AbstractProtocol.java:928) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.tomcat.util.net.NioEndpoint$SocketProcessor.doRun(NioEndpoint.java:1794) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.tomcat.util.net.SocketProcessorBase.run(SocketProcessorBase.java:52) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.tomcat.util.threads.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1191) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.tomcat.util.threads.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:659) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:63) [tomcat-embed-core-9.0.86.jar!/:?]
	at java.lang.Thread.run(Thread.java:750) [?:1.8.0_441]
26.12.2025 16:41:42,515  INFO [http-nio-1080-exec-10][IncomingRequestLogInterceptor] Incoming request: http://localhost:1080/dynamika/json/admin/users_roles (2776 ms)
26.12.2025 16:41:42,650  INFO [http-nio-1080-exec-9][DocumentMarshaller] Unmarshalling document: 
<?xml version='1.0' encoding='UTF-8'?><Document product="GetViewData" user="ADMIN" platform="NF"><Failure><info>403 : "Ошибка при обращении к внешнему сервису: "</info><ShowModal>0</ShowModal></Failure></Document>
26.12.2025 16:41:42,664 ERROR [http-nio-1080-exec-9][XmlHttpClientService] Failed to perform XML HTTP operation: null, null, 403 : "Ошибка при обращении к внешнему сервису: "
26.12.2025 16:41:42,669  INFO [http-nio-1080-exec-9][DocumentMarshaller] Marshalling document: 
<?xml version='1.0' encoding='UTF-8'?><Document product="GetViewData" user="ADMIN" ver_xml="2016-05-04" platform="NF" contextId="849f77b0-971a-486f-90d9-1a085fd6daf0"><ReqGetViewData><Context><Param name="runId">849f77b0-971a-486f-90d9-1a085fd6daf0</Param><Param name="P_CLIENT_ID"/><Param name="START_FROM">1</Param><Param name="PAGE_SIZE">20</Param></Context><View name="marketplace_sravni_sravnirequest_hot_key" class="mp_sravni_sravnirequest"/><params/></ReqGetViewData></Document>
26.12.2025 16:41:42,705 ERROR [http-nio-1080-exec-9][GlobalControllerExceptionHandler] Request raised exception: POST http://localhost:1080/dynamika/json/get_view_data
Headers: {sec-fetch-mode=[cors], referer=[http://localhost:1080/dynamika/newDesign], content-length=[222], sec-fetch-site=[same-origin], cookie=[JSESSIONID=B09D708FB693A69FB277266BC6D71CFA; fusra_session_id=442f9202-0188-4b9d-9de1-ea2dfc24ba24; Idea-3a06cb4b=0c081245-05f5-4ca1-9cab-94bb92f79ba9; Idea-8e091029=4777d4f7-4bfe-4367-bc91-09d47a3ad1f0; Idea-f431a117=15c42a08-2165-4d5d-a900-6f5ae19f4efa], accept-language=[ru,en-US;q=0.9,en;q=0.8], origin=[http://localhost:1080], accept=[*/*], sec-ch-ua=[&quot;Chromium&quot;;v=&quot;142&quot;, &quot;Google Chrome&quot;;v=&quot;142&quot;, &quot;Not_A Brand&quot;;v=&quot;99&quot;], sec-ch-ua-mobile=[?0], sec-ch-ua-platform=[&quot;Windows&quot;], host=[localhost:1080], content-type=[application/json], connection=[keep-alive], accept-encoding=[gzip, deflate, br, zstd], sec-fetch-dest=[empty], user-agent=[Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36]}
Body: {"runId":"849f77b0-971a-486f-90d9-1a085fd6daf0","name":"marketplace_sravni_sravnirequest_hot_key","class":"mp_sravni_sravnirequest","dataSourceName":"MARKETPLACE_SRAVNI","startFrom":1,"pageSize":20}
ru.dynamika.unitsplayer.service.ServiceException: 403 : "Ошибка при обращении к внешнему сервису: "
	at ru.dynamika.unitsplayer.service.web.XmlHttpClientService.performRequest(XmlHttpClientService.java:63) ~[classes!/:?]
	at ru.dynamika.unitsplayer.service.AbstractStatelessService.performRequest(AbstractStatelessService.java:72) ~[classes!/:?]
	at ru.dynamika.unitsplayer.service.ProductDataService.getViewData(ProductDataService.java:207) ~[classes!/:?]
	at ru.dynamika.web.controller.json.JsonUnitController.getViewData(JsonUnitController.java:783) ~[classes!/:?]
	at sun.reflect.GeneratedMethodAccessor635.invoke(Unknown Source) ~[?:?]
	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43) ~[?:1.8.0_441]
	at java.lang.reflect.Method.invoke(Method.java:498) ~[?:1.8.0_441]
	at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:205) ~[spring-web-5.3.25.jar!/:5.3.25]
	at org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:150) ~[spring-web-5.3.25.jar!/:5.3.25]
	at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:117) ~[spring-webmvc-5.3.25.jar!/:5.3.25]
	at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.invokeHandlerMethod(RequestMappingHandlerAdapter.java:895) ~[spring-webmvc-5.3.25.jar!/:5.3.25]
	at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.handleInternal(RequestMappingHandlerAdapter.java:808) ~[spring-webmvc-5.3.25.jar!/:5.3.25]
	at org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter.handle(AbstractHandlerMethodAdapter.java:87) ~[spring-webmvc-5.3.25.jar!/:5.3.25]
	at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1071) [spring-webmvc-5.3.25.jar!/:5.3.25]
	at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:964) [spring-webmvc-5.3.25.jar!/:5.3.25]
	at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1006) [spring-webmvc-5.3.25.jar!/:5.3.25]
	at org.springframework.web.servlet.FrameworkServlet.doPost(FrameworkServlet.java:909) [spring-webmvc-5.3.25.jar!/:5.3.25]
	at javax.servlet.http.HttpServlet.service(HttpServlet.java:555) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:883) [spring-webmvc-5.3.25.jar!/:5.3.25]
	at javax.servlet.http.HttpServlet.service(HttpServlet.java:623) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:209) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.tomcat.websocket.server.WsFilter.doFilter(WsFilter.java:51) [tomcat-embed-websocket-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.springframework.security.web.session.ConcurrentSessionFilter.doFilter(ConcurrentSessionFilter.java:147) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.session.ConcurrentSessionFilter.doFilter(ConcurrentSessionFilter.java:125) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter.doFilter(AbstractAuthenticationProcessingFilter.java:223) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter.doFilter(AbstractAuthenticationProcessingFilter.java:217) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.springframework.security.web.access.intercept.FilterSecurityInterceptor.invoke(FilterSecurityInterceptor.java:106) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.access.intercept.FilterSecurityInterceptor.doFilter(FilterSecurityInterceptor.java:81) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:337) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.access.intercept.FilterSecurityInterceptor.invoke(FilterSecurityInterceptor.java:115) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.access.intercept.FilterSecurityInterceptor.doFilter(FilterSecurityInterceptor.java:81) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:122) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:116) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.session.SessionManagementFilter.doFilter(SessionManagementFilter.java:126) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.session.SessionManagementFilter.doFilter(SessionManagementFilter.java:81) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.authentication.AnonymousAuthenticationFilter.doFilter(AnonymousAuthenticationFilter.java:109) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter.doFilter(SecurityContextHolderAwareRequestFilter.java:149) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.savedrequest.RequestCacheAwareFilter.doFilter(RequestCacheAwareFilter.java:63) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter.doFilter(AbstractAuthenticationProcessingFilter.java:223) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter.doFilter(AbstractAuthenticationProcessingFilter.java:217) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.header.HeaderWriterFilter.doHeadersAfter(HeaderWriterFilter.java:90) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.header.HeaderWriterFilter.doFilterInternal(HeaderWriterFilter.java:75) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117) [spring-web-5.3.25.jar!/:5.3.25]
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter.doFilterInternal(WebAsyncManagerIntegrationFilter.java:55) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117) [spring-web-5.3.25.jar!/:5.3.25]
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.session.ConcurrentSessionFilter.doFilter(ConcurrentSessionFilter.java:147) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.session.ConcurrentSessionFilter.doFilter(ConcurrentSessionFilter.java:125) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.context.SecurityContextPersistenceFilter.doFilter(SecurityContextPersistenceFilter.java:112) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.context.SecurityContextPersistenceFilter.doFilter(SecurityContextPersistenceFilter.java:82) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.session.DisableEncodeUrlFilter.doFilterInternal(DisableEncodeUrlFilter.java:42) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117) [spring-web-5.3.25.jar!/:5.3.25]
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:346) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.FilterChainProxy.doFilterInternal(FilterChainProxy.java:221) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.springframework.security.web.FilterChainProxy.doFilter(FilterChainProxy.java:186) [spring-security-web-5.7.11.jar!/:5.7.11]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.springframework.orm.jpa.support.OpenEntityManagerInViewFilter.doFilterInternal(OpenEntityManagerInViewFilter.java:186) [spring-orm-5.3.32.jar!/:5.3.32]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117) [spring-web-5.3.25.jar!/:5.3.25]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.springframework.web.filter.CharacterEncodingFilter.doFilterInternal(CharacterEncodingFilter.java:201) [spring-web-5.3.25.jar!/:5.3.25]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117) [spring-web-5.3.25.jar!/:5.3.25]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153) [tomcat-embed-core-9.0.86.jar!/:?]
	at ru.dynamika.web.filter.ContentCachingFilter.doFilter(ContentCachingFilter.java:17) [classes!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:102) [spring-web-5.3.25.jar!/:5.3.25]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153) [tomcat-embed-core-9.0.86.jar!/:?]
	at ru.dynamika.web.filter.CORSFilter.doFilter(CORSFilter.java:76) [classes!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153) [tomcat-embed-core-9.0.86.jar!/:?]
	at net.bull.javamelody.MonitoringFilter.doFilter(MonitoringFilter.java:239) [javamelody-core-1.91.0.jar!/:1.91.0]
	at net.bull.javamelody.MonitoringFilter.doFilter(MonitoringFilter.java:215) [javamelody-core-1.91.0.jar!/:1.91.0]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.springframework.boot.actuate.metrics.web.servlet.WebMvcMetricsFilter.doFilterInternal(WebMvcMetricsFilter.java:96) [spring-boot-actuator-2.7.18.jar!/:2.7.18]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:117) [spring-web-5.3.25.jar!/:5.3.25]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:153) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.StandardWrapperValve.invoke(StandardWrapperValve.java:168) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.StandardContextValve.invoke(StandardContextValve.java:90) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.authenticator.AuthenticatorBase.invoke(AuthenticatorBase.java:481) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.StandardHostValve.invoke(StandardHostValve.java:130) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.valves.ErrorReportValve.invoke(ErrorReportValve.java:93) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.core.StandardEngineValve.invoke(StandardEngineValve.java:74) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.catalina.connector.CoyoteAdapter.service(CoyoteAdapter.java:346) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.coyote.http11.Http11Processor.service(Http11Processor.java:390) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.coyote.AbstractProcessorLight.process(AbstractProcessorLight.java:63) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.coyote.AbstractProtocol$ConnectionHandler.process(AbstractProtocol.java:928) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.tomcat.util.net.NioEndpoint$SocketProcessor.doRun(NioEndpoint.java:1794) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.tomcat.util.net.SocketProcessorBase.run(SocketProcessorBase.java:52) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.tomcat.util.threads.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1191) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.tomcat.util.threads.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:659) [tomcat-embed-core-9.0.86.jar!/:?]
	at org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:63) [tomcat-embed-core-9.0.86.jar!/:?]
	at java.lang.Thread.run(Thread.java:750) [?:1.8.0_441]
26.12.2025 16:41:42,718  INFO [http-nio-1080-exec-9][IncomingRequestLogInterceptor] Incoming request: http://localhost:1080/dynamika/json/get_view_data (3773 ms)




    @Override
    public void downloadCardReestr(String clientExtId, String username, String contextId) {
        Document document = requestCardReestr(clientExtId, username, contextId, "default", null, false);
        document = requestCardReestr(clientExtId, username, contextId, "validate", "V_VALID", true);
        document = requestCardReestr(clientExtId, username, contextId, "execute", null, true);

        while (hasPendingSaveAsCalls(document)) {
            Document plpDocument = createDocument("OperationInteraction");
            plpDocument.setUser(username);
            plpDocument.setContextId(contextId);
            plpDocument.setAnsCallOper(document.getAnsCallOper());

            ReqCallOper plpReq = new ReqCallOper();
            plpReq.setObjectId(clientExtId);
            plpReq.setOperationName("CL_PRIV_PRI_CARD_REESTR");
            plpReq.setContainingView("VW_CRIT_PRI_ORDER_N_CARD");
            plpReq.setActionType("call");
            plpReq.setCollection("0");
            addReestrVar(plpReq, "ClassId", "OOXML");
            addReestrVar(plpReq, "OperID", "SAVE_AS");
            addReestrVar(plpReq, "type", "PLPCALL");

            plpDocument.setReqCallOper(plpReq);
            document = directABSService.request(plpDocument);

            if (document.getFailure() != null && document.getFailure().getInfo() != null) {
                throw new RuntimeException("SAVE_AS: " + document.getFailure().getInfo());
            }
            logger.info("Реестр SAVE_AS: {}", marshalDocument(document));
        }
    }

    private Document requestCardReestr(String clientExtId, String username, String contextId,
                                       String actionType, String fieldName, boolean withEmbossFields) {
        Document document = createDocument("OperationInteraction");
        document.setUser(username);
        document.setContextId(contextId);

        ReqCallOper req = new ReqCallOper();
        req.setObjectId(clientExtId);
        req.setOperationName("CL_PRIV_PRI_CARD_REESTR");
        req.setActionType(actionType);
        req.setContainingView("VW_CRIT_PRI_ORDER_N_CARD");
        if (StringUtils.hasText(fieldName)) {
            req.setFieldName(fieldName);
        }
        if (withEmbossFields) {
            addReestrField(req, "V_VALID", "");
            addReestrField(req, "V_EMBOSS_LAST_NAME", "");
            addReestrField(req, "V_EMBOSS_NAME", "");
            addReestrField(req, "V_CODE_WORD", "");
        }

        document.setReqCallOper(req);
        document = directABSService.request(document);
        logger.info("Реестр {}: {}", actionType, marshalDocument(document));
        return document;
    }

    private void addReestrField(ReqCallOper req, String name, String value) {
        ReqCallOper.Field field = new ReqCallOper.Field();
        field.setName(name);
        field.setValue(value);
        field.setType("String");
        req.getField().add(field);
    }

    private void addReestrVar(ReqCallOper req, String name, String value) {
        ReqCallOper.Vars var = new ReqCallOper.Vars();
        var.setName(name);
        var.setValue(value);
        req.getVars().add(var);
    }

    private boolean hasPendingSaveAsCalls(Document document) {
        return document.getAnsCallOper() != null
                && document.getAnsCallOper().getCalls() != null
                && !document.getAnsCallOper().getCalls().getCall().isEmpty();
    }
}

