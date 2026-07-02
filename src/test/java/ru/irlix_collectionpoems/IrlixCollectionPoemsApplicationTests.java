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







DTO (маппинг XML → Java)
Complexes.java (корневой элемент <complexes>)
package dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

import java.util.List;

@Data
public class Complexes {

    // <complex>...</complex>
    @JacksonXmlProperty(localName = "complex")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<Complex> complexes;
}
Complex.java
package dto;

import lombok.Data;

@Data
public class Complex {

    private Long id;
    private String name;
    private Double latitude;
    private Double longitude;
    private String address;

    // вложенные здания
    private Buildings buildings;
}
Buildings.java
package dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

import java.util.List;

@Data
public class Buildings {

    @JacksonXmlProperty(localName = "building")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<Building> buildings;
}
Building.java
package dto;

import lombok.Data;

@Data
public class Building {

    private Long id;
    private String name;
    private Integer floors;

    // вложенные квартиры
    private Flats flats;
}
Flats.java
package dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

import java.util.List;

@Data
public class Flats {

    @JacksonXmlProperty(localName = "flat")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<Flat> flats;
}
Flat.java
package dto;

import lombok.Data;

@Data
public class Flat {

    private Long flat_id;
    private Integer apartment;
    private Integer floor;
    private Integer room;
    private Long price;
    private Double area;
}
🌐 2. Сервис (загрузка + парсинг XML)
package service;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import dto.Complexes;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class XmlImportService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final XmlMapper xmlMapper = new XmlMapper();

    /**
     * Загружает XML по URL и парсит в Java-объекты
     */
    public Complexes loadFromUrl(String url) {
        try {
            // 1. скачиваем XML как строку
            String xml = restTemplate.getForObject(url, String.class);

            // 2. парсим XML → Java
            return xmlMapper.readValue(xml, Complexes.class);

        } catch (Exception e) {
            throw new RuntimeException("Ошибка загрузки или парсинга XML", e);
        }
    }

    /**
     * Парсинг XML из строки (например, если файл локальный)
     */
    public Complexes loadFromString(String xml) {
        try {
            return xmlMapper.readValue(xml, Complexes.class);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка парсинга XML", e);
        }
    }
}
🎮 3. Контроллер (для проверки)
package controller;

import dto.Complex;
import dto.Complexes;
import dto.Flat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import service.XmlImportService;

import java.util.List;

@RestController
public class ImportController {

    private final XmlImportService service;

    public ImportController(XmlImportService service) {
        this.service = service;
    }

    /**
     * Проверочный endpoint
     */
    @GetMapping("/import")
    public String importData() {

        // если файл локальный — можешь читать через Files.readString(...)
        String url = "ВСТАВЬ_ССЫЛКУ_ИЛИ_УБЕРИ_ИСПОЛЬЗУЙ_loadFromString";

        Complexes complexes = service.loadFromUrl(url);

        // считаем количество квартир
        int totalFlats = complexes.getComplexes().stream()
                .flatMap(c -> c.getBuildings().getBuildings().stream())
                .flatMap(b -> b.getFlats().getFlats().stream())
                .toList()
                .size();

        return "Загружено квартир: " + totalFlats;
    }

    /**
     * Получить все квартиры (для наглядности)
     */
    @GetMapping("/flats")
    public List<Flat> getFlats() {

        String url = "ВСТАВЬ_ССЫЛКУ";

        Complexes complexes = service.loadFromUrl(url);

        return complexes.getComplexes().stream()
                .flatMap(c -> c.getBuildings().getBuildings().stream())
                .flatMap(b -> b.getFlats().getFlats().stream())
                .toList();
    }
}
▶️ 4. Главный класс
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RealEstateImporterApplication {

    public static void main(String[] args) {
        SpringApplication.run(RealEstateImporterApplication.class, args);
    }
}


1) Новый метод validate для P_COMMENT
private void sendCommentToIbso(String clientId, String comment, String username, String contextId) {
    Document document = createDocument("OperationInteraction");
    document.setUser(username);
    document.setContextId(contextId);

    ReqCallOper reqCallOper = new ReqCallOper();
    reqCallOper.setObjectId(clientId);
    reqCallOper.setFieldName("P_COMMENT");
    reqCallOper.setActionType("validate");
    reqCallOper.setOperationName("CL_PRIV_DNM_NEW_DOC");

    ReqCallOper.Field commentField = new ReqCallOper.Field();
    commentField.setName("P_COMMENT");
    commentField.setValue(comment);
    commentField.setType("VARCHAR2"); // если у вас String используется везде, можно String, но лучше VARCHAR2

    ReqCallOper.Field valid = new ReqCallOper.Field();
    valid.setName("V_VALID");
    valid.setType("String");

    ReqCallOper.Field documentTypeField = new ReqCallOper.Field();
    documentTypeField.setName("DNM_ATTACH");
    documentTypeField.setType("DNM_ATTACH_FILES");

    reqCallOper.getField().add(commentField);
    reqCallOper.getField().add(valid);
    reqCallOper.getField().add(documentTypeField);

    document.setReqCallOper(reqCallOper);

    logger.info("Отправка комментария (validate) в ИБСО: {}", marshalDocument(document));
    Document resultDoc = directABSService.request(document);
    logger.info("Ответ на validate комментария из ИБСО: {}", marshalDocument(resultDoc));
}
2) Вставить вызов этого метода в поток перед execute
В методе uploadDocumentsToIbso(...) после sendFileTypeToIbso(...):
String contextId = UUID.randomUUID().toString();
sendDefaultValidate(client.getExtId(), username, contextId);
sendFileTypeToIbso(client.getExtId(), docTypeId, typeName, username, contextId);

// ДОБАВИТЬ ЭТО:
sendCommentToIbso(client.getExtId(), AUTO_COMMENT, username, contextId);

sendDocumentsToIbso(idNameMap, client.getExtId(), username, contextId);
3) execute оставить с P_COMMENT (лучше не убирать)
В sendDocumentToIbso(...):
ReqCallOper.Field commentField = new ReqCallOper.Field();
commentField.setName("P_COMMENT");
commentField.setValue(AUTO_COMMENT);
commentField.setType("VARCHAR2");
И оставляй его в reqCallOper.getField().add(commentField); — это не мешает, а чаще помогает.
4) (Опционально, но полезно) Проверка результата AnsCallOper
Сейчас у тебя проверка только resultDoc.getFailure(). Добавь еще:
if (resultDoc.getAnsCallOper() != null && resultDoc.getAnsCallOper().getFailure() != null) {
    throw new RuntimeException("Ошибка при загрузке документа в АБС: "
            + resultDoc.getAnsCallOper().getFailure().getInfo());
}





package ru.dynamika.findelivery.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.dynamika.data.developer.entities.findelivery.Client;
import ru.dynamika.data.developer.entities.findelivery.Documents;
import ru.dynamika.data.developer.entities.findelivery.Order;
import ru.dynamika.findelivery.ibso.IbsoService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FindostavkaCallbackServiceImplTest {

    @Mock
    private IbsoService ibsoService;

    @InjectMocks
    private FindostavkaCallbackServiceImpl findostavkaCallbackService;

    @Test
    @DisplayName("Не отправляет документы, если у заказа нет клиента")
    void copyDocumentsToIbso_shouldSkip_whenClientIsNull() {
        Order order = buildOrder(null, List.of(doc("my_passport_scan.pdf")));

        findostavkaCallbackService.copyDocumentsToIbso(order);

        verify(ibsoService, never()).uploadDocumentsToIbso(anyList(), eq(null), eq("10"), eq("BOLOTOVA"), eq("Паспорт"));
    }

    @Test
    @DisplayName("Не отправляет документы, если extId клиента пустой")
    void copyDocumentsToIbso_shouldSkip_whenClientExtIdIsBlank() {
        Client client = new Client();
        client.setExtId(" ");
        Order order = buildOrder(client, List.of(doc("my_passport_scan.pdf")));

        findostavkaCallbackService.copyDocumentsToIbso(order);

        verify(ibsoService, never()).uploadDocumentsToIbso(anyList(), eq(client), eq("10"), eq("BOLOTOVA"), eq("Паспорт"));
    }

    @Test
    @DisplayName("Не отправляет документы, если список документов пустой или null")
    void copyDocumentsToIbso_shouldSkip_whenDocumentsAbsent() {
        Client client = client("1");

        findostavkaCallbackService.copyDocumentsToIbso(buildOrder(client, null));
        findostavkaCallbackService.copyDocumentsToIbso(buildOrder(client, List.of()));

        verify(ibsoService, never()).uploadDocumentsToIbso(anyList(), eq(client), eq("10"), eq("BOLOTOVA"), eq("Паспорт"));
    }

    @Test
    @DisplayName("Игнорирует null/пустые/checklist документы и отправляет остальные")
    void copyDocumentsToIbso_shouldIgnoreInvalidDocs_andSendValidOnes() {
        Client client = client("1");
        Order order = buildOrder(
                client,
                List.of(
                        null,
                        doc(null),
                        doc(""),
                        doc("checklist.txt"),
                        doc("my_passport_scan.pdf"),
                        doc("some_other_info.docx")
                )
        );
        when(ibsoService.getFileTypes()).thenReturn(fileTypesPassportAndOther());

        findostavkaCallbackService.copyDocumentsToIbso(order);

        assertNotNull(order.getUser());
        verify(ibsoService).uploadDocumentsToIbso(
                argThat(docs -> docs.size() == 1 && "my_passport_scan.pdf".equals(docs.get(0).getFileName())),
                eq(client),
                eq("10"),
                eq("BOLOTOVA"),
                eq("Паспорт")
        );
        verify(ibsoService).uploadDocumentsToIbso(
                argThat(docs -> docs.size() == 1 && "some_other_info.docx".equals(docs.get(0).getFileName())),
                eq(client),
                eq("30"),
                eq("BOLOTOVA"),
                eq("Прочее")
        );
    }

@Test
    @DisplayName("Отправляет документы во все группы при полном наборе типов")
    void copyDocumentsToIbso_shouldSendAllDocGroups() {
        Client client = client("1");
        Order order = buildOrder(
                client,
                List.of(
                        doc("passport_1.pdf"),
                        doc("zajavlenie_1.pdf"),
                        doc("photoklienta_1.jpg"),
                        doc("raspiska_1.pdf"),
                        doc("personaldann_1.pdf"),
                        doc("other_1.docx")
                )
        );
        when(ibsoService.getFileTypes()).thenReturn(fileTypesAll());

        findostavkaCallbackService.copyDocumentsToIbso(order);

        verify(ibsoService).uploadDocumentsToIbso(anyList(), eq(client), eq("10"), eq("BOLOTOVA"), eq("Паспорт"));
        verify(ibsoService).uploadDocumentsToIbso(anyList(), eq(client), eq("11"), eq("BOLOTOVA"), eq("Заявление"));
        verify(ibsoService).uploadDocumentsToIbso(anyList(), eq(client), eq("12"), eq("BOLOTOVA"), eq("Фото клиента"));
        verify(ibsoService).uploadDocumentsToIbso(anyList(), eq(client), eq("13"), eq("BOLOTOVA"), eq("Расписка"));
        verify(ibsoService).uploadDocumentsToIbso(anyList(), eq(client), eq("14"), eq("BOLOTOVA"), eq("Персональные данные"));
        verify(ibsoService).uploadDocumentsToIbso(anyList(), eq(client), eq("30"), eq("BOLOTOVA"), eq("Прочее"));
        verify(ibsoService, times(6)).uploadDocumentsToIbso(anyList(), eq(client), argThat(id -> id != null && !id.isBlank()), eq("BOLOTOVA"), argThat(name -> name != null && !name.isBlank()));
    }

    @Test
    @DisplayName("Не отправляет группу, если код типа не найден в справочнике ИБСО")
    void copyDocumentsToIbso_shouldSkipGroup_whenTypeNotFound() {
        Client client = client("1");
        Order order = buildOrder(client, List.of(doc("my_passport_scan.pdf")));
        when(ibsoService.getFileTypes()).thenReturn(List.of(Map.of("code", "FM_OTHER", "ID", "30", "name", "Прочее")));

        findostavkaCallbackService.copyDocumentsToIbso(order);

        verify(ibsoService, never()).uploadDocumentsToIbso(anyList(), eq(client), eq("10"), eq("BOLOTOVA"), eq("Паспорт"));
    }

    @Test
    @DisplayName("Не пробрасывает исключение, если ИБСО не доступно")
    void copyDocumentsToIbso_shouldNotThrow_whenIbsoFails() {
        Client client = client("1");
        Order order = buildOrder(client, List.of(doc("my_passport_scan.pdf")));
        when(ibsoService.getFileTypes()).thenReturn(fileTypesPassportAndOther());
        when(ibsoService.uploadDocumentsToIbso(anyList(), eq(client), eq("10"), eq("BOLOTOVA"), eq("Паспорт")))
                .thenThrow(new RuntimeException("IBSO temporary error"));

        findostavkaCallbackService.copyDocumentsToIbso(order);

        verify(ibsoService).uploadDocumentsToIbso(anyList(), eq(client), eq("10"), eq("BOLOTOVA"), eq("Паспорт"));
    }

    private static Order buildOrder(Client client, List<Documents> documents) {
        Order order = new Order();
        order.setId("order-1");
        order.setClient(client);
        order.setDocuments(documents);
        return order;
    }

    private static Client client(String extId) {
        Client client = new Client();
        client.setExtId(extId);
        return client;
    }

    private static Documents doc(String fileName) {
        return Documents.builder().fileName(fileName).build();
    }

    private static List<Map<String, String>> fileTypesPassportAndOther() {
        return List.of(
                Map.of("code", "PASSPORT", "ID", "10", "name", "Паспорт"),
                Map.of("code", "FM_OTHER", "ID", "30", "name", "Прочее")
        );
    }
private static List<Map<String, String>> fileTypesAll() {
        return List.of(
                Map.of("code", "PASSPORT", "ID", "10", "name", "Паспорт"),
                Map.of("code", "BANK_SPRAV", "ID", "11", "name", "Заявление"),
                Map.of("code", "FOTO_FACE", "ID", "12", "name", "Фото клиента"),
                Map.of("code", "REC", "ID", "13", "name", "Расписка"),
                Map.of("code", "SVEDENIYA_BANK", "ID", "14", "name", "Персональные данные"),
                Map.of("code", "FM_OTHER", "ID", "30", "name", "Прочее")
        );
    }
}

