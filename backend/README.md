# backend/

Spring Boot 3 (Java 25 LTS) backend using Spring JdbcTemplate with hand-written SQL and Flyway for schema migrations. NO JPA/Hibernate; `ddl-auto` is not used. Java only calls stored procedures/queries — business rules live in MySQL.

Build with the Maven Wrapper and JDK 25:

```shell
./mvnw clean test
```

The Docker image also uses Eclipse Temurin 25 for both build and runtime stages.
