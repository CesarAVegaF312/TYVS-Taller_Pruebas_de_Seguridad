# Imagen de la Registraduria para docker compose (ver docker-compose.yml en la
# raiz del repositorio).
#
# Dos etapas: la primera compila con una version fija de Maven y Java, la
# segunda solo lleva el JRE y el jar. Asi el resultado no depende de lo que
# haya instalado en el equipo de cada persona.

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
# La cache de ~/.m2 sobrevive entre builds: la segunda vez no se descarga nada.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests -Dspotbugs.skip=true package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/target/registraduria-1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