public void copyDocumentsToIbso(Order order) {
    try {
        Client client = order.getClient();
        User user = order.getUser();

        if (client == null  !StringUtils.hasText(client.getExtId())) {
            return;
        }

        if (user == null  !StringUtils.hasText(user.getUserName())) {
            log.warn("У заказа {} отсутствует пользователь", order.getId());
            return;
        }

        ...
        
        sendDocGroup(passportDocs, client, DOC_TYPE_PASSPORT, user.getUserName());
private static Order buildOrder(Client client, List<Documents> documents) {
    Order order = new Order();
    order.setId("order-1");
    order.setClient(client);
    order.setDocuments(documents);

    User user = new User();
    user.setUserName("TEST_USER");

    order.setUser(user);

    return order;
}

verify(ibsoService).uploadDocumentsToIbso(
    anyList(),
    eq(client),
    eq("10"),
    anyString(),
    eq("Паспорт")
);

@Component
@Slf4j
public class OrderCreateInCard extends OrderCreate {

    @Autowired
    private IbsoService ibsoService;

    @Autowired
    private CardOrderService cardOrderService;

    @Override
    public OperationResponseV3 defaultValidate(RequestV3 rp) {
        super.defaultValidate(rp, Order.class);

        CardOrder cardOrder;
        try {
            cardOrder = cardOrderService.findById(rp.getObjectIdList().get(0));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        rp.getOrCreateField("clients.fio").setSingleValue(cardOrder.getClient());
            rp.getOrCreateField("clients").setSingleValue(cardOrder.getIdComunda());

            String clientPhone = ibsoService.getClientPhoneById(cardOrder.getIdComunda());
            String formattedPhone = formatPhoneTo16Symbols(clientPhone);
            rp.getOrCreateField("entity.phone").setSingleValue(formattedPhone);

            if (StringUtils.hasText(cardOrder.getCity())) {
                Iterable<DeliveryPoint> allPoints = repo.findAll(DeliveryPoint[].class);
                String matchedAddressId = StreamSupport.stream(allPoints.spliterator(), false)
                        .filter(dp -> cardOrder.getCity().equalsIgnoreCase(dp.getLabel()))
                        .map(DeliveryPoint::getId)
                        .findFirst()
                        .orElse(null);

                if (matchedAddressId != null) {
                    rp.getOrCreateField("deliveryAddress").setSingleValue(matchedAddressId);
                    rp.getOrCreateField("deliveryAddress").setReadOnly(false);
                }
            }



        rp.setObjectId(null);
        return null;
    }

    @Override
    public OperationResponseV3 execute(RequestV3 rp) {
        String phone = rp.getOrCreateField("entity.phone").getSingleValue();
        if (StringUtils.hasText(phone) && phone.length() != 16) {
            rp.getOrCreateField("entity.phone").setSingleValue(formatPhoneTo16Symbols(phone));
        }

        return super.execute(rp);
    }


    private String formatPhoneTo16Symbols(String rawPhone) {
        if (!StringUtils.hasText(rawPhone)) {
            return "+7 (___) ___-__-__";
        }
        String digits = rawPhone.replaceAll("\\D", "");

        if ((digits.startsWith("7") || digits.startsWith("8")) && digits.length() > 10) {
            digits = digits.substring(1);
        }

        if (digits.length() < 10) {
            return rawPhone;
        }

        String code = digits.substring(0, 3);
        String tri = digits.substring(3, 6);
        String di1 = digits.substring(6, 8);
        String di2 = digits.substring(8, 10);

        return String.format("+7 (%s) %s-%s-%s", code, tri, di1, di2);
    }
}


		Service
@RequiredArgsConstructor
public class CardOrderService {

    @Autowired
    private DataControllerEntityRepository repo;

    @Autowired
    private QueryApiTemplate query;

    @Autowired
    private IbsoService ibsoService;

    private final DateTimeFormatter FORMATTER_DATE = DateTimeFormatter.ofPattern("[yyyy-MM-dd][dd.MM.yyyy]");
    private final String DEFAULT_DATE = String.valueOf(LocalDate.now());

    public void downloadAndSaveOrders() throws ClassNotFoundException {
        List<Map<String, String>> remoteRecords = ibsoService.getCardOrders();

        for (Map<String, String> map : remoteRecords) {
            String extId = map.get("extId");

            if (extId == null || extId.isBlank()) {
                continue;
            }

            CardOrder cardOrder = findByExtId(extId);

            if (cardOrder == null) {
                cardOrder = new CardOrder();
                cardOrder.setExtId(extId);
            }
            cardOrder.setExtId(map.get("extId"));
            cardOrder.setNum(map.get("num"));
            cardOrder.setStatus(map.get("status"));
            cardOrder.setClient(map.get("client"));
            cardOrder.setPaySystem(map.get("paySystem"));
            cardOrder.setTarif(map.get("tarif"));
            cardOrder.setCity(map.get("city"));
            cardOrder.setOffice(map.get("office"));
            String dateStr = map.get("localDate");
            cardOrder.setLocalDate(dateStr != null ? LocalDate.parse(dateStr, FORMATTER_DATE)
                    : LocalDate.parse(DEFAULT_DATE, FORMATTER_DATE));
            cardOrder.setReestr(map.get("reestr"));
            cardOrder.setCommentDetail(map.get("commentDetail"));
            cardOrder.setIdComunda(map.get("clientId"));

            String flagStr = map.get("generateFlag");
            cardOrder.setGenerateFlag(flagStr != null ? Boolean.valueOf(flagStr) : null);

            repo.save(cardOrder);
        }
    }

    private CardOrder findByExtId(String extId) throws ClassNotFoundException {
        return query.queryForObject("select * from fdelivery.cardorder where extid = :extid", CardOrder.class, "extid", extId)
                .orElse(null);
    }

    public CardOrder findById(String id) throws ClassNotFoundException {
        return query.queryForObject("select * from fdelivery.cardorder where id = :id", CardOrder.class, "id", id)
                .orElse(null);
    }

	DeliveryPointSelectHandler
        return ("ru_dynamika_findelivery_units_pages_operations_OrderCreate".equals(reqGetData.getOperation()) ||
                "ru_dynamika_findelivery_units_pages_operations_OrderEdit".equals(reqGetData.getOperation()) ||
                "ru_dynamika_findelivery_units_pages_operations_OrderCreateIBSO".equals(reqGetData.getOperation()) ||
                "ru_dynamika_findelivery_units_pages_operations_GetOrdersReport".equals(reqGetData.getOperation())) &&
                "deliveryAddress".equals(reqGetData.getParam());


		// DeliveryPointSelectHandler.canHandle()
"ru_dynamika_findelivery_units_pages_operations_OrderCreateInCard".equals(reqGetData.getOperation())


	Override
public OperationResponseV3 defaultValidate(RequestV3 rp) {
    CardOrder cardOrder = cardOrderService.findById(rp.getObjectIdList().get(0));

    // 1. Клиент — через ИБСО, в clients кладём внутренний id (как IBSO-операция)
    Client client = ibsoService.findClientByExtId(cardOrder.getIdComunda());
    rp.getOrCreateField("clients.fio").setSingleValue(client.getFio());
    rp.getOrCreateField("clients").setSingleValue(client.getId());

    // 2. Телефон
    String phone = ibsoService.getClientPhoneById(cardOrder.getIdComunda()); // метод нужно добавить
    rp.getOrCreateField("entity.phone").setSingleValue(formatPhoneTo16Symbols(phone));

    rp.setObjectId(null);

    // 3. СНАЧАЛА базовая инициализация OrderCreate
    OperationResponseV3 response = super.defaultValidate(rp);

    // 4. ПОТОМ заглушка продукта (2-й из списка) + разблокировка города
    var items = rp.getOrCreateField("entity.productCode").getItems();
    if (items != null && items.size() >= 2) {
        rp.getOrCreateField("entity.productCode").setSingleValue(items.get(1).getValue());
        rp.getOrCreateField("deliveryAddress").setReadOnly(false);
    }

    // 5. Автозаполнение города
    if (StringUtils.hasText(cardOrder.getCity())) {
        repo.findAll(DeliveryPoint[].class).forEach(dp -> {
            if (cardOrder.getCity().equalsIgnoreCase(dp.getLabel())) {
                rp.getOrCreateField("deliveryAddress").setSingleValue(dp.getId());
                rp.getOrCreateField("deliveryAddress.name").setSingleValue(dp.getLabel());
            }
        });
    }

    return response;
}

		2. execute — нужен override как в OrderCreateIBSO
Базовый OrderCreate.execute делает findClientByExtId(clients), а ты в clients кладёшь idComunda (extId). OrderCreateIBSO это обходит:
entity.setClient(repo.findById(rp.getOrCreateField("clients").getSingleValue(), Client.class)...);
Скопируй execute из OrderCreateIBSO, не вызывай super.execute().
3. getClientPhoneById — в проекте нет
В IbsoService / IbsoServiceImpl такого метода нет. В findFilteredClients телефон вообще пустой (phone = ""). Метод нужно добавить в IbsoService и реализовать через запрос клиента по clientId в ИБСО.
Ошибки в CardOrderService
Маппинг полей из getCardOrders() не совпадает с тем, что реально возвращает IbsoServiceImpl:
Твой ключ
Реальный ключ из ИБСО
extId
id
num
orderNumber
status
statusName
client
clientName
tarif
tariff
localDate
dateTime
reestr
registryFileName
commentDetail
comment
generateFlag
isGenerated
Из-за этого findById может находить запись, но idComunda, city, client — пустые/null.
Фикс маппинга:
cardOrder.setExtId(map.get("id"));
cardOrder.setNum(map.get("orderNumber"));
cardOrder.setStatus(map.get("statusName"));
cardOrder.setClient(map.get("clientName"));
cardOrder.setPaySystem(map.get("paySystem"));
cardOrder.setTarif(map.get("tariff"));
cardOrder.setCity(map.get("city"));
cardOrder.setOffice(map.get("office"));
cardOrder.setIdComunda(map.get("clientId"));
// dateTime, comment, isGenerated — аналогично
Чеклист
#
Проблема
Решение
1
Пустой список городов
Добавить операцию в DeliveryPointSelectHandler
2
Нет продуктов / форма «ломается»
Вызывать super.defaultValidate(rp), не super.defaultValidate(rp, Order.class)
3
Продукт не выставляется
После super — 2-й элемент из getItems()
4
Город заблокирован
После выбора продукта: deliveryAddress.setReadOnly(false)
5
Клиент при сохранении
Override execute как в OrderCreateIBSO
6
Телефон
Реализовать getClientPhoneById в IbsoService
7
Данные заявки пустые
Исправить маппинг в CardOrderService
8
Форма
Скопировать форму от OrderCreateIBSO (поле deliveryAddress типа Object с колонками name/address)


	 Формат под маску (главное)
private String formatPhoneTo16Symbols(String rawPhone) {
    if (!StringUtils.hasText(rawPhone)) {
        return null; // не подставляй placeholder с пробелами
    }
    String digits = rawPhone.replaceAll("\\D", "");
    if ((digits.startsWith("7") || digits.startsWith("8")) && digits.length() > 10) {
        digits = digits.substring(1);
    }
    if (digits.length() < 10) {
        return null;
    }
    return String.format("+7(%s)%s-%s-%s",
            digits.substring(0, 3),
            digits.substring(3, 6),
            digits.substring(6, 8),
            digits.substring(8, 10));
}
2. Нормализация в execute перед checkFields
@Override
public OperationResponseV3 execute(RequestV3 rp) {
    String phone = rp.getOrCreateField("entity.phone").getSingleValue();
    String normalized = formatPhoneTo16Symbols(phone);
    if (normalized != null) {
        rp.getOrCreateField("entity.phone").setSingleValue(normalized);
    }
    return super.execute(rp);
}
3. Если после фикса формата всё ещё null при сохранении
Маска иногда не отправляет значение, пока пользователь не трогал поле. Тогда в defaultValidate сохрани телефон в контекст:
rp.getContext().put("prefilledPhone", formattedPhone);
И в execute:
if (!StringUtils.hasText(rp.getOrCreateField("entity.phone").getSingleValue())) {
    String saved = rp.getContext().getValue("prefilledPhone", String.class);
    if (StringUtils.hasText(saved)) {
        rp.getOrCreateField("entity.phone").setSingleValue(saved);
    }
}
Итог
Причина
Симптом
Формат с пробелами (18 символов)
Видно на форме, валидация не проходит
Маска не коммитит значение без ввода
После правки одной цифры всё работает



@Override
public OperationResponseV3 defaultValidate(RequestV3 rp) {

    try {

        if (rp.getObjectIdList().size() > 1) {
            throw new RuntimeException("Выбрано более 1 заявки");
        }


        CardOrder cardOrder = cardOrderService.findById(
                rp.getObjectIdList().get(0)
        );


        // дата
        rp.getOrCreateField("createOrderDate")
                .setSingleValue(LocalDateTime.now().toString());


        // продукт
        cardOrderService.getActiveProductCode()
                .ifPresent(productCode -> {

                    rp.getOrCreateField("entity.productCode")
                            .setSingleValue(productCode.getId());

                    rp.getOrCreateField("entity.productCode")
                            .setItems(List.of(
                                    new Field.Item(
                                            productCode.getId(),
                                            productCode.getLabel()
                                    )
                            ));
                });



        // клиент
        rp.getOrCreateField("clients.fio")
                .setSingleValue(cardOrder.getClient());


        rp.getOrCreateField("clients")
                .setSingleValue(cardOrder.getIdComunda());



        // телефон
        String phone = ibsoService.getClientPhoneById(
                cardOrder.getIdComunda()
        );

        rp.getOrCreateField("entity.phone")
                .setSingleValue(formatPhone(phone));



        // города как в родителе
        rp.getOrCreateField("deliveryAddress")
                .setItems(
                    StreamSupport.stream(
                        repo.findAll(DeliveryPoint[].class)
                        .spliterator(),
                        false
                    )
                    .map(dp -> new Field.Item(
                            dp.getId(),
                            dp.getLabel()
                    ))
                    .toList()
                );


        rp.getOrCreateField("deliveryAddress")
                .setReadOnly(false);



    } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
    }


    return super.defaultValidate(rp, Order.class);
}

public void sendCardToOeb(
        String cardId,
        String status,
        String idComunda,
        String clientId
) {

    Document document = createDocument("OperationInteraction");

    ReqCallOper reqCallOper = new ReqCallOper();

    reqCallOper.setObjectId(cardId);
    reqCallOper.setOperationName("O_CARD_TO_OEB");
    reqCallOper.setActionType("execute");


    ReqCallOper.Field statusField = new ReqCallOper.Field();
    statusField.setName("P_STATUS");
    statusField.setValue(status);
    statusField.setType("String");


    ReqCallOper.Field comundaField = new ReqCallOper.Field();
    comundaField.setName("P_ID_COMUNDA");
    comundaField.setValue(idComunda);
    comundaField.setType("String");


    ReqCallOper.Field clientField = new ReqCallOper.Field();
    clientField.setName("P_CLIENT");
    clientField.setValue(clientId);
    clientField.setType("Object");


    ReqCallOper.Field rejectionField = new ReqCallOper.Field();
    rejectionField.setName("P_REJECTION");
    rejectionField.setValue("");
    rejectionField.setType("String");


    reqCallOper.getField().add(statusField);
    reqCallOper.getField().add(comundaField);
    reqCallOper.getField().add(clientField);
    reqCallOper.getField().add(rejectionField);


    document.setReqCallOper(reqCallOper);


    Document result = directABSService.request(document);


    if (result.getFailure() != null) {
        throw new RuntimeException(
            "Ошибка передачи в ОЭБ: "
            + result.getFailure().getInfo()
        );
    }
}


















	2026-06-24 16:00:49,720 INFO  ru.dynamika.core.service.impl.DocumentMarshaller : Unmarshalling document: 
<?xml version="1.0" encoding="ISO-8859-5"?>
<Document id="#" product="#" user="#">
  <Failure>
    <ReqName>AnsCallOper</ReqName>
    <oper>[CIT_INTERFACE]::[DNM_CALL_OPER]</oper>
    <info> пакет не создан
ORA-06512: на  &quot;IBS.MESSAGE&quot;, line 51
ORA-06512: на  &quot;IBS.Z$CIT_INTERFACE_DNM_CALL_OPER&quot;, line 1406
</info>
  </Failure>
</Document>
@Component
@Slf4j
public class SendOrderForOeb extends AutoInitOperation<CardOrder> {

    @Autowired
    private IbsoServiceImpl ibsoService;


    @Override
    public OperationResponseV3 defaultValidate(RequestV3 rp) {
        return null;
    }

    @Override
    public OperationResponseV3 validate(RequestV3 rp) {
        return null;
    }

    @Override
    public OperationResponseV3 execute(RequestV3 rp) {
        CardOrder cardOrder = repo.findById(rp.getObjectIdList().get(0), CardOrder.class).orElseThrow(() -> new RuntimeException("Заявка не найдена"));
        log.info("Отправка заявки {} в ОЭБ", cardOrder.getExtId());
        ibsoService.sendCardToOeb(rp.getObjectId(), rp.getUser(), rp.getContext().toString(), cardOrder.getStatus(), cardOrder.getIdComunda(), cardOrder.getClient());
        return null;
    }
}


    public void sendCardToOeb(String objectId, String username, String contextId, String status, String idComunda, String clientId) {
        Document document = createDocument("OperationInteraction");
        document.setUser(username);
        document.setContextId(contextId);

        ReqCallOper reqCallOper = new ReqCallOper();

        reqCallOper.setObjectId(objectId);
        reqCallOper.setOperationName("DNM_TO_OEB_GR");
        reqCallOper.setActionType("execute");
        reqCallOper.setContainingView("VW_CRIT_PRI_ORDER_N_CARD");

        ReqCallOper.Field statusField = new ReqCallOper.Field();
        statusField.setName("P_STATUS");
        statusField.setValue(status);
        statusField.setType("String");

        ReqCallOper.Field comundaField = new ReqCallOper.Field();
        comundaField.setName("P_ID_COMUNDA");
        comundaField.setValue(idComunda);
        comundaField.setType("String");

        ReqCallOper.Field clientField = new ReqCallOper.Field();
        clientField.setName("P_CLIENT");
        clientField.setValue(clientId);
        clientField.setType("String");

        ReqCallOper.Field rejectionField = new ReqCallOper.Field();
        rejectionField.setName("P_REJECTION");
        rejectionField.setValue("");
        rejectionField.setType("String");

        reqCallOper.getField().add(statusField);
        reqCallOper.getField().add(comundaField);
        reqCallOper.getField().add(clientField);
        reqCallOper.getField().add(rejectionField);

        document.setReqCallOper(reqCallOper);

        logger.info("Отправка карты в ОЭБ: {}", marshalDocument(document));

        Document result = directABSService.request(document);

        if (result.getFailure() != null) {
            throw new RuntimeException("Ошибка передачи в ОЭБ: " + result.getFailure().getInfo());
        }
    }
}























	package body Z$PRI_ORDER_N_CARD_64983611976 is
--#section PRIVATE 4
--#section VALIDSYS 7
--# 1,1
	procedure O_CARD_TO_OEB_VALIDATE(THIS IN OUT NOCOPY number,PLP$CLASS IN varchar2,P_MESSAGE IN OUT NOCOPY varchar2,P_INFO IN OUT NOCOPY varchar2,P_STATUS IN OUT NOCOPY number,P_ID_COMUNDA IN OUT NOCOPY VARCHAR2,P_CLIENT IN OUT NOCOPY number,P_REJECTION IN OUT NOCOPY VARCHAR2) is
		plp$class$	varchar2(128);
		plp$var$	Z#PRI_ORDER_N_CARD#INTERFACE.CLASS#PRI_ORDER_N_CARD;
--#section VALIDATE 14
		procedure Get$Obj$This is
			plp$id$	number;
		begin if THIS is null then return; end if;
			plp$id$ := THIS;
			select C_STATUS, C_ID_COMUNDA, C_CLIENT
			  into plp$var$.A#STATUS, plp$var$.A#ID_COMUNDA, plp$var$.A#CLIENT
			  from Z#PRI_ORDER_N_CARD
			 where id=plp$id$;
		exception when NO_DATA_FOUND then
			message.error('EXEC','OBJECT_NOT_FOUND',plp$id$);
		end;
	begin
--#section VALIDSYS
		if plp$CLASS is NULL then
			plp$class$ := Z#PRI_ORDER_N_CARD#INTERFACE.class$(THIS);
		elsif plp$CLASS like '$$$%' then
			plp$class$ := Z#PRI_ORDER_N_CARD#INTERFACE.class$(THIS);
		else plp$class$ := plp$CLASS;
		end if;
		rtl.read(null);
		Get$Obj$This;
--#section VALIDATE
--# 2,2
		if P_MESSAGE = 'DEFAULT' then
--# 3,12
			P_STATUS := plp$var$.A#STATUS;
			P_ID_COMUNDA := plp$var$.A#ID_COMUNDA;
			P_CLIENT := plp$var$.A#CLIENT;
			Z$RUNTIME_CSMD.COMMAND(V_DEST_STR,'reject.Enabled = false');
		elsif P_MESSAGE = 'VALIDATE' then
--# 8,3
			if P_INFO = 'P_REJECTION' and P_REJECTION is not NULL then
--# 9,6
				Z$RUNTIME_CSMD.COMMAND(V_DEST_STR,'reject.Enabled = true');
			else
--# 11,7
				Z$RUNTIME_CSMD.COMMAND(V_DEST_STR,'reject.Enabled = false');
			end if;
		end if;
		return;
	end;
--#section EXECUTESYS 32
--# 1,1
	function O_CARD_TO_OEB_EXECUTE(THIS IN number,PLP$CLASS IN varchar2,P_STATUS IN number,P_ID_COMUNDA IN VARCHAR2,P_CLIENT IN number,P_REJECTION IN VARCHAR2) return VARCHAR2 is
		plp$class$	varchar2(128);
		plp$THIS	number := THIS;
		plp$var$	Z#PRI_ORDER_N_CARD#INTERFACE.CLASS#PRI_ORDER_N_CARD;
		plp$P_ID_COMUNDA	VARCHAR2(300) := P_ID_COMUNDA;
--#section EXECUTE 39
--# 4,2
		RESULT	varchar2(128);
		REFCLIENTDOSSIERIBSO	number;
		REQ	Z$RUNTIME_HTTP_MGR.REQ;
		RESP	Z$RUNTIME_HTTP_MGR.RESP;
		STR_URL	varchar2(1000);
		ANS	varchar2(2000);
		VURL	varchar2(200);
--# 12,2
		DATA	varchar2(200) := '{"variables":{"finddossier":{"value":"pvalue"}}}';
		PSTATUS	varchar2(128);
		JSONTASK	Z$RUNTIME_LIB_JSON.T_JSON_ELEMENT;
		TASKID	varchar2(128);
		procedure Set$Obj$This is
		begin
			if plp$THIS is null then return; end if;
			valmgr.check_readonly;
			update Z#PRI_ORDER_N_CARD set
			     sn=nvl(sn,1)+1, su=rtl.uid$, C_STATUS=plp$var$.A#STATUS, C_COMMENT=plp$var$.A#COMMENT
			    where id=plp$THIS;
		end;
		procedure Get$Obj$This is
			plp$id$	number;
		begin if plp$THIS is null then return; end if;
			plp$id$ := plp$THIS;
			select C_STATUS, C_COMMENT, C_ID_COMUNDA
			  into plp$var$.A#STATUS, plp$var$.A#COMMENT, plp$var$.A#ID_COMUNDA
			  from Z#PRI_ORDER_N_CARD
			 where id=plp$id$;
		exception when NO_DATA_FOUND then
			message.error('EXEC','OBJECT_NOT_FOUND',plp$id$);
		end;
	begin
--#section EXECUTESYS
		if plp$CLASS is NULL then
			plp$class$ := Z#PRI_ORDER_N_CARD#INTERFACE.class$(plp$THIS);
		elsif plp$CLASS like '$$$%' then
			plp$class$ := Z#PRI_ORDER_N_CARD#INTERFACE.class$(plp$THIS);
		else plp$class$ := plp$CLASS;
		end if;
		Z#PRI_ORDER_N_CARD#INTERFACE.lock_object(plp$THIS,'[PRI_ORDER_N_CARD]::[O_CARD_TO_OEB]',plp$class$);
		Get$Obj$This;
--#section EXECUTE
--# 18,23
		REFCLIENTDOSSIERIBSO := P_CLIENT;
		if Z$CL_PRIV_SITE_LIB.SIMPLE_IDENTIFICATION(REFCLIENTDOSSIERIBSO) = 'OK' then
--# 20,12
			plp$var$.A#STATUS := 523475286;
			RESULT := 'Упрощённая идентификация пройдена успешно';
			PSTATUS := 'IMNS_FORM';
		else
--# 24,12
			plp$var$.A#STATUS := 6447314;
			RESULT := 'fail';
			PSTATUS := 'NO_CONFIRM';
		end if;
--# 29,2
		if P_REJECTION is not NULL then
--# 30,12
			plp$var$.A#STATUS := 6447314;
			RESULT := 'fail';
			PSTATUS := 'NO_CONFIRM';
			plp$var$.A#COMMENT := P_REJECTION;
		end if;
--# 35,5
		Z$LBB_EXPORT_DATA_LBB_SITEGT.PUT('simple_identification status: '||PSTATUS,'export');
--# 38,2
		begin
--# 39,3
			Set$Obj$This;
			cache_mgr.cache_set_savepoint ('SP64983611976CARD');
--# 40,16
			plp$P_ID_COMUNDA := plp$var$.A#ID_COMUNDA;
--# 42,5
			if COALESCE(Z$SYSTEM_PARAMS_GET.GET_EXECUTE(NULL,'SYSTEM_PARAMS','PRI_TEST_DB'),'LEXX') = 'LEXX' then
--# 43,11
				VURL := Z$FP_TUNE_LIB.GET_STR_VALUE('DNM_LOCATION_MOVE_PROCESS');
			else
--# 45,11
				VURL := Z$FP_TUNE_LIB.GET_STR_VALUE('DNM_LOCATION_MOVE_PROCESS_TEST');
			end if;
--# 48,5
			if VURL is NULL then
--# 49,9
				Z$LBB_EXPORT_DATA_LBB_SITEGT.PUT('Заявка на выпуск дебетовой карты O_CARD_TO_OEB: Не определен инстанс бизнес-процесса на маршруте! P_ID_COMUNDA: '||plp$P_ID_COMUNDA||' sqlerrm: '||utils.error_stack(false),'export');
--# 51,13
				MESSAGE.APP_ERROR('PRI_ORDER_N_CARD.O_CARD_TO_OEB','Не определен инстанс бизнес-процесса на маршруте!');
			end if;
--# 56,13
			STR_URL := VURL||'/engine-rest/task?processInstanceId='||plp$P_ID_COMUNDA;
			REQ := Z$RUNTIME_HTTP_MGR.BEGIN_REQUEST(STR_URL,'GET','HTTP/1.1');
--# 59,8
			Z$RUNTIME_HTTP_MGR.SET_HEADER(REQ,'Authorization','Basic '||UTL_RAW.CAST_TO_VARCHAR2(UTL_ENCODE.BASE64_ENCODE(UTL_RAW.CAST_TO_RAW('dnm:12345'))));
			Z$RUNTIME_HTTP_MGR.SET_HEADER(REQ,'Content-Type','application/json; charset=utf8');
			Z$RUNTIME_HTTP_MGR.SET_HEADER(REQ,'Accept','application/json');
			Z$RUNTIME_HTTP_MGR.SET_HEADER(REQ,'Method','POST');
--# 64,11
			RESP := Z$RUNTIME_HTTP_MGR.GET_RESPONSE(REQ);
--# 66,5
			if RESP.STATUS_CODE not in ('200','204') then
--# 67,9
				Z$LBB_EXPORT_DATA_LBB_SITEGT.PUT('Ошибка HTTP: '||RESP.STATUS_CODE||': '||RESP.REASON_PHRASE,'export');
			else
--# 69,6
				null;
			end if;
--# 72,6
			Z$RUNTIME_HTTP_MGR.READ_TEXT(RESP,ANS);
			Z$RUNTIME_HTTP_MGR.END_RESPONSE(RESP);
			JSONTASK := Z$RUNTIME_LIB_JSON.PARSEJSON(SUBSTR(ANS,2,LENGTH(ANS)-2));
			TASKID := Z$WEB_DATA_SWAP_LIB_JSON.GET_KEY_VALUE2(JSONTASK,'id',128);
--# 79,10
			DATA := REPLACE(DATA,'pvalue',PSTATUS);
			STR_URL := VURL||'/engine-rest/task/'||TASKID||'/complete';
--# 82,8
			Z$LBB_EXPORT_DATA_LBB_SITEGT.PUT('Передать в ОЭБ: str_url -> '||STR_URL,'export');
--# 84,11
			REQ := Z$RUNTIME_HTTP_MGR.BEGIN_REQUEST(STR_URL,'POST','HTTP/1.1');
--# 86,8
			Z$RUNTIME_HTTP_MGR.SET_HEADER(REQ,'Authorization','Basic '||UTL_RAW.CAST_TO_VARCHAR2(UTL_ENCODE.BASE64_ENCODE(UTL_RAW.CAST_TO_RAW('dnm:12345'))));
			Z$RUNTIME_HTTP_MGR.SET_HEADER(REQ,'Content-Type','application/json; charset=utf8');
			Z$RUNTIME_HTTP_MGR.SET_HEADER(REQ,'Accept','application/json');
			Z$RUNTIME_HTTP_MGR.SET_HEADER(REQ,'Method','POST');
			Z$RUNTIME_HTTP_MGR.SET_HEADER(REQ,'Content-Length',LENGTH(DATA));
			Z$RUNTIME_HTTP_MGR.WRITE_RAW(REQ,UTL_RAW.CAST_TO_RAW(DATA));
--# 93,11
			RESP := Z$RUNTIME_HTTP_MGR.GET_RESPONSE(REQ);
--# 95,5
			if RESP.STATUS_CODE not in ('200','204') then
--# 96,13
				RESULT := RESULT||'. Ошибка бизнес-процесса при передаче в ОЭБ!';
				Z$LBB_EXPORT_DATA_LBB_SITEGT.PUT('Ошибка HTTP: '||RESP.STATUS_CODE||': '||RESP.REASON_PHRASE,'export');
			else
--# 99,13
				RESULT := RESULT||'. Заявка передана в ОЭБ!';
			end if;
--# 102,6
			Z$RUNTIME_HTTP_MGR.READ_TEXT(RESP,ANS);
			Z$RUNTIME_HTTP_MGR.END_RESPONSE(RESP);
--# 105,8
			Z$LBB_EXPORT_DATA_LBB_SITEGT.PUT('двигаем процесс: '||ANS,'export');
		exception
		when Z$RUNTIME_HTTP_MGR.END_OF_BODY then
--# 109,6
			null;
		when others then
		if sqlcode in (-4061,-6508) then raise; end if;
--# 112,7
			cache_mgr.cache_rollback('SP64983611976CARD');
			Z$LBB_EXPORT_DATA_LBB_SITEGT.PUT('Ошибка HTTP(2): '||utils.error_stack(false)||' url '||STR_URL,'export');
			RESULT := RESULT||'. Ошибка бизнес-процесса при передаче в ОЭБ!';
		end;
--# 117,1
		return RESULT;
	end;
end Z$PRI_ORDER_N_CARD_64983611976;









1. Как устроена интеграция в твоём проекте
Все вызовы ИБСО идут через один механизм:
Java → Document (XML) → DirectABSService.request() → ИБСО
Базовый шаблон уже есть в IbsoServiceImpl:
Что делаем
product в Document
Тег запроса
Читаем представление
GetViewData
ReqGetViewData
Вызываем операцию
OperationInteraction
ReqCallOper
Ищем клиента
CLIENT_NEW
ReqFindClient
Рабочий пример вызова операции — загрузка документа (CL_PRIV_DNM_NEW_DOC). Там видна правильная схема:
1.
default — инициализация операции
2.
validate — по полям (если нужно)
3.
execute — выполнение
Это эталон, на который нужно ориентироваться.
2. Главная ошибка: ты вызываешь не ту операцию
В ИБСО у тебя две разные операции:
A) Поштучная — O_CARD_TO_OEB
Вызывается на одной заявке (один objectId).
Именно её нужно дергать из Финдоставки.
B) Списковая (batch) — DNM_TO_OEB_GR
Это массовая операция «ДНМ. Передать в ОЭБ(списочная)».
Внутри она просто в цикле вызывает O_CARD_TO_OEB по выделенным строкам.
DNM_TO_OEB_GR  →  for each selected row  →  O_CARD_TO_OEB(...)
Из внешнего Java-кода нельзя вызывать DNM_TO_OEB_GR как обычный ReqCallOper.
Ошибка пакет не создан на DNM_CALL_OPER как раз об этом: для batch-операции нет нормального пакета для одиночного XML-вызова.
Вывод: в operationName должно быть O_CARD_TO_OEB, а не DNM_TO_OEB_GR.
3. Правильный алгоритм вызова O_CARD_TO_OEB
Шаг 0. Подготовь данные
Из getCardOrders() тебе нужен в первую очередь ID — это extId заявки в ИБСО (например 547774482740).
Важно:
Поле
Что это в ИБСО
Что у тебя сейчас
Проблема
objectId
ID записи PRI_ORDER_N_CARD
rp.getObjectId() — ID в Финдоставке
Неверно
P_STATUS
Числовой ref состояния (C_STATUS)
cardOrder.getStatus() — текст «Подготовлен»
Неверно
P_CLIENT
Числовой ref клиента (C_CLIENT)
cardOrder.getClient() — ФИО
Неверно
P_ID_COMUNDA
ID Camunda-процесса
может быть null
Часто пусто
P_STATUS и P_CLIENT в PL/SQL — это number / Object, не строки с названиями.
Лучший способ: не передавать их руками, а сначала вызвать default — ИБСО сам подтянет значения из объекта:
-- из validate O_CARD_TO_OEB:
P_STATUS     := plp$var$.A#STATUS;      -- из Z#PRI_ORDER_N_CARD
P_ID_COMUNDA := plp$var$.A#ID_COMUNDA;
P_CLIENT     := plp$var$.A#CLIENT;
Шаг 1. default — инициализация операции
Document document = createDocument("OperationInteraction");
document.setUser(username);           // логин оператора в ИБСО
document.setContextId(UUID.randomUUID().toString());  // новый UUID на сессию операции!

