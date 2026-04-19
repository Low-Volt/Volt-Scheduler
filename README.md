# Calendar App

This project is a full stack Java calendar application built with Spring Boot, Thymeleaf, PostgreSQL, and Docker Compose.

## Features

- Account registration with unique usernames.
- BCrypt password hashing for stored credentials.
- Per-user event management with create, update, and delete flows.
- Required event title and date.
- Optional start time, end time, and description.
- Responsive, modern UI built with server-rendered templates and custom CSS.

## Stack

- Java 25
- Spring Boot 3.5
- Spring Security
- Spring Data JPA
- Thymeleaf
- PostgreSQL
- Docker Compose

## Run locally with Maven

1. Start PostgreSQL with Docker:

   ```bash
   docker compose up db -d
   ```

2. Run the application from the project root:

   ```bash
   mvn spring-boot:run
   ```

3. Open `http://localhost:8080`

The application uses these default database settings unless overridden with environment variables:

- `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/calendar_app`
- `SPRING_DATASOURCE_USERNAME=calendar_user`
- `SPRING_DATASOURCE_PASSWORD=calendar_pass`

## Run with Docker Compose

```bash
docker compose up --build
```

This starts both the Spring Boot app and PostgreSQL.