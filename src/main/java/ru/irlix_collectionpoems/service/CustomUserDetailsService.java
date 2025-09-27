уpackage ru.irlix_collectionpoems.service;

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