ReqCallOper req = new ReqCallOper();
req.setObjectId(ibsoOrderId);         // extId заявки из представления, НЕ id из Финдоставки!
req.setOperationName("O_CARD_TO_OEB");
req.setActionType("default");

document.setReqCallOper(req);
Document defaultResult = directABSService.request(document);
XML будет примерно таким:
<Document product="OperationInteraction" user="operator_login" contextId="uuid-here" ver_xml="2016-05-04" platform="NF">
  <ReqCallOper objectId="547774482740" operationName="O_CARD_TO_OEB" actionType="default"/>
</Document>
В ответе (AnsCallOper) ИБСО вернёт поля P_STATUS, P_ID_COMUNDA, P_CLIENT с правильными значениями. Их нужно взять для execute.
Шаг 2. execute — выполнение
Document document = createDocument("OperationInteraction");
document.setUser(username);
document.setContextId(contextId);   // тот же UUID, что в default!

ReqCallOper req = new ReqCallOper();
req.setObjectId(ibsoOrderId);
req.setOperationName("O_CARD_TO_OEB");
req.setActionType("execute");
req.setContainingView("VW_CRIT_PRI_ORDER_N_CARD");

// Поля из ответа default (или из БД, если сохранил)
ReqCallOper.Field statusField = new ReqCallOper.Field();
statusField.setName("P_STATUS");
statusField.setValue(statusId);      // число! например "6447314"
statusField.setType("Object");

ReqCallOper.Field comundaField = new ReqCallOper.Field();
comundaField.setName("P_ID_COMUNDA");
comundaField.setValue(idComunda);    // строка UUID процесса
comundaField.setType("String");

ReqCallOper.Field clientField = new ReqCallOper.Field();
clientField.setName("P_CLIENT");
clientField.setValue(clientId);      // число! например "229540276331"
clientField.setType("Object");

ReqCallOper.Field rejectionField = new ReqCallOper.Field();
rejectionField.setName("P_REJECTION");
rejectionField.setValue("");         // null / пусто = без отказа
rejectionField.setType("String");

req.getField().add(statusField);
req.getField().add(comundaField);
req.getField().add(clientField);
req.getField().add(rejectionField);

document.setReqCallOper(req);
Document result = directABSService.request(document);
Шаг 3. Проверка ответа
if (result.getFailure() != null) {
    throw new RuntimeException("Ошибка: " + result.getFailure().getInfo());
}
if (result.getAnsCallOper() != null && result.getAnsCallOper().getFailure() != null) {
    throw new RuntimeException("Ошибка операции: " + result.getAnsCallOper().getFailure().getInfo());
}
// успех — в AnsCallOper будет результат execute
4. Что именно сломано в твоём sendCardToOeb
// ❌ ПРОБЛЕМА 1: batch-операция
reqCallOper.setOperationName("DNM_TO_OEB_GR");

// ❌ ПРОБЛЕМА 2: objectId — это ID в Финдоставке, а не в ИБСО
ibsoService.sendCardToOeb(rp.getObjectId(), ...);

// ❌ ПРОБЛЕМА 3: status и client — текстовые названия, а не ref-id
statusField.setValue(status);        // "Зарегистрирован" — нельзя
statusField.setType("String");       // должен быть Object

clientField.setValue(clientId);      // если это ФИО — нельзя
clientField.setType("String");       // должен быть Object

// ❌ ПРОБЛЕМА 4: нет шага default
reqCallOper.setActionType("execute"); // сразу execute без default

