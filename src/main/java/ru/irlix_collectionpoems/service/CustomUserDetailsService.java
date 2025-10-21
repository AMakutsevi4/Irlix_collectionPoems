package ru.irlix_collectionpoems.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import ru.irlix_collectionpoems.repository.UserRepository;

import java.util.Set;
import java.util.stream.Collectors;

/*
* Логика для загрузки сведений о пользователе по имени или электронной почте из базы данных.
*
Создаём CustomUserDetailsService, который реализует интерфейс UserDetailsService
(встроенный интерфейс Spring security) и предоставляет реализацию для метода loadUserByUername():
*
* Spring Security использует интерфейс UserDetailsService, который содержит метод loadUserByUsername(строковое имя пользователя)
 для поиска пользовательских данных для данного имени пользователя.
*
* Интерфейс UserDetails представляет собой объект аутентифицированного пользователя,
а Spring Security предоставляет готовую реализацию org.springframework.security.core.userdetails.User.*/
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        /* Поиск пользователя по имени или email */
        var user = userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found with username or email: " + usernameOrEmail));

        /* Получение ролей пользователя и преобразование их в SimpleGrantedAuthority */
        Set<GrantedAuthority> authorities = user
                .getRoles()
                .stream()
                .map((role) -> new SimpleGrantedAuthority(role.getName())).collect(Collectors.toSet());

        /* Возвращение объекта UserDetails, который будет использоваться для аутентификации */
        return new org.springframework.security.core.userdetails.User(user.getEmail(),
                user.getPassword(),
                authorities);
    }
}






Хорошо — раз `client.getContactList()` всегда `null`, разберёмся последовательно: где обычно корни проблемы и какие практичные шаги/патчи можно применить **сейчас** (код + отладка), чтобы гарантированно получить телефоны/почту даже если JAXB-маппинг «не дотягивает».

---

# 1) Быстрая диагностическая проверка (сделай прямо сейчас)

Добавь логирование сырого XML-ответа и поиск по нему на предмет тегов контактов — это одно из самых быстрых и информативных действий.

```java
String respXml = marshalDocument(answerDocument); // у тебя уже есть marshalDocument
logger.info("IBSO raw response:\n{}", respXml);

// простая проверка на вхождения
if (!respXml.contains("Contact") && !respXml.toLowerCase().contains("contact")) {
    logger.warn("В ответе ИБСО не найдено тегов contact/Contact/ContactList");
} else {
    logger.info("В ответе есть тег(и) contact — значит данные приходят, но JAXB их не распарсил");
}
```

Если в ответе нет ни одного `Contact` / `ContactList` — значит сервер их просто не отдаёт в этом запросе (см. п.3). Если есть — продолжаем.

---

# 2) Возможные причины, почему `client.getContactList()` == null и как их фиксить

**А. Ответ от ИБСО не содержит контактов (чёрный ящик).**
Причина: операция `CLIENT_NEW` / `findFilteredClients` может возвращать «облегчённые» данные (без контактных данных).
Что делать: выполнить запрос, который возвращает полный профиль клиента (обычно отдельный метод/тип запроса — «get client by id / full client»). См. п.4 про вариант запроса.

**Б. Несовпадение XML-тегов / имён в JAXB-классах (регистры, опечатки, namespace).**
Ваша генерация классов содержит много опечаток в примере (`Contactlist` vs `ContactList`, `Contactlist contactlist;` и т.д.). JAXB чувствителен к имени тега и пространствам имён. Если XML содержит `<ContactList>` а поле аннотировано `@XmlElement(name="Contactlist")` — маппинга не будет → поле null.

Что делать:

* Посмотри сырой XML (п.1), найди точное имя тега и namespace.
* Исправь аннотации в сгенерированном классе `Client` на точное имя тега, например:

```java
@XmlElement(name = "ContactList") // <-- имя взять из реального XML
protected ContactList contactList;
```

* Либо регенерируй JAXB-классы из XSD, если он у вас есть.

**В. Неправильные/отсутствующие геттеры/сеттеры**
Если поле называется `contactlist` (в коде) — геттер будет `getContactlist()` (маленькая l). Вы вызываете `getContactList()` — это может быть другая сущность/метод или просто нестандарт. Проверь наличие реального геттера в скомпилированном классе.

Проверь рефлексией все методы, содержащие «contact»:

```java
for (Method m : client.getClass().getMethods()) {
    if (m.getName().toLowerCase().contains("contact")) {
        logger.info("Method: {}", m.getName());
    }
}
```

---

# 3) Быстрый обходной вариант — парсим контакты из XML ответом XPath (без переписывания JAXB)

Если нужно быстро получить телефоны/емейлы и не хочется править JAXB, сделай парсер по XML (устойчив к несовпадениям в аннотациях):

```java
private List<Map<String,String>> parseContactsFromXml(String xml, String clientId) {
    List<Map<String,String>> res = new ArrayList<>();
    try {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false); // поставить true, если в XML есть namespace и нужно учитывать
        DocumentBuilder db = dbf.newDocumentBuilder();
        org.w3c.dom.Document dom = db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        XPath xp = XPathFactory.newInstance().newXPath();
        // находим все Contact узлы внутри нужного client (по id). Подогнать пути под ваш XML.
        String expr = String.format("//client[id/text()='%s']//Contact | //client[id/text()='%s']//contact", clientId, clientId);
        NodeList nodes = (NodeList) xp.evaluate(expr, dom, XPathConstants.NODESET);

        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            String type = xp.evaluate("type", el).trim();
            String value = xp.evaluate("value", el).trim();
            if (value.isEmpty()) continue;
            Map<String, String> m = new HashMap<>();
            m.put("type", type);
            m.put("value", value);
            res.add(m);
        }
    } catch (Exception ex) {
        logger.error("Ошибка парсинга contacts из XML", ex);
    }
    return res;
}
```

Вставь это в `findFilteredClients` как fallback: если `client.getContactList() == null` — вызови `parseContactsFromXml(respXml, client.getId())` и подставь первый найденный телефон/email в `clientMap`.

---

# 4) Если findFilteredClients действительно возвращает облегчённый набор — запроси полный профиль

Частая практика в интеграциях: операция «поиска» даёт список клиентов без контактных данных; чтобы получить контакты, нужно отдельный запрос `GET_CLIENT` / `CLIENT_BY_ID` / `CLIENT_FULL` с `clientId`. Проверь в API ИБСО (доках) — как называется операция получения полного клиента. Примерный код (шаблон):

```java
Document doc = createDocument("CLIENT_FULL"); // имя операции взять из доков
ReqFindClient req = new ReqFindClient();
req.setClientId(clientId);
doc.setReqFindClient(req);

Document answer = directABSService.request(doc);
logger.info("Ответ на CLIENT_FULL: {}", marshalDocument(answer));
// далее проверяешь answer.getAnsGetClient().getClient().getContactList()
```

Если нет доков — легко проверить экспериментом: сформируй такой `Document` с тем же `clientId` и посмотри, вернёт ли сервер контактную информацию.

---

# 5) Пример «полезного» патча в findFilteredClients: комбинированный подход

```java
// после получения answerDocument и перед циклом — захвати XML
String respXml = marshalDocument(answerDocument);

for (Client client : answerDocument.getAnsFindClient().getExactClients().getClient()) {
    Map<String,String> clientMap = new HashMap<>();
    clientMap.put("ID", client.getId());

    // существующая инфа
    // ...

    // 1) Попробуем стандартно
    ContactList cl = client.getContactList(); // возможно null

    // 2) Попробуем альтернативный геттер (если сгенерированные классы могли иметь другую нотацию)
    if (cl == null) {
        try {
            Method alt = client.getClass().getMethod("getContactlist"); // маленькая "l"
            Object altVal = alt.invoke(client);
            if (altVal instanceof ContactList) cl = (ContactList) altVal;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) { }
    }

    // 3) Если есть объект ContactList — вытащим контакты
    if (cl != null && cl.getContact() != null) {
        cl.getContact().stream()
          .filter(c -> c.getValue() != null && !c.getValue().isEmpty())
          .forEach(c -> {
              String t = (c.getType() != null) ? c.getType().toUpperCase() : "OTHER";
              if ("EMAIL".equals(t)) clientMap.putIfAbsent("email", c.getValue());
              if ("MOBILE".equals(t) || "PHONE".equals(t)) clientMap.putIfAbsent("phone", c.getValue());
          });
    } else {
        // 4) fallback: парсим XML напрямую
        List<Map<String,String>> contactsFromXml = parseContactsFromXml(respXml, client.getId());
        for (Map<String,String> cn : contactsFromXml) {
            String type = cn.get("type");
            String value = cn.get("value");
            if (type == null) type = "";
            if (type.equalsIgnoreCase("email")) clientMap.putIfAbsent("email", value);
            else clientMap.putIfAbsent("phone", value);
        }
    }

    result.add(clientMap);
}
```

