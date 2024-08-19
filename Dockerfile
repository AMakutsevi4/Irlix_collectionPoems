FROM openjdk:17-jdk-slim

WORKDIR /app

COPY target/Irlix_collectionPoems-0.0.1-SNAPSHOT.jar /app/collectionPoems.jar
COPY src/main/resources/application.properties /app/application.properties

ENTRYPOINT ["java", "-jar", "collectionPoems.jar"]


#Команды для успешного создания контейнера
# создание image
#1. docker build -t collection-poems .
#2. docker tag collection-poems amakutsevi4/collection-poems-spring
#3. docker push amakutsevi4/collection-poems-spring
# установка портов и запуск контейнера
#4. docker run -d -p 8080:8080 collection-poems