// ❌ ПРОБЛЕМА 5: contextId
document.setContextId(rp.getContext().toString()); // нужен UUID, как в uploadDocumentToIbso
Исправленный вызов из SendOrderForOeb:
@Override
public OperationResponseV3 execute(RequestV3 rp) {
    CardOrder cardOrder = repo.findById(rp.getObjectIdList().get(0), CardOrder.class)
        .orElseThrow(() -> new RuntimeException("Заявка не найдена"));

    log.info("Отправка заявки {} в ОЭБ", cardOrder.getExtId());

    ibsoService.sendCardToOeb(
        cardOrder.getExtId(),   // ← ID из ИБСО!
        rp.getUser(),
        UUID.randomUUID().toString(),
        null,                   // status — пусть default подтянет
        null,                   // idComunda — пусть default подтянет
        null                    // client — пусть default подтянет
    );
    return null;
}
5. Полный метод sendCardToOeb (как должно быть)
public void sendCardToOeb(String ibsoOrderId, String username, String contextId,
                          String status, String idComunda, String clientId) {

    // === ШАГ 1: DEFAULT ===
    Document defaultDoc = createDocument("OperationInteraction");
    defaultDoc.setUser(username);
    defaultDoc.setContextId(contextId);

    ReqCallOper defaultReq = new ReqCallOper();
    defaultReq.setObjectId(ibsoOrderId);
    defaultReq.setOperationName("O_CARD_TO_OEB");
    defaultReq.setActionType("default");
    defaultDoc.setReqCallOper(defaultReq);

    logger.info("DEFAULT O_CARD_TO_OEB: {}", marshalDocument(defaultDoc));
    Document defaultResult = directABSService.request(defaultDoc);
    logger.info("Ответ DEFAULT: {}", marshalDocument(defaultResult));

    if (defaultResult.getFailure() != null) {
        throw new RuntimeException("Ошибка default: " + defaultResult.getFailure().getInfo());
    }

    // Берём поля из ответа default (если не передали явно)
  String actualStatus   = status   != null ? status   : extractField(defaultResult, "P_STATUS");
  String actualComunda  = idComunda != null ? idComunda : extractField(defaultResult, "P_ID_COMUNDA");
  String actualClient   = clientId != null ? clientId : extractField(defaultResult, "P_CLIENT");

    // === ШАГ 2: EXECUTE ===
    Document execDoc = createDocument("OperationInteraction");
    execDoc.setUser(username);
    execDoc.setContextId(contextId);

    ReqCallOper execReq = new ReqCallOper();
    execReq.setObjectId(ibsoOrderId);
    execReq.setOperationName("O_CARD_TO_OEB");
    execReq.setActionType("execute");
    execReq.setContainingView("VW_CRIT_PRI_ORDER_N_CARD");

    addField(execReq, "P_STATUS",     actualStatus,  "Object");
    addField(execReq, "P_ID_COMUNDA", actualComunda, "String");
    addField(execReq, "P_CLIENT",     actualClient,  "Object");
    addField(execReq, "P_REJECTION",  "",            "String");

    execDoc.setReqCallOper(execReq);

    logger.info("EXECUTE O_CARD_TO_OEB: {}", marshalDocument(execDoc));
    Document result = directABSService.request(execDoc);
    logger.info("Ответ EXECUTE: {}", marshalDocument(result));

    if (result.getFailure() != null) {
        throw new RuntimeException("Ошибка execute: " + result.getFailure().getInfo());
    }
}

private void addField(ReqCallOper req, String name, String value, String type) {
    ReqCallOper.Field field = new ReqCallOper.Field();
    field.setName(name);
    field.setValue(value);
    field.setType(type);
    req.getField().add(field);
}

