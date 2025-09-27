package ru.irlix_collectionpoems.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.irlix_collectionpoems.model.Poem;
import ru.irlix_collectionpoems.model.User;
import ru.irlix_collectionpoems.repository.PoemRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PoemService {

    private final PoemRepository poemRepository;

    public List<Poem> getAllPoems() {
        return poemRepository.findAll();
    }

    public List<Poem> searchPoemsByTitle(String title) {
        return poemRepository.findByTitle(title);
    }

    public List<Poem> searchPoemsByAuthor(User author) {
        return poemRepository.findByAuthor(author);
    }

    public void deletePoem(Long poemId) {
        poemRepository.deleteById(poemId);
    }

    public Optional<Poem> getPoemById(Long poemId) {
        return poemRepository.findById(poemId);
    }
}

Ниже — пошаговое решение, как из ИБСО получить **контакты** (телефоны, e-mail и т. п.) по *ID* клиента, исходя из кода, который вы прислали.

---

## 1. Что у нас уже есть

* **Метод `findFilteredClients(ReqFindClient)`**
  Делает запрос в АБС (directABSService.request) и возвращает `List<Map<String,String>>` с базовой информацией о клиенте (ФИО, ИНН, паспорт, дата рождения).
  В мапу телефон пока не кладётся — есть `TODO`.

* **Класс `Client`**
  Содержит:

  * `ContactList contactList`  → список объектов `Contact`
  * `AddressList addressList`  → адреса
  * `IDcardList iDcardList`    → документы
    Контакты явно уже приходят в ответе (см. `@XmlElement(name="ContactList")`).

Следовательно, телефон/email лежат в `client.getContactList().getContact()`.

---

## 2. Дорабатываем существующий метод

В текущем цикле по найденным клиентам:

```java
for (ru.dynamika.unitsplayer.model.generated.Client client :
        answerDocument.getAnsFindClient().getExactClients().getClient()) {

    Map<String, String> clientMap = new HashMap<>();
    clientMap.put("ID", client.getId());
    clientMap.put("inn", client.getInn());
    clientMap.put("fio", String.join(" ",
            client.getSurname(),
            client.getFirstname(),
            client.getMiddlename()));
    clientMap.put("birthdate", client.getBirthdate());
    clientMap.put("passport", client.getMainDoc());
    // TODO: телефон
    result.add(clientMap);
}
```

Добавляем извлечение контактов:

```java
// пример: берём первый найденный мобильный
if (client.getContactList() != null &&
    client.getContactList().getContact() != null) {

    Optional<String> mobile = client.getContactList()
        .getContact().stream()
        .filter(c -> "MOBILE".equalsIgnoreCase(c.getType()) // или свой критерий
                 || "PHONE".equalsIgnoreCase(c.getType()))
        .map(Contact::getValue)
        .findFirst();

    mobile.ifPresent(phone -> clientMap.put("phone", phone));
}
```

Если нужен e-mail, аналогично фильтруем по `type = EMAIL`.

---

## 3. Вызов метода для получения контактов по *ID*

Чтобы получить контакты по конкретному **clientId**:

```java
ReqFindClient req = new ReqFindClient();
req.setClientId("ID_нужного_клиента");

List<Map<String, String>> clients = ibsoService.findFilteredClients(req);
if (clients.isEmpty()) {
    // не найден
} else {
    Map<String,String> client = clients.get(0);
    String phone = client.get("phone");
    String email = client.get("email"); // если добавите
}
```

---

## 4. Готовый пример модифицированного метода

```java
@Override
public List<Map<String, String>> findFilteredClients(ReqFindClient reqFindClient) {
    Document document = createDocument("CLIENT_NEW");
    document.setReqFindClient(reqFindClient);
    Document answerDocument;

    try {
        logger.info("Запрос в ИБСО: {}", marshalDocument(document));
        answerDocument = directABSService.request(document);
    } catch (Exception e) {
        throw new RuntimeException("Ошибка при получении клиентов из ИБСО", e);
    }

    if (answerDocument.getAnsFindClient().getFailure() != null) {
        throw new RuntimeException(
            answerDocument.getAnsFindClient().getFailure().getInfo()
        );
    }
    if (answerDocument.getAnsFindClient().getExactClients() == null) {
        return new ArrayList<>();
    }

    List<Map<String, String>> result = new ArrayList<>();
    for (Client client : answerDocument.getAnsFindClient()
                                       .getExactClients()
                                       .getClient()) {

        Map<String, String> clientMap = new HashMap<>();
        clientMap.put("ID", client.getId());
        clientMap.put("inn", client.getInn());
        clientMap.put("fio", String.join(" ",
                client.getSurname(),
                client.getFirstname(),
                client.getMiddlename()));
        clientMap.put("birthdate", client.getBirthdate());
        clientMap.put("passport", client.getMainDoc());

        // --- ДОБАВЛЯЕМ КОНТАКТЫ ---
        if (client.getContactList() != null &&
            client.getContactList().getContact() != null) {

            // телефон
            client.getContactList().getContact().stream()
                  .filter(c -> "PHONE".equalsIgnoreCase(c.getType())
                            || "MOBILE".equalsIgnoreCase(c.getType()))
                  .map(Contact::getValue)
                  .findFirst()
                  .ifPresent(phone -> clientMap.put("phone", phone));

            // email (по желанию)
            client.getContactList().getContact().stream()
                  .filter(c -> "EMAIL".equalsIgnoreCase(c.getType()))
                  .map(Contact::getValue)
                  .findFirst()
                  .ifPresent(email -> clientMap.put("email", email));
        }

        result.add(clientMap);
    }
    return result;
}
```

---

### Что проверить дополнительно

* Поля `Contact::getType()` и `Contact::getValue()` — убедитесь, что именно так называются геттеры.
* Уточните в XSD/схемах ИБСО возможные значения type (`MOBILE`, `PHONE`, `EMAIL` и т.д.).
* Если нужно несколько телефонов/почт — кладите их в `List` или через запятую.

Так вы сможете по *ID* пользователя сформировать `ReqFindClient`, вызвать метод и получить контакты из ИБСО.

