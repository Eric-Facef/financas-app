# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Sem "dependency:go-offline": ele resolve plugins e árvores transitivas inteiras e é o passo mais
# sensível a artefatos SNAPSHOT/ausentes. O "package" já baixa só o que o projeto realmente usa.
COPY pom.xml .
COPY src ./src
RUN mvn -q -B package -DskipTests

# ---- runtime ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]