---

# 6) Резюме — кратко, что сделать прямо сейчас

1. **Посмотри/залогируй сырой XML** (п.1). Это даст однозначный ответ: приходят ли контакты с сервера.
2. Если контакты есть в XML — **правь аннотации JAXB** (имена тегов/namespace) или используй XPath fallback (п.3).
3. Если в XML нет контактов — **вызови отдельную операцию получения полного профиля** (п.4).
4. Проверь имена геттеров в сгенерированных классах (`getContactlist()` vs `getContactList()`), и используй reflection временно, если нужно.

---

Если хочешь, я сделаю следующее прямо сейчас (поясни коротко то, что удобно):

* если пришлёшь кусок ответа `marshalDocument(answerDocument)` — я быстро укажу точную причину (какой тег/namespace возвращается) и дам точную правку аннотаций/XPath-шабль.
* либо пришли сгенерированный класс `Client` целиком — я скажу, какие аннотации/геттеры нужно поправить.

Если не хочешь присылать — сделай шаг 1 (залогируй XML) и по результату выполни шаги из п.2–4. Помогу дальше — скажи, какой вариант удобнее.




import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Тесты для метода IbsoServiceImpl.findFilteredClients.
 * Мы не ходим во внешний сервис IBSO, а подставляем фейковые ответы с помощью Mockito.
 */
@ExtendWith(MockitoExtension.class)
class IbsoServiceImplTest {

    // ----------- Моки зависимостей -----------

    /** Это внешний сервис, который реальный код дергает для запроса в ИБСО. */
    @Mock
    private DirectABSService directABSService;

    /**
     * Тестируемый сервис. Все @Mock-поля будут внедрены сюда автоматически,
     * чтобы мы могли управлять поведением зависимостей.
     */
    @InjectMocks
    private IbsoServiceImpl ibsoService;

    // ----------- Позитивный тест -----------

    @Test
    void findFilteredClients_returnsClientMap_whenClientExists() {
        // === Подготовка данных ===

        // Запрос, который пойдет в метод
        ReqFindClient req = new ReqFindClient();
        req.setClientId("123");

        // Поддельный клиент, который как будто пришёл от ИБСО
        Client client = new Client();
        client.setId("123");
        client.setInn("1234567890");
        client.setSurname("Ivanov");
        client.setFirstname("Ivan");
        client.setMiddlename("Ivanovich");
        client.setBirthdate("01.01.1990");
        client.setMainDoc("1111 222222");
        client.setMainContact("79990001122");

        // Упаковываем клиента в структуру, имитирующую XML-ответ
        AnsFindClient ans = new AnsFindClient();
        ExactClients exact = new ExactClients();
        exact.getClient().add(client);
        ans.setExactClients(exact);

        Document fakeResponse = new Document();
        fakeResponse.setAnsFindClient(ans);

        // === Настройка мока ===
        // Говорим Mockito: когда сервис попытается вызвать directABSService.request(),
        // верни наш поддельный Document вместо настоящего запроса
        when(directABSService.request(any())).thenReturn(fakeResponse);

        // === Выполнение тестируемого метода ===
        List<Map<String, String>> result = ibsoService.findFilteredClients(req);

        // === Проверки ===
        assertEquals(1, result.size(), "Должен вернуться один клиент");
        Map<String, String> map = result.get(0);
        assertEquals("123", map.get("ID"));
        assertEquals("Ivanov Ivan Ivanovich", map.get("fio"));
        assertEquals("79990001122", map.get("phone"));
    }

    // ----------- Негативный тест №1: ответ содержит failure -----------