private String extractField(Document doc, String fieldName) {
    if (doc.getAnsCallOper() == null) return null;
    return doc.getAnsCallOper().getField().stream()
        .filter(f -> fieldName.equals(f.getName()))
        .map(ReqCallOper.Field::getValue)
        .findFirst().orElse(null);
}
6. Что делает операция внутри ИБСО (чтобы понимать ошибки)
По твоему PL/SQL O_CARD_TO_OEB_EXECUTE:
1.
Проверяет упрощённую идентификацию клиента (SIMPLE_IDENTIFICATION)
2.
Меняет статус заявки
3.
Берёт URL Camunda из настройки DNM_LOCATION_MOVE_PROCESS
4.
Делает HTTP GET к /engine-rest/task?processInstanceId={P_ID_COMUNDA}
5.
Делает HTTP POST complete задачи с {"variables":{"finddossier":{"value":"IMNS_FORM"}}}
Типичные ошибки на этом этапе:
Ошибка
Причина
пакет не создан
Вызвана DNM_TO_OEB_GR вместо O_CARD_TO_OEB
OBJECT_NOT_FOUND
Неверный objectId (не extId из ИБСО)
Не определен инстанс бизнес-процесса
P_ID_COMUNDA пустой или процесс не существует
HTTP ошибка Camunda
Процесс завершён или Camunda недоступна
Идентификация fail
Клиент не прошёл SIMPLE_IDENTIFICATION
7. Про ID_COMUNDA — важный момент
В представлении VW_CRIT_PRI_ORDER_N_CARD нет колонки ID_COMUNDA.
В getCardOrders() ты её не получишь.
Варианты:
1.
Рекомендуемый: брать через default (ИБСО читает из Z#PRI_ORDER_N_CARD)
2.
Добавить колонку в представление ИБСО (если есть доступ к разработке ИБСО)
3.
Читать через ReqGetData по объекту PRI_ORDER_N_CARD
Поэтому не сохраняй idComunda при импорте — проще всегда брать из default.
8. Чеклист перед вызовом
Перед execute проверь:
•
[ ] objectId = ID из ИБСО (колонка ID в ответе GetViewData)
•
[ ] operationName = O_CARD_TO_OEB
•
[ ] Сначала вызван default, потом execute
•
[ ] Один и тот же contextId (UUID) на оба шага
•
[ ] P_STATUS и P_CLIENT — числовые ref, тип Object
•
[ ] P_REJECTION — пустая строка (если нет отказа)
•
[ ] У заявки заполнено LATIN_CL_NAME (batch-операция фильтрует по этому полю)
•
[ ] Заявка в нужном состоянии (по инструкции: «Зарегистрирован» + офис «Сектор курьерской службы»)
9. Как отладить пошагово
1.
Возьми одну заявку из лога getCardOrders — скопируй её ID
2.
Вызови только default с этим ID — посмотри XML-ответ в логе
3.
Убедись, что в ответе есть P_STATUS, P_CLIENT, P_ID_COMUNDA
4.
Вызови execute с этими значениями
5.
Проверь в ИБСО: состояние должно смениться на «Подготовлен»
Краткий итог
Было
Должно быть
DNM_TO_OEB_GR
O_CARD_TO_OEB
rp.getObjectId()
cardOrder.getExtId()
Сразу execute
default → execute
P_STATUS как String с текстом
P_STATUS как Object с числовым ref
P_CLIENT как String с ФИО
P_CLIENT как Object с числовым ref
contextId = rp.getContext().toString()
UUID.randomUUID().toString()
Если хочешь, в следующем сообщении можешь скинуть полный XML запроса и ответа на default для одной заявки — разберём построчно, что ИБСО вернул и что передать в execute.




	Ок, если с O_CARD_TO_OEB та же ошибка — значит дело не в имени операции. Падает ещё до выполнения логики: на этапе, когда DNM_CALL_OPER ищет PL/SQL-пакет в Oracle.
Что означает эта ошибка
<oper>[CIT_INTERFACE]::[DNM_CALL_OPER]</oper>
<info> пакет не создан</info>
Это не «операция отработала с ошибкой». Это: ИБСО не нашла скомпилированный Oracle-пакет для вызова.
Цепочка такая:
Твой XML (ReqCallOper)
    → DNM_CALL_OPER (диспетчер)
        → ищет пакет Z$... в Oracle
            → НЕ НАШЁЛ → "пакет не создан"
До O_CARD_TO_OEB_EXECUTE (Camunda, идентификация и т.д.) дело даже не доходит.
Почему CL_PRIV_DNM_NEW_DOC работает, а O_CARD_TO_OEB — нет
В проекте есть рабочий пример — загрузка документа:
Рабочая операция
Твоя операция
Имя в Java
CL_PRIV_DNM_NEW_DOC
O_CARD_TO_OEB
Oracle-пакет
Z$CL_PRIV_DNM_NEW_DOC
Z$PRI_ORDER_N_CARD_64983611976
Имя пакета
= имя операции
другое — с хэшем класса
Ты прислал PL/SQL:
package body Z$PRI_ORDER_N_CARD_64983611976 is
    procedure O_CARD_TO_OEB_VALIDATE(...)
    function  O_CARD_TO_OEB_EXECUTE(...) return VARCHAR2
То есть процедуры лежат внутри пакета класса PRI_ORDER_N_CARD, а не в отдельном Z$O_CARD_TO_OEB.
DNM_CALL_OPER при вызове O_CARD_TO_OEB без указания класса скорее всего ищет:
Z$O_CARD_TO_OEB   ← такого пакета нет
Отсюда и пакет не создан — и с DNM_TO_OEB_GR, и с O_CARD_TO_OEB.
Главное, что нужно попробовать: передать класс
В GetViewData класс всегда указывается:
<View name="VW_CRIT_PRI_ORDER_N_CARD" class="PRI_ORDER_N_CARD"/>
Для ReqCallOper класс, скорее всего, тоже обязателен, но в твоём коде его нет.
Вариант 1 — добавить class в ReqCallOper
ReqCallOper req = new ReqCallOper();
req.setObjectId(ibsoOrderId);              // ID заявки из ИБСО!
req.setOperationName("O_CARD_TO_OEB");
req.setActionType("default");
req.setClazz("PRI_ORDER_N_CARD");          // ← ЭТО КЛЮЧЕВОЕ
req.setContainingView("VW_CRIT_PRI_ORDER_N_CARD");
XML должен выглядеть так:
<Document product="OperationInteraction" user="твой_логин" contextId="uuid" ver_xml="2016-05-04" platform="NF">
  <ReqCallOper objectId="547774482740"
               operationName="O_CARD_TO_OEB"
               actionType="default"
               class="PRI_ORDER_N_CARD"
               containingView="VW_CRIT_PRI_ORDER_N_CARD"/>
</Document>
Если в твоей версии JAXB нет setClazz, попробуй setClass — посмотри автокомплит по ReqCallOper.
Вариант 2 — полное имя операции
req.setOperationName("PRI_ORDER_N_CARD_O_CARD_TO_OEB");
// или
req.setOperationName("[PRI_ORDER_N_CARD]::[O_CARD_TO_OEB]");
Вариант 3 — комбинация
req.setClazz("PRI_ORDER_N_CARD");
req.setOperationName("O_CARD_TO_OEB");
req.setContainingView("VW_CRIT_PRI_ORDER_N_CARD");
Диагностика: 3 теста по порядку
Тест 1 — работает ли DNM_CALL_OPER вообще
Вызови только default для рабочей операции CL_PRIV_DNM_NEW_DOC на любом клиенте:
Document doc = createDocument("OperationInteraction");
doc.setUser("твой_логин_ибсо");
doc.setContextId(UUID.randomUUID().toString());

ReqCallOper req = new ReqCallOper();
req.setObjectId("229540276331");  // любой реальный client ID
req.setOperationName("CL_PRIV_DNM_NEW_DOC");
req.setActionType("default");

doc.setReqCallOper(req);
Document result = directABSService.request(doc);
Результат
Вывод
CL_PRIV_DNM_NEW_DOC работает
XML-механизм ок, проблема именно в O_CARD_TO_OEB / классе
Тоже пакет не создан
Проблема в окружении / пользователе / подключении к другой БД
Тест 2 — проверка пакета в Oracle
Если есть доступ к БД ИБСО (absDs):
-- Есть ли пакет операции?
SELECT object_name, object_type, status
FROM all_objects
WHERE owner = 'IBS'
  AND object_name LIKE '%PRI_ORDER_N_CARD%'
ORDER BY object_name;

-- Есть ли отдельный пакет O_CARD_TO_OEB (скорее всего нет)?
SELECT object_name, status
FROM all_objects
WHERE owner = 'IBS'
  AND object_name LIKE '%O_CARD%';
Ожидаемый результат:
•
есть Z$PRI_ORDER_N_CARD_64983611976 (число может отличаться на твоём стенде)
•
нет Z$O_CARD_TO_OEB
Если пакета класса нет или status = INVALID — из Java это не починить, нужна перекомпиляция класса PRI_ORDER_N_CARD в Администраторе ИБСО.
Тест 3 — операция в UI ИБСО
В самом ИБСО:
1.
Открой представление VW_CRIT_PRI_ORDER_N_CARD
2.
Встань на заявку
3.
Вызови операцию «Передать в ОЭБ» вручную
Результат
Вывод
В UI работает
Пакет есть, не хватает параметров в XML (скорее всего class)
В UI тоже не работает
Пакет не скомпилирован на этом стенде
Минимальный рабочий запрос для проверки
Скопируй и попробуй именно этот набор (сначала только default):
String ibsoOrderId = "547774482740";  // реальный ID из getCardOrders
String username    = "твой_логин";      // не "IBS", а реальный пользователь ИБСО
String contextId   = UUID.randomUUID().toString();

Document doc = createDocument("OperationInteraction");
doc.setUser(username);
doc.setContextId(contextId);

ReqCallOper req = new ReqCallOper();
req.setObjectId(ibsoOrderId);
req.setOperationName("O_CARD_TO_OEB");
req.setActionType("default");
req.setClazz("PRI_ORDER_N_CARD");                    // попробуй
req.setContainingView("VW_CRIT_PRI_ORDER_N_CARD");   // попробуй

doc.setReqCallOper(req);

logger.info("REQUEST: {}", marshalDocument(doc));
Document result = directABSService.request(doc);
logger.info("RESPONSE: {}", marshalDocument(result));
Скинь оба XML (запрос и ответ) — по ним можно будет сказать точно, что не так.
Если class не помог — обходной путь через JDBC
В проекте уже есть прямой вызов Oracle, минуя DNM_CALL_OPER:
// как setBlobForId в IbsoServiceImpl:
"{? = call IBS.Z$DNM_MESS_BLOB_EXT_CALL.SETOBJ(?)}"
Теоретически можно вызвать напрямую:
// имя пакета узнать из SQL выше!
String sql = "{? = call IBS.Z$PRI_ORDER_N_CARD_64983611976.O_CARD_TO_OEB_EXECUTE(?, ?, ?, ?, ?, ?)}";

try (Connection conn = dataSource.getConnection();
     CallableStatement cs = conn.prepareCall(sql)) {

    cs.registerOutParameter(1, Types.VARCHAR);  // RESULT
    cs.setLong(2, Long.parseLong(ibsoOrderId)); // THIS
    cs.setString(3, "PRI_ORDER_N_CARD");         // PLP$CLASS
    cs.setLong(4, statusId);                     // P_STATUS (число!)
    cs.setString(5, idComunda);                  // P_ID_COMUNDA
    cs.setLong(6, clientId);                   // P_CLIENT (число!)
    cs.setString(7, null);                       // P_REJECTION

    cs.execute();
    String result = cs.getString(1);
}
Но:
•
хэш 64983611976 может отличаться на твоём стенде
•
это обходной путь, не основной
•
сначала всё равно нужен SQL из теста 2
Чеклист: что ещё проверить
Даже если добавишь class, убедись:
#
Проверка
1
objectId = ID из ИБСО (extId), не id из Финдоставки
2
user = реальный логин оператора в ИБСО (как при загрузке документов)
3
contextId = новый UUID, один и тот же на default + execute
4
Сначала default, потом execute
5
P_STATUS, P_CLIENT — числовые ref, тип Object, не текст
Но повторюсь: при пакет не создан это всё вторично. Сначала нужно, чтобы DNM_CALL_OPER нашёл пакет.
Итог
Скорее всего у тебя не проблема Java-кода как такового, а одно из двух:
1.
В ReqCallOper не передаётся class="PRI_ORDER_N_CARD" — и DNM_CALL_OPER ищет несуществующий Z$O_CARD_TO_OEB
2.
На стенде, куда ходит directABSService, пакет класса не скомпилирован — тогда нужна команда ИБСО
Сделай тест 1 (CL_PRIV_DNM_NEW_DOC) и тест 3 (операция в UI), и скинь XML запроса/ответа для O_CARD_TO_OEB с class. По этим трём вещам можно будет сказать на 100%, где затык.

@Component
@Slf4j
public class OrderCreateInCard extends OrderCreate {

    private static final Pattern CITY_PATTERN = Pattern.compile(
            "\\bг\\.?\\s+([А-Яа-яЁё\\-]+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS
    );

    @Autowired
    private CardOrderService cardOrderService;

    @Autowired
    IbsoService ibsoService;

    @Override
    public OperationResponseV3 defaultValidate(RequestV3 rp) {
        try {
            if (rp.getObjectIdList().size() > 1) {
                throw new RuntimeException("Выбрано более 1 заявки");
            }

            CardOrder cardOrder = cardOrderService.findById(rp.getObjectIdList().get(0));

            rp.getOrCreateField("createOrderDate")
                    .setSingleValue(LocalDateTime.now().toString());

            cardOrderService.getActiveProductCode()
                    .ifPresent(productCode -> {
                        rp.getOrCreateField("entity.productCode")
                                .setItems(List.of(new Field.Item(
                                        productCode.getId(),
                                        String.format("%s (%s)", productCode.getLabel(), productCode.getCode())
                                )));
                        rp.getOrCreateField("entity.productCode")
                                .setSingleValue(productCode.getId());
                    });

            rp.getOrCreateField("clients.fio").setSingleValue(cardOrder.getClient());
            rp.getOrCreateField("clients").setSingleValue(cardOrder.getClientId());

            String phone = ibsoService.getClientPhoneById(cardOrder.getClientId());
            rp.getGlobalContext().setValue("phone", phone);
            rp.getOrCreateField("entity.phone").setSingleValue(formatPhone(phone));
            rp.getOrCreateField("entity.phone").setReadOnly(false);

            rp.getOrCreateField("deliveryAddress").setItems(
                    StreamSupport.stream(repo.findAll(DeliveryPoint[].class).spliterator(), false)
                            .filter(DeliveryPoint::isActive)
                            .map(dp -> new Field.Item(dp.getId(), dp.getLabel()))
                            .collect(Collectors.toList())
            );
            rp.getOrCreateField("deliveryAddress").setReadOnly(false);

            autoSelectDeliveryPoint(rp, cardOrder);

        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        return super.defaultValidate(rp, Order.class);
    }

    @Override
    public OperationResponseV3 validate(RequestV3 rp) {
        return super.validate(rp);
    }

    @Override
    public OperationResponseV3 execute(RequestV3 rp) {
        return super.execute(rp);
    }

    private void autoSelectDeliveryPoint(RequestV3 rp, CardOrder cardOrder) {
        String clientAddress = cardOrder.getCity();
        if (!StringUtils.hasText(clientAddress)) {
            log.info("В заявке на карту не указан город получения, id={}", cardOrder.getId());
            return;
        }

        Optional<DeliveryPoint> matchedPoint = findDeliveryPoint(clientAddress);
        if (matchedPoint.isEmpty()) {
            log.info("Пункт доставки не найден для адреса '{}' (заявка id={})",
                    clientAddress, cardOrder.getId());
            return;
        }

        DeliveryPoint dp = matchedPoint.get();
        rp.getOrCreateField("deliveryAddress").setSingleValue(dp.getId());
        rp.getOrCreateField("deliveryAddress.name").setSingleValue(dp.getLabel());
        rp.getOrCreateField("deliveryAddress.name").setReadOnly(true);

        rp.setActivatedField("deliveryAddress");
        super.validate(rp);
    }

    private Optional<DeliveryPoint> findDeliveryPoint(String clientAddress) {
        String city = extractCity(clientAddress);
        if (!StringUtils.hasText(city)) {
            return Optional.empty();
        }

        return StreamSupport.stream(repo.findAll(DeliveryPoint[].class).spliterator(), false)
                .filter(DeliveryPoint::isActive)
                .filter(dp -> matchesCity(clientAddress, dp))
                .min(Comparator.comparingInt(dp -> matchPriority(city, clientAddress, dp)));
    }

    private int matchPriority(String city, String clientAddress, DeliveryPoint dp) {
        if (extractCity(dp.getLabel()).equalsIgnoreCase(city)) {
            return 0;
        }
        if (checkDelivery(clientAddress, dp.getLabel())) {
            return 1;
        }
        return 2;
    }

    private boolean matchesCity(String clientAddress, DeliveryPoint dp) {
        return checkDelivery(clientAddress, dp.getLabel())
                || checkDelivery(clientAddress, dp.getAddress());
    }

    private static String extractCity(String address) {
        if (!StringUtils.hasText(address)) {
            return "";
        }

        Matcher matcher = CITY_PATTERN.matcher(address);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        String firstPart = address.split(",")[0].trim();
        return firstPart.replaceAll("[^А-Яа-яЁё\\-]", "");
    }

    private static boolean checkDelivery(String userAddress, String deliveryEntity) {
        String city = extractCity(userAddress);
        if (!StringUtils.hasText(city) || deliveryEntity == null) {
            return false;
        }
        return deliveryEntity.toLowerCase().contains(city.toLowerCase());
    }

    private String formatPhone(String phone) {
        String digits = phone.replaceAll("\\D", "");

        if ((digits.startsWith("7") || digits.startsWith("8")) && digits.length() > 10) {
            digits = digits.substring(1);
        }

        if (digits.length() < 10) {
            return phone;
        }

        String code = digits.substring(0, 3);
        String one = digits.substring(3, 6);
        String two = digits.substring(6, 8);
        String three = digits.substring(8, 10);

        return String.format("+7 (%s) %s-%s-%s", code, one, two, three);
    }
}




package ru.dynamika.findelivery.ibso;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DownloadedIbsoFile {
    private final String fileName;
    private final String fileId;
}



package ru.dynamika.findelivery.ibso;

import ru.dynamika.app.module.api.file.exceptions.FileAvailabilityException;
import ru.dynamika.data.developer.entities.findelivery.Client;
import ru.dynamika.data.developer.entities.findelivery.Documents;
import ru.dynamika.unitsplayer.model.generated.ReqFindClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface IbsoService {

    Client findClientByInn(String inn);

    Client findClientByExtId(String extId);

    List<Map<String, String>> findFilteredClients(ReqFindClient reqFindClient);

    void uploadDocumentToIbso(Documents documents, Client client, String docTypeId, String username, String typeName) throws FileAvailabilityException, IOException;

    List<Map<String, String>> getFileTypes();

    List<Map<String, String>> findFilteredOrganizations(Map<String, String> filterParams);

    Map<String, String> findOrganizationById(String orgId);

    List<Map<String, String>> getCardOrders();

    List<Map<String, String>> getClientDetails(String clientExtId);

    String getClientPhoneById(String clientExtId);

    void sendCardToOeb(String objectId, String username, String contextId, String status, String idComunda, String clientId);

    List<DownloadedIbsoFile> downloadCardReestr(String objectId, String username, String contextId, String latinClName);
}



package ru.dynamika.findelivery.ibso;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;
import ru.dynamika.data.developer.entities.findelivery.Client;
import ru.dynamika.data.developer.entities.findelivery.Documents;
import ru.dynamika.unitsplayer.model.generated.ReqFindClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnMissingBean(name = {"absDs"})
public class IbsoServiceStub implements IbsoService {

    @Override
    public Client findClientByInn(String inn) {
        log.warn("IbsoServiceStub findClientByInn!");
        return null;
    }

    @Override
    public Client findClientByExtId(String extId) {
        log.warn("IbsoServiceStub findClientByExtId!");
        return null;
    }

    public Map<String, String> findOrganizationById(String orgId) {
        log.warn("IbsoServiceStub findOrganizationById!");
        return Collections.EMPTY_MAP;
    }

    @Override
    public List<Map<String, String>> getCardOrders() {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<Map<String, String>> getClientDetails(String clientExtId) {
        return List.of();
    }

    @Override
    public String getClientPhoneById(String clientExtId) {
        return "";
    }

    @Override
    public void sendCardToOeb(String objectId, String username, String contextId, String status, String idComunda, String clientId) {

    }

    @Override
    public List<DownloadedIbsoFile> downloadCardReestr(String objectId, String username, String contextId, String latinClName) {
        log.warn("IbsoServiceStub downloadCardReestr!");
        return Collections.emptyList();
    }

    public List<Map<String, String>> findFilteredOrganizations(Map<String, String> filterParams) {
        log.warn("IbsoServiceStub findFilteredOrganizations!");
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<Map<String, String>> findFilteredClients(ReqFindClient reqFindClient) {
        log.warn("IbsoServiceStub findFilteredClients!");
        return Collections.EMPTY_LIST;
    }

    @Override
    public void uploadDocumentToIbso(Documents documents, Client client, String docTypeId, String username, String typeName) {
        log.warn("IbsoServiceStub uploadDocumentToIbso!");
    }

    public List<Map<String, String>> getFileTypes() {
        log.warn("IbsoServiceStub getFileTypes!");
        return Collections.EMPTY_LIST;


    }
}




package ru.dynamika.findelivery.units.pages.operations;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.dynamika.app.module.api.operation.model.Attachment;
import ru.dynamika.app.module.api.operation.model.FileStructure;
import ru.dynamika.app.module.api.operation.model.OperationResponseV3;
import ru.dynamika.app.module.api.operation.model.RequestV3;
import ru.dynamika.app.module.api.operation.service.AutoInitOperation;
import ru.dynamika.data.developer.entities.findelivery.CardOrder;
import ru.dynamika.findelivery.ibso.DownloadedIbsoFile;
import ru.dynamika.findelivery.ibso.IbsoService;

import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class SendOrderForOeb extends AutoInitOperation<CardOrder> {

    @Autowired
    private IbsoService ibsoService;


    @Override
    public OperationResponseV3 defaultValidate(RequestV3 rp) {
        return null;
    }

    @Override
    public OperationResponseV3 validate(RequestV3 rp) {
        return null;
    }

    @Override
    public OperationResponseV3 execute(RequestV3 rp) {
        CardOrder cardOrder = repo.findById(rp.getObjectIdList().get(0), CardOrder.class)
                .orElseThrow(() -> new RuntimeException("Заявка не найдена"));

        log.info("Отправка заявки {} в ОЭБ", cardOrder.getExtId());

  //      ibsoService.sendCardToOeb(
  //              cardOrder.getExtId(),
 //               rp.getUser(),
 //               UUID.randomUUID().toString(),
 //               null,
 //               null,
//                null
//        );

        List<DownloadedIbsoFile> reestrFiles = ibsoService.downloadCardReestr(
                cardOrder.getExtId(),
                rp.getUser(),
                UUID.randomUUID().toString(),
                cardOrder.getLatinClName()
        );

        if (reestrFiles.isEmpty()) {
            throw new RuntimeException("ИБСО не вернул файлы реестра");
        }

        if (reestrFiles.size() > 1) {
            log.info("ИБСО вернул {} файлов реестра, оператору будет показан первый", reestrFiles.size());
        }

        DownloadedIbsoFile reestrFile = reestrFiles.get(0);
        return OperationResponseV3.builder()
                .attachment(Attachment.builder()
                        .file(FileStructure.builder()
                                .id(reestrFile.getFileId())
                                .name(reestrFile.getFileName())
                                .ext("xlsx")
                                .build())
                        .build())
                .build();
    }
}



package ru.dynamika.findelivery.ibso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import ru.dynamika.app.module.api.common.service.ContextService;
import ru.dynamika.app.module.api.file.exceptions.FileAvailabilityException;
import ru.dynamika.app.module.api.file.service.MinioFileService;
import ru.dynamika.core.service.impl.DataSourceConfig;
import ru.dynamika.core.service.impl.DirectABSService;
import ru.dynamika.core.service.impl.DocumentMarshaller;
import ru.dynamika.data.developer.DataControllerEntityRepository;
import ru.dynamika.data.developer.QueryApiTemplate;
import ru.dynamika.data.developer.entities.findelivery.Client;
import ru.dynamika.data.developer.entities.findelivery.Documents;
import ru.dynamika.unitsplayer.model.generated.AnsGetData;
import ru.dynamika.unitsplayer.model.generated.Data;
import ru.dynamika.unitsplayer.model.generated.Document;
import ru.dynamika.unitsplayer.model.generated.FieldData;
import ru.dynamika.unitsplayer.model.generated.Call;
import ru.dynamika.unitsplayer.model.generated.ReqCallOper;
import ru.dynamika.unitsplayer.model.generated.ReqFindClient;
import ru.dynamika.unitsplayer.model.generated.ReqGetData;
import ru.dynamika.unitsplayer.model.generated.ReqGetViewData;
import ru.dynamika.unitsplayer.model.generated.ReqUniversal;

import jakarta.annotation.Nonnull;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@ConditionalOnBean(name = {"absDs"})
public class IbsoServiceImpl implements IbsoService {

    public static final String FILE_FUNCTION_SIGNATURE_SET = "{? = call IBS.Z$DNM_MESS_BLOB_EXT_CALL.SETOBJ(?)}";
    private static final String FILE_FUNCTION_SIGNATURE_GET = "{? = call IBS.Z$DNM_MESS_BLOB_EXT_CALL.GETOBJ(?)}";
    private static final String CARD_REESTR_OPERATION = "PRI_ORDER_N_CARD_DNM_CARD_REESTR";
    private static final String CARD_REESTR_VIEW = "VW_CRIT_PRI_ORDER_N_CARD";
    private static final String PROTOCOL_VERSION = "2016-05-04";
    private static final String PLATFORM = "NF";
    private static final String USER = "IBS";
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    @Value("${findostavka.document.type}")
    private String docType;

    @Autowired
    private DataControllerEntityRepository repo;
    @Autowired
    private MinioFileService minioFileService;
    @Autowired
    private QueryApiTemplate query;
    @Lazy
    @Autowired
    private DirectABSService directABSService;
    @Autowired
    private DocumentMarshaller marshaller;
    @Autowired
    @Qualifier("absDs")
    private DataSource dataSource;

    protected Document createDocument(@Nonnull String product) {
        Document result = new Document();
        result.setProduct(product);
        result.setVerXml(PROTOCOL_VERSION);
        result.setPlatform(PLATFORM);
        result.setUser(USER);
        return result;
    }

    @Override
    public Client findClientByInn(String inn) {
        ReqFindClient reqFindClient = new ReqFindClient();
        reqFindClient.setInn(inn);

        try {
            List<Map<String, String>> clients = findFilteredClients(reqFindClient);
            if (clients.isEmpty()) {
                throw new RuntimeException("Клиент не найден в ИБСО, инн: " + inn);
            }

            Client client = query.queryForObject("select * from fdelivery.client where inn = :inn", Client.class, "inn", inn)
                    .orElse(new Client());

            mapToClient(clients.get(0), client);

            return repo.save(client);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Client findClientByExtId(String extId) {
        if (!StringUtils.hasText(extId)) {
            throw new RuntimeException("Не передано id клиента");
        }

        ReqFindClient reqFindClient = new ReqFindClient();
        reqFindClient.setClientId(extId);

        try {
            List<Map<String, String>> clients = findFilteredClients(reqFindClient);
            if (clients.isEmpty()) {
                throw new RuntimeException("Клиент не найден в ИБСО");
            }

            Client client = query.queryForObject("select * from fdelivery.client where extid = :extId", Client.class, "extId", extId)
                    .orElse(new Client());

            mapToClient(clients.get(0), client);

            return repo.save(client);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ReqUniversal fillUniversalReq(Map<String, String> params) {
        ReqUniversal reqUniversal = new ReqUniversal();
        reqUniversal.setRequestName("FindOrg");

        Data data = new Data();
        Data.Fields fields = new Data.Fields();

        for (Map.Entry<String, String> stringStringEntry : params.entrySet()) {
            FieldData fieldData = new FieldData();
            fieldData.setName(stringStringEntry.getKey());
            fieldData.setSimpleValue(stringStringEntry.getValue());
            fields.getField().add(fieldData);
        }

        data.setFields(fields);
        reqUniversal.setData(data);

        return reqUniversal;
    }

    public Map<String, String> findOrganizationById(String orgId) {
        Map<String, String> params = new HashMap<>();
        params.put("id", orgId);
        List<Map<String, String>> result = findFilteredOrganizations(params);
        if (result == null || result.isEmpty()) {
            logger.error("Не найдена организация в ИБСО c id {}", orgId);
            throw new RuntimeException("Не найдена организация в ИБСО с id " + orgId);
        }
        return result.get(0);
    }

    public List<Map<String, String>> findFilteredOrganizations(Map<String, String> filterParams) {

        ReqUniversal reqUniversal = fillUniversalReq(filterParams);

        Document document = createDocument("DNM_ORG");
        document.setReqUniversal(reqUniversal);
        Document answerDocument = null;

        try {
            logger.info("Отправка запроса на получение организаций из ИБСО: {}", marshalDocument(document));
            answerDocument = directABSService.request(document);
        } catch (Exception e) {
            logger.error("Ошибка при получении организаций из ИБСО: {}", marshalDocument(answerDocument), e);
            throw new RuntimeException("Ошибка при получении клиентов из ИБСО", e);
        }

        if (answerDocument.getFailure() != null) {
            throw new RuntimeException(answerDocument.getFailure().getInfo());
        }

        List<Map<String, String>> result = new ArrayList<>();
        for (FieldData fieldData : answerDocument.getAnsUniversal().getData().getFields().getField()) {
            for (FieldData item : fieldData.getArray().getItem()) {
                Map<String, String> orgMap = new HashMap<>();
                for (FieldData field : item.getObjectFields().getField()) {
                    String name = field.getName();
                    name = name.equals("id") ? name.toUpperCase() : name;
                    orgMap.put(name, field.getSimpleValue());
                }
                result.add(orgMap);
            }
        }

        return result;
    }

    private void mapToClient(Map<String, String> map, Client client) {
        client.setInn(map.getOrDefault("inn", client.getInn()));
        client.setFio(map.getOrDefault("fio", client.getFio()));
        client.setExtId(map.getOrDefault("ID", client.getExtId()));
    }

    private String marshalDocument(Document document) {
        if (document == null) {
            return "null";
        }

        try {
            return marshaller.marshal(document);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при переводе документа в строку ", e);
        }
    }

    @Override
    public List<Map<String, String>> findFilteredClients(ReqFindClient reqFindClient) {
        Document document = createDocument("CLIENT_NEW");
        document.setReqFindClient(reqFindClient);
        Document answerDocument = null;

        try {
            logger.info("Отправка запроса на получение клиентов из ИБСО: {}", marshalDocument(document));
            answerDocument = directABSService.request(document);
        } catch (Exception e) {
            logger.error("Ошибка при получении клиентов из ИБСО: {}", marshalDocument(answerDocument));
            throw new RuntimeException("Ошибка при получении клиентов из ИБСО", e);
        }

        if (answerDocument.getAnsFindClient().getFailure() != null) {
            throw new RuntimeException(answerDocument.getAnsFindClient().getFailure().getInfo());
        }

        if (answerDocument.getAnsFindClient().getExactClients() == null) {
            return new ArrayList<>();
        }

        List<Map<String, String>> result = new ArrayList<>();
        for (ru.dynamika.unitsplayer.model.generated.Client client : answerDocument.getAnsFindClient().getExactClients().getClient()) {
            Map<String, String> clientMap = new HashMap<>();
            clientMap.put("ID", client.getId());
            clientMap.put("inn", client.getInn());
            clientMap.put("fio", String.join(" ", client.getSurname(), client.getFirstname(), client.getMiddlename()));
            clientMap.put("birthdate", client.getBirthdate()); //форматы: дд-мм-гггг или дд.мм.гггг
            clientMap.put("passport", client.getMainDoc()); // Форматы: "1111 222222" или "1111222222"
            clientMap.put("phone", ""); //TODO: телефон возможно есть в contacts, но нет клиентов в ibso с заполненным contacts
            result.add(clientMap);
        }
        return result;
    }

    @Override
    public void uploadDocumentToIbso(Documents documents, Client client, String docTypeId, String username, String typeName) throws FileAvailabilityException, IOException {
        String[] fileParts = documents.getFileId().split("/");
        byte[] fileContent = minioFileService.getFile(fileParts[1]).getStream().readAllBytes();
        String objectId = setBlobForId(fileContent);
        String fileName = documents.getFileName();
        if (!fileName.endsWith(documents.getFileExt())) {
            fileName = String.join(".", fileName, documents.getFileExt());
        }
        String contextId = UUID.randomUUID().toString();

        sendDefaultValidate(client.getExtId(), username, contextId);

        sendFileTypeToIbso(client.getExtId(), docTypeId, typeName, username, contextId);

        sendDocumentToIbso(objectId, client.getExtId(), fileName, username, contextId);
    }

    private String setBlobForId(byte[] content) {
        try (Connection connection = dataSource.getConnection()) {
            CallableStatement callableStatement = connection.prepareCall(FILE_FUNCTION_SIGNATURE_SET);
            callableStatement.registerOutParameter(1, Types.VARCHAR);
            callableStatement.setBlob(2, new ByteArrayInputStream(content));
            callableStatement.execute();
            String fileId = callableStatement.getString(1);
            return fileId;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] getBlobById(String blobId) {
        try (Connection connection = dataSource.getConnection()) {
            CallableStatement callableStatement = connection.prepareCall(FILE_FUNCTION_SIGNATURE_GET);
            callableStatement.registerOutParameter(1, Types.BLOB);
            callableStatement.setString(2, blobId);
            callableStatement.execute();
            Blob blob = callableStatement.getBlob(1);
            if (blob == null) {
                throw new RuntimeException("ИБСО не вернул содержимое файла для id " + blobId);
            }
            return blob.getBytes(1, (int) blob.length());
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при получении файла из ИБСО", e);
        }
    }

    private void addOperationField(ReqCallOper req, String name, String value, String type) {
        ReqCallOper.Field field = new ReqCallOper.Field();
        field.setName(name);
        field.setValue(value);
        field.setType(type);
        req.getField().add(field);
    }

    private void checkOperationFailure(Document document) {
        if (document.getFailure() != null && document.getFailure().getInfo() != null) {
            throw new RuntimeException("Ошибка ИБСО: " + document.getFailure().getInfo());
        }
        if (document.getAnsCallOper() == null) {
            return;
        }
        if (document.getAnsCallOper().getFailure() != null
                && document.getAnsCallOper().getFailure().getInfo() != null) {
            throw new RuntimeException("Ошибка операции: " + document.getAnsCallOper().getFailure().getInfo());
        }
    }

    private Document sendCardReestrStep(String actionType, String objectId, String username, String contextId,
                                        String latinClName, String fieldName) {
        Document document = createDocument("OperationInteraction");
        document.setUser(username);
        document.setContextId(contextId);

        ReqCallOper req = new ReqCallOper();
        req.setObjectId(objectId);
        req.setOperationName(CARD_REESTR_OPERATION);
        req.setActionType(actionType);
        req.setContainingView(CARD_REESTR_VIEW);
        if (StringUtils.hasText(fieldName)) {
            req.setFieldName(fieldName);
        }

        if ("validate".equals(actionType) || "execute".equals(actionType)) {
            addOperationField(req, "V_VALID", "", "String");
            addOperationField(req, "LATIN_CL_NAME", latinClName != null ? latinClName : "", "String");
        }

        document.setReqCallOper(req);
        logger.info("Отправка {} формирования реестра в ИБСО: {}", actionType, marshalDocument(document));
        Document result = directABSService.request(document);
        logger.info("Ответ ИБСО {} формирования реестра: {}", actionType, marshalDocument(result));
        checkOperationFailure(result);
        return result;
    }

    private Document sendPlpCall(String objectId, String username, String contextId, Call call) {
        Document document = createDocument("OperationInteraction");
        document.setUser(username);
        document.setContextId(contextId);

        ReqCallOper req = new ReqCallOper();
        req.setObjectId(objectId);
        req.setOperationName(CARD_REESTR_OPERATION);
        req.setContainingView(CARD_REESTR_VIEW);
        req.setActionType("call");
        req.getCall().add(call);

        document.setReqCallOper(req);
        logger.info("Отправка PLPCALL {} в ИБСО: {}", call.getOperID(), marshalDocument(document));
        Document result = directABSService.request(document);
        logger.info("Ответ PLPCALL {} из ИБСО: {}", call.getOperID(), marshalDocument(result));
        checkOperationFailure(result);
        return result;
    }

    private List<Call> getPendingCalls(Document document) {
        if (document.getAnsCallOper() == null || document.getAnsCallOper().getCalls() == null) {
            return List.of();
        }
        return document.getAnsCallOper().getCalls().getCall();
    }

    private DownloadedIbsoFile saveReportFromResponse(Document document, int index) {
        String blobId = extractBlobId(document);
        String fileName = extractFileName(document, index);
        byte[] content = getBlobById(blobId);
        String fileId = UUID.randomUUID().toString();
        minioFileService.putFileWithId(fileId, new ByteArrayInputStream(content));
        return new DownloadedIbsoFile(fileName, fileId);
    }

    private String extractBlobId(Document document) {
        if (document.getAnsCallOper() != null
                && document.getAnsCallOper().getAnsReport() != null
                && StringUtils.hasText(document.getAnsCallOper().getAnsReport().getObjectId())) {
            return document.getAnsCallOper().getAnsReport().getObjectId();
        }
        if (document.getAnsCallOper() != null
                && document.getAnsCallOper().getAnsReport() != null
                && StringUtils.hasText(document.getAnsCallOper().getAnsReport().getId())) {
            return document.getAnsCallOper().getAnsReport().getId();
        }
        throw new RuntimeException("ИБСО не вернул идентификатор файла реестра");
    }

    private String extractFileName(Document document, int index) {
        if (document.getAnsCallOper() != null
                && document.getAnsCallOper().getAnsReport() != null
                && StringUtils.hasText(document.getAnsCallOper().getAnsReport().getName())) {
            return document.getAnsCallOper().getAnsReport().getName();
        }
        return "reestr_" + index + ".xlsx";
    }

    private Document processPendingCalls(Document document, String objectId, String username, String contextId,
                                         List<DownloadedIbsoFile> downloadedFiles) {
        List<Call> pendingCalls = getPendingCalls(document);
        int callIndex = downloadedFiles.size();
        while (!pendingCalls.isEmpty()) {
            Call call = pendingCalls.get(0);
            document = sendPlpCall(objectId, username, contextId, call);
            downloadedFiles.add(saveReportFromResponse(document, callIndex++));
            pendingCalls = getPendingCalls(document);
        }
        return document;
    }

    public List<Map<String, String>> getFileTypes() {
        Document document = createDocument("GET_VIEW_DATA");
        ReqGetData reqGetData = new ReqGetData();
        reqGetData.setOperation("DNM_NEW_DOC");
        reqGetData.setClazz("CL_PRIV");
        reqGetData.setParam("P_TYPE_REF");
        document.setReqGetData(reqGetData);
        Document answerDocument = directABSService.request(document);

        if (answerDocument.getAnsGetData() == null) {
            logger.error("Ошибка при получении типов документов из ибсо: " + marshalDocument(answerDocument));
            throw new RuntimeException("Ошибка при получении типов документов из ИБСО");
        }

        List<Map<String, String>> types = answerDocument.getAnsGetData().getRecords().getRecord().stream()
                .map(record -> {
                    Map<String, String> result = new HashMap<>();
                    for (AnsGetData.Records.Record.Column column : record.getColumn()) {
                        // С_ переменные используются для отображения на нашем стенде
                        if ("C_CODE".equals(column.getName())) {
                            result.put("code", column.getValue());
                        }
                        if ("CODE".equals(column.getName())) {
                            result.put("code", column.getValue());
                        }
                        if ("C_NAME".equals(column.getName())) {
                            result.put("name", column.getValue());
                        }
                        if ("NAME".equals(column.getName())) {
                            result.put("name", column.getValue());
                        }
                        if ("ID".equals(column.getName())) {
                            result.put("ID", column.getValue());
                        }
                    }
                    return result;
                })
                .filter(map -> map.containsKey("ID"))
                .collect(Collectors.toList());

        return types;
    }

    private void sendDefaultValidate(String clientId, String username, String contextId) {
        Document document = createDocument("OperationInteraction");
        document.setUser(username);
        document.setContextId(contextId);
        ReqCallOper reqCallOper = new ReqCallOper();
        reqCallOper.setObjectId(clientId);
        reqCallOper.setActionType("default");
        reqCallOper.setOperationName("CL_PRIV_DNM_NEW_DOC");

        ReqCallOper.Field clientField = new ReqCallOper.Field();
        clientField.setName("P_CLIENT");
        clientField.setValue(clientId);
        clientField.setType("Object");
        reqCallOper.getField().add(clientField);

        document.setReqCallOper(reqCallOper);
        logger.info("Отправка default в ИБСО: {}", marshalDocument(document));
        Document resultDoc = directABSService.request(document);
        logger.info("Ответ default из ИБСО: {}", marshalDocument(resultDoc));
    }

    private void sendFileTypeToIbso(String clientId, String typeId, String typeName, String username, String contextId) {
        Document document = createDocument("OperationInteraction");
        document.setUser(username);
        document.setContextId(contextId);
        ReqCallOper reqCallOper = new ReqCallOper();
        reqCallOper.setObjectId(clientId);
        reqCallOper.setFieldName("P_TYPE_REF");
        reqCallOper.setActionType("validate");
        reqCallOper.setOperationName("CL_PRIV_DNM_NEW_DOC");
        ReqCallOper.Field typeField = new ReqCallOper.Field();
        typeField.setName("P_TYPE_REF");
        typeField.setValue(typeId);
        typeField.setType("Object");
        ReqCallOper.Field typeFieldName = new ReqCallOper.Field();
        typeFieldName.setName("P_TYPE_REF.NAME");
        typeFieldName.setValue(typeName);
        typeFieldName.setType("String");
        ReqCallOper.Field valid = new ReqCallOper.Field();
        valid.setName("V_VALID");
        valid.setType("String");
        ReqCallOper.Field documentTypeField = new ReqCallOper.Field();
        documentTypeField.setName("DNM_ATTACH");
        documentTypeField.setType("DNM_ATTACH_FILES");
        reqCallOper.getField().add(typeField);
        reqCallOper.getField().add(typeFieldName);
        reqCallOper.getField().add(valid);
        reqCallOper.getField().add(documentTypeField);

        document.setReqCallOper(reqCallOper);

        logger.info("Отправка типа документа в ИБСО: {}", marshalDocument(document));
        Document resultDoc = directABSService.request(document);
        logger.info("Ответ на отправку типа документа в ИБСО: {}", resultDoc);
    }

    private void sendDocumentToIbso(String objectId, String clientId, String fileName, String username, String contextId) {
        Document document = createDocument("OperationInteraction");
        document.setUser(username);
        document.setContextId(contextId);
        ReqCallOper reqCallOper = new ReqCallOper();
        reqCallOper.setObjectId(clientId);
        reqCallOper.setOperationName("CL_PRIV_DNM_NEW_DOC");
        reqCallOper.setActionType("execute");
        reqCallOper.setContainingView("VW_CRIT_DNM_CL_PRIV_V");
        ReqCallOper.Field valid = new ReqCallOper.Field();
        valid.setName("V_VALID");
        valid.setType("String");
        ReqCallOper.Field documentTypeField = new ReqCallOper.Field();
        documentTypeField.setName("DNM_ATTACH");
        documentTypeField.setType("DNM_ATTACH_FILES");
        ReqCallOper.Field clientField = new ReqCallOper.Field();
        clientField.setName("P_CLIENT");
        clientField.setValue(clientId);
        clientField.setType("Object");
        ReqCallOper.Field documentField = new ReqCallOper.Field();
        documentField.setName("DNM_ATTACH");
        documentField.setValue(String.join("///", objectId, fileName, ""));
        documentField.setType("Memo");

        reqCallOper.getField().add(documentTypeField);
        reqCallOper.getField().add(documentField);
        reqCallOper.getField().add(clientField);
        reqCallOper.getField().add(valid);

        document.setReqCallOper(reqCallOper);
        logger.info("Отправка документа в ИБСО: {}", marshalDocument(document));
        Document resultDoc = directABSService.request(document);

        if (resultDoc.getFailure() != null && resultDoc.getFailure().getInfo() != null
                && resultDoc.getFailure().getInfo().contains("В АБС не заведен пользователь")) {
            document.setUser(USER);
            resultDoc = directABSService.request(document);
        }

        if (resultDoc.getFailure() != null) {
            throw new RuntimeException("Ошибка при загрузке документа в АБС: " + resultDoc.getFailure().getInfo());
        }

//        if (resultDoc.getAnsCallOper() != null && resultDoc.getAnsCallOper().getFailure() != null) {
//            throw new RuntimeException("Ошибка при загрузке документа в АБС: " + resultDoc.getAnsCallOper().getFailure().getInfo());
//        }
    }

    @Override
    public List<Map<String, String>> getCardOrders() {
        Document document = createDocument("GetViewData");

        ReqGetViewData reqGetViewData = new ReqGetViewData();

        ReqGetViewData.Context context = new ReqGetViewData.Context();
        ReqGetViewData.Context.Param contextParam = new ReqGetViewData.Context.Param();

        contextParam.setName("SHOW_ALL_RECORDS");
        contextParam.setValue("VW_CRIT_PRI_ORDER_N_CARD");

        context.getParam().add(contextParam);
        reqGetViewData.setContext(context);

        ReqGetViewData.View view = new ReqGetViewData.View();
        view.setName("VW_CRIT_PRI_ORDER_N_CARD");
        view.setClazz("PRI_ORDER_N_CARD");
        reqGetViewData.setView(view);

        document.setReqGetViewData(reqGetViewData);

        Document answerDocument = directABSService.request(document);


        return answerDocument.getAnsGetViewData().getRecords().getRecord().stream()
                .map(record -> {
                    Map<String, String> result = new HashMap<>();
                    for (var column : record.getColumn()) {
                        String name = column.getName();
                        String value = column.getValue();

                        switch (name) {
                            case "ID" -> result.put("extId", value);
                            case "C_NUM" -> result.put("num", value);
                            case "C_DATE_TIME" -> result.put("localDate", value);
                            case "C_NAME" -> result.put("status", value);
                            case "C_NAME_1" -> result.put("client", value);
                            case "C_CLIENT" -> result.put("clientId", value);
                            case "C_PAYSYSTEM" -> result.put("paySystem", value);
                            case "C_TARIF" -> result.put("tarif", value);
                            case "C_CITY" -> result.put("city", value);
                            case "C_OFFICE" -> result.put("office", value);
                            case "C_LATIN_CL_NAME" -> result.put("latinClName", value);
                            case "C_FILENAME" -> result.put("reestr", value);
                            case "C_COMMENT" -> result.put("commentDetail", value);
                            case "C_ISGENERATE" -> result.put("generateFlag", value);
                            case "C_DOCS" -> result.put("value", value);
                        }
                    }
                    return result;
                })
                .filter(map -> map.containsKey("extId"))
                .filter(map -> "Сектор курьерской службы".equals(map.get("office")))
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, String>> getClientDetails(String clientExtId) {
        Document document = createDocument("CLIENT_NEW");
        ReqFindClient reqFindClient = new ReqFindClient();
        reqFindClient.setClientId(clientExtId);
        document.setReqFindClient(reqFindClient);
        Document answerDocument = null;

        try {
            logger.info("Отправка запроса на получение клиентов из ИБСО: {}", marshalDocument(document));
            answerDocument = directABSService.request(document);
        } catch (Exception e) {
            logger.error("Ошибка при получении клиентов из ИБСО: {}", marshalDocument(answerDocument));
            throw new RuntimeException("Ошибка при получении клиентов из ИБСО", e);
        }

        List<Map<String, String>> result = new ArrayList<>();
        for (ru.dynamika.unitsplayer.model.generated.Client client : answerDocument.getAnsFindClient().getExactClients().getClient()) {
            Map<String, String> clientMap = new HashMap<>();
            clientMap.put("ID", client.getId());
            clientMap.put("inn", client.getInn());
            clientMap.put("fio", String.join(" ", client.getSurname(), client.getFirstname(), client.getMiddlename()));
            clientMap.put("birthdate", client.getBirthdate()); //форматы: дд-мм-гггг или дд.мм.гггг
            clientMap.put("passport", client.getMainDoc()); // Форматы: "1111 222222" или "1111222222"
            clientMap.put("mainContact", client.getMainContact());
            result.add(clientMap);
        }
        return result;
    }

    public String getClientPhoneById(String clientExtId) {
        List<Map<String, String>> clientDetailsList = getClientDetails(clientExtId);

        if (clientDetailsList != null && !clientDetailsList.isEmpty()) {
            Map<String, String> clientMap = clientDetailsList.get(0);

            String phone = clientMap.get("mainContact");

            if (StringUtils.hasText(phone)) {
                return phone;
            }
        }
        return null;
    }

    @Override
    public void sendCardToOeb(String objectId, String username, String contextId, String status, String idComunda, String clientId) {

        Document document = createDocument("OperationInteraction");
        document.setUser(username);
        document.setContextId(contextId);

        ReqCallOper req = new ReqCallOper();
        req.setObjectId(objectId);
        req.setOperationName("PRI_ORDER_N_CARD_O_CARD_TO_OEB");
        req.setActionType("execute");
        req.setContainingView("VW_CRIT_PRI_ORDER_N_CARD");

        ReqCallOper.Field statusField = new ReqCallOper.Field();
        statusField.setName("P_STATUS");
        statusField.setValue(status);
        statusField.setType("Object");

        ReqCallOper.Field comundaField = new ReqCallOper.Field();
        comundaField.setName("P_ID_COMUNDA");
        comundaField.setValue(idComunda);
        comundaField.setType("String");

        ReqCallOper.Field clientField = new ReqCallOper.Field();
        clientField.setName("P_CLIENT");
        clientField.setValue(clientId);
        clientField.setType("Object");

        ReqCallOper.Field rejectionField = new ReqCallOper.Field();
        rejectionField.setName("P_REJECTION");
        rejectionField.setValue("");
        rejectionField.setType("String");

        req.getField().add(statusField);
        req.getField().add(comundaField);
        req.getField().add(clientField);
        req.getField().add(rejectionField);

        document.setReqCallOper(req);
        Document result = directABSService.request(document);
        if (result.getFailure() != null && result.getFailure().getInfo() != null) {
            throw new RuntimeException("Ошибка: " + result.getFailure().getInfo());
        }

        if (result.getAnsCallOper() != null && result.getAnsCallOper().getFailure() != null) {
            if (result.getAnsCallOper().getFailure().getInfo() != null) {
                throw new RuntimeException("Ошибка операции: " + result.getAnsCallOper().getFailure().getInfo());
            }
        }
    }

    @Override
    public List<DownloadedIbsoFile> downloadCardReestr(String objectId, String username, String contextId,
                                                       String latinClName) {
        List<DownloadedIbsoFile> downloadedFiles = new ArrayList<>();

        Document document = sendCardReestrStep("execute", objectId, username, contextId, latinClName, null);
        processPendingCalls(document, objectId, username, contextId, downloadedFiles);

        if (downloadedFiles.isEmpty()) {
            throw new RuntimeException("ИБСО не вернул файлы реестра");
        }
        return downloadedFiles;
    }
}