    @Test
    void findFilteredClients_throwsException_whenFailureReturned() {
        // Запрос
        ReqFindClient req = new ReqFindClient();
        req.setClientId("not-exists");

        // Ответ с ошибкой
        Failure failure = new Failure();
        failure.setInfo("Client not found");

        AnsFindClient ans = new AnsFindClient();
        ans.setFailure(failure);

        Document fakeResponse = new Document();
        fakeResponse.setAnsFindClient(ans);

        // Мокаем успешный вызов, но с ошибкой внутри ответа
        when(directABSService.request(any())).thenReturn(fakeResponse);

        // Ожидаем, что метод выбросит RuntimeException
        RuntimeException ex = assertThrows(
            RuntimeException.class,
            () -> ibsoService.findFilteredClients(req)
        );
        assertTrue(ex.getMessage().contains("Client not found"));
    }

    // ----------- Негативный тест №2: исключение при запросе в ИБСО -----------

    @Test
    void findFilteredClients_throwsException_whenRequestFails() {
        // Запрос
        ReqFindClient req = new ReqFindClient();
        req.setClientId("any");

        // Настраиваем мок так, чтобы он бросал исключение (например, сеть упала)
        when(directABSService.request(any()))
            .
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.dynamika.findelivery.ibso.IbsoServiceImpl;
import ru.dynamika.unitsplayer.model.generated.Client;
import ru.dynamika.unitsplayer.model.generated.Document;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для сервиса IBSO (findFilteredClients).
 * Используем JUnit 5 + Mockito.
 */
@ExtendWith(MockitoExtension.class)
class IbsoServiceImplTest {

    @Mock
    private DirectABSService directABSService; // мокаем внешний сервис

    @InjectMocks
    private IbsoServiceImpl ibsoService; // тестируем реальный сервис

    @Test
    @DisplayName("✅ Позитивный кейс: IBSO возвращает клиента, маппинг корректный")
    void testFindFilteredClients_success() {
        // 1. Подготавливаем фейковый ответ от IBSO
        Client client = new Client();
        client.setId("12345");
        client.setInn("7701234567");
        client.setSurname("Иванов");
        client.setFirstname("Иван");
        client.setMiddlename("Иванович");
        client.setBirthdate("01.01.1990");
        client.setMainDoc("1111 222222");
        client.setMainContact("79247694420");

        Document answerDoc = new Document();
        answerDoc.setAnsFindClient(new AnsFindClientStub(client));

        // 2. Мокаем вызов IBSO
        when(directABSService.request(any(Document.class))).thenReturn(answerDoc);

        // 3. Запускаем метод
        ReqFindClient req = new ReqFindClient();
        req.setClientId("12345");
        List<Map<String, String>> result = ibsoService.findFilteredClients(req);

        // 4. Проверяем результат
        assertEquals(1, result.size());
        Map<String, String> clientMap = result.get(0);
        assertEquals("12345", clientMap.get("ID"));
        assertEquals("7701234567", clientMap.get("inn"));
        assertEquals("Иванов Иван Иванович", clientMap.get("fio"));
        assertEquals("01.01.1990", clientMap.get("birthdate"));
        assertEquals("1111 222222", clientMap.get("passport"));
        assertEquals("79247694420", clientMap.get("phone"));
    }

    @Test
    @DisplayName("❌ Негативный кейс: IBSO возвращает failure → должно быть исключение")
    void testFindFilteredClients_failure() {
        // 1. Подготавливаем фейковый ответ с ошибкой
        Document answerDoc = new Document();
        answerDoc.setAnsFindClient(new AnsFindClientFailureStub("Ошибка доступа"));

        when(directABSService.request(any(Document.class))).thenReturn(answerDoc);

        // 2. Проверяем что выбрасывается RuntimeException
        ReqFindClient req = new ReqFindClient();
        assertThrows(RuntimeException.class, () -> ibsoService.findFilteredClients(req));
    }

    @Test
    @DisplayName("⚠️ IBSO вернул пустой список клиентов → результат пустой")
    void testFindFilteredClients_empty() {
        Document answerDoc = new Document();
        answerDoc.setAnsFindClient(new AnsFindClientEmptyStub());

        when(directABSService.request(any(Document.class))).thenReturn(answerDoc);

        ReqFindClient req = new ReqFindClient();
        List<Map<String, String>> result = ibsoService.findFilteredClients(req);

        assertTrue(result.isEmpty());
    }

    // ---- стабы для упрощения теста ----
    private static class AnsFindClientStub extends AnsFindClient {
        private final Client client;
        AnsFindClientStub(Client client) { this.client = client; }
        @Override public List<Client> getExactClients() { return List.of(client); }
    }

    private static class AnsFindClientFailureStub extends AnsFindClient {
        private final Failure failure;
        AnsFindClientFailureStub(String message) {
            this.failure = new Failure();
            this.failure.setInfo(message);
        }
        @Override public Failure getFailure() { return failure; }
    }

    private static class AnsFindClientEmptyStub extends AnsFindClient {
        @Override public List<Client> getExactClients() { return Collections.emptyList(); }
    }
}
























подключения к ИБСО
@Configuration
@ConfigurationProperties(prefix = "ibso")
@Getter
@Setter
public class IbsoProperties {
    private String url;
    private String username;
    private String password;
    private int timeout;
}
🚀 2. Сервис интеграции с ИБСО (DirectIbsoService)
@Slf4j
@Service
@RequiredArgsConstructor
public class DirectIbsoService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final IbsoProperties ibsoProperties;

    public IbsoResponse sendApplication(CreditApplication application) {
        try {
            // Формируем заголовки
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBasicAuth(ibsoProperties.getUsername(), ibsoProperties.getPassword());

            // Формируем тело запроса
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("clientId", application.getMplClientId());
            requestBody.put("amount", application.getAmount());
            requestBody.put("term", application.getTerm());
            requestBody.put("region", application.getRegion());
            requestBody.put("fullName", application.getLastName() + " " + application.getFirstName());
            requestBody.put("phone", application.getPhone());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            log.info("Отправка заявки в ИБСО: {}", requestBody);

            ResponseEntity<IbsoResponse> response = restTemplate.exchange(
                    ibsoProperties.getUrl(),
                    HttpMethod.POST,
                    request,
                    IbsoResponse.class
            );

            log.info("Ответ от ИБСО: {}", response.getBody());

            return response.getBody();

        } catch (HttpStatusCodeException e) {
            log.error("Ошибка при вызове ИБСО: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            return new IbsoResponse("ERROR", null, e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Системная ошибка при отправке в ИБСО", e);
            return new IbsoResponse("ERROR", null, e.getMessage());
        }
    }
}
📦 3. DTO ответа от ИБСО
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class IbsoResponse {
    private String status;      // Например: SUCCESS / ERROR
    private String ibsoAppId;   // ID заявки в ИБСО
    private String message;     // Описание ошибки, если есть
}
🧰 4. Интеграция с бизнес-логикой
В твоём CreditApplicationServiceImpl просто вызываем наш DirectIbsoService:
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditApplicationServiceImpl implements CreditApplicationService {

    private final CreditApplicationRepository applicationRepository;
    private final CreditApplicationMapper applicationMapper;
    private final DirectIbsoService directIbsoService;

    @Override
    @Transactional
    public CreditApplicationResponse save(@NonNull CreditApplicationRequest request) {
        // 1. Сохраняем заявку
        CreditApplication application = applicationRepository.save(
                applicationMapper.createRequestToEntity(request)
        );
        log.info("Заявка сохранена в БД: {}", application.getId());

        // 2. Отправляем в ИБСО
        IbsoResponse ibsoResponse = directIbsoService.sendApplication(application);

        // 3. Формируем ответ клиенту
        CreditApplicationResponse response = new CreditApplicationResponse();
        response.setId(application.getId());
        response.setStatus(ibsoResponse.getStatus());
        response.setErrorMessage(ibsoResponse.getMessage());

        log.info("Возвращаем ответ клиенту: {}", response);
        return response;
    }
}
✅ Результат
Когда маркетплейс делает POST /applicationSave,
ты сохраняешь заявку и отправляешь JSON в ИБСО.
В логах ты увидишь:
Отправка заявки в ИБСО: {...}
Ответ от ИБСО: IbsoResponse(status=SUCCESS, ibsoAppId=12345, message=null)
Клиент получает ответ:
{
  "status": "SUCCESS",
  "id": "7d1b1b4a-19b2-4a59-bf15-74b9a7cd5b98",
  "errorMessage": null
}

