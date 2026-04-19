# Volt Scheduler Java Code Guide

This guide explains every Java file in the project, what each class is responsible for, how the main methods are used, and where PostgreSQL fits into the application.

## High-Level Flow

1. A browser request hits a controller in `com.example.calendar.controller`.
2. The controller calls a service in `event` or `user`.
3. The service applies business rules and calls a Spring Data JPA repository.
4. Hibernate translates entity/repository operations into SQL for PostgreSQL.
5. PostgreSQL stores rows in tables such as `users`, `events`, and `persistent_logins`.

## Where PostgreSQL Is Configured

PostgreSQL is wired in [src/main/resources/application.properties](src/main/resources/application.properties):

```properties
spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/calendar_app}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:calendar_user}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:calendar_pass}
spring.jpa.hibernate.ddl-auto=update
```

Key point:

- `spring.jpa.hibernate.ddl-auto=update` tells Hibernate to inspect the `@Entity` classes and create or update matching PostgreSQL tables automatically.

That means the application does not have a manual SQL migration file for the main tables. Instead:

- `UserAccount` creates/updates the `users` table.
- `CalendarEvent` creates/updates the `events` table.
- `SecurityConfig.ensureRememberMeTable(...)` creates the `persistent_logins` table manually with SQL because it is for Spring Security's remember-me feature, not one of the app's JPA entities.

## PostgreSQL Table Creation, Saving, and Reading

### Table creation

The main application tables come from these entity classes:

- [src/main/java/com/example/calendar/user/UserAccount.java](src/main/java/com/example/calendar/user/UserAccount.java)
- [src/main/java/com/example/calendar/event/CalendarEvent.java](src/main/java/com/example/calendar/event/CalendarEvent.java)

Important annotations:

- `@Entity`: marks the class as a database-backed JPA entity.
- `@Table(name = "...")`: sets the PostgreSQL table name.
- `@Id` and `@GeneratedValue(strategy = GenerationType.IDENTITY)`: define the primary key, with PostgreSQL generating the id value.
- `@ManyToOne` and `@OneToMany`: define relationships between tables.

The remember-me table is created in:

- [src/main/java/com/example/calendar/config/SecurityConfig.java](src/main/java/com/example/calendar/config/SecurityConfig.java)

Method:

- `ensureRememberMeTable(JdbcTemplate jdbcTemplate)`

This method runs:

```sql
CREATE TABLE IF NOT EXISTS persistent_logins (
    username VARCHAR(64) NOT NULL,
    series VARCHAR(64) PRIMARY KEY,
    token VARCHAR(64) NOT NULL,
    last_used TIMESTAMP NOT NULL
)
```

### How data is saved

The save pattern is:

1. Controller accepts form input.
2. Service builds or updates an entity.
3. Repository `save(...)` is called.
4. Hibernate generates an `INSERT` or `UPDATE`.
5. PostgreSQL stores the row.

Examples:

- User registration:
  - [src/main/java/com/example/calendar/controller/AuthController.java](src/main/java/com/example/calendar/controller/AuthController.java)
  - [src/main/java/com/example/calendar/user/UserService.java](src/main/java/com/example/calendar/user/UserService.java)
  - `UserService.register(...)` creates a `UserAccount` and calls `userAccountRepository.save(userAccount)`.

- Event creation:
  - [src/main/java/com/example/calendar/controller/DashboardController.java](src/main/java/com/example/calendar/controller/DashboardController.java)
  - [src/main/java/com/example/calendar/event/EventService.java](src/main/java/com/example/calendar/event/EventService.java)
  - `EventService.createEvent(...)` creates a `CalendarEvent` and calls `calendarEventRepository.save(event)`.

- Password and username changes:
  - `UserService.changePassword(...)`
  - `UserService.changeUsername(...)`
  - Both update an existing entity and call `save(...)`, which becomes an SQL `UPDATE`.

### How data is read

The read pattern is:

1. Controller needs data for the current request.
2. Service loads the related user or event.
3. Repository method runs a query.
4. Hibernate maps result rows back into Java objects.

Examples:

- Dashboard event list:
  - `DashboardController.dashboard(...)`
  - `EventService.listEventsForUser(...)`
  - `CalendarEventRepository.findAllByOwnerOrderByEventDateAscStartTimeAscIdAsc(...)`
  - This becomes a `SELECT` filtered by the signed-in user.

- Single event for editing:
  - `EventService.getEventForUser(...)`
  - `CalendarEventRepository.findByIdAndOwner(...)`
  - This prevents a user from editing someone else's event.

- Login:
  - `CustomUserDetailsService.loadUserByUsername(...)`
  - `UserAccountRepository.findByUsernameIgnoreCase(...)`
  - This reads a user row so Spring Security can authenticate the password.

## File-by-File Breakdown

### `CalendarApplication.java`

Purpose:

- Starts the Spring Boot application.

Methods:

- `main(String[] args)`
  - Entry point for the whole app.
  - Starts the embedded server and all Spring-managed beans.

### `config/SecurityConfig.java`

Purpose:

- Configures authentication, protected routes, remember-me persistence, and password hashing.

Methods:

- `securityFilterChain(...)`
  - Defines which routes are public and which require login.
  - Configures form login, logout, and remember-me.

- `persistentTokenRepository(DataSource dataSource)`
  - Connects Spring Security remember-me tokens to the database.

- `jdbcTemplate(DataSource dataSource)`
  - Exposes a `JdbcTemplate` for direct SQL access.

- `ensureRememberMeTable(JdbcTemplate jdbcTemplate)`
  - Creates the `persistent_logins` table if it does not exist.
  - This is one of the most important PostgreSQL spots in the code because it is explicit SQL.

- `passwordEncoder()`
  - Returns a BCrypt encoder used before passwords are saved.

### `controller/AuthController.java`

Purpose:

- Handles login, registration, account changes, and account deletion endpoints.

Methods:

- `login(Authentication authentication)`
  - Shows the login page unless the user is already signed in.

- `registerForm(Authentication authentication, Model model)`
  - Shows the registration form.

- `register(...)`
  - Validates registration input and calls `userService.register(...)`.
  - This is part of the flow that inserts a row into the `users` table.

- `home(Authentication authentication)`
  - Redirects to the correct start page.

- `changePasswordForm(...)`
  - Returns the change-password page.

- `changePassword(...)`
  - Accepts JSON, verifies the current password, and updates the stored password hash.

- `changeUsernameForm(...)`
  - Returns the change-username page.

- `changeUsername(...)`
  - Updates the username in the `users` table and logs the user out.

- `deleteAccountForm(...)`
  - Returns the delete-account page.

- `deleteAccount(...)`
  - Verifies the password, logs out the user, and deletes the account.
  - Because of cascade settings on `UserAccount`, related events are removed too.

- `isAuthenticated(...)`
  - Small helper to detect real signed-in users.

Inner request classes:

- `DeleteAccountRequest`
- `ChangePasswordRequest`
- `ChangeUsernameRequest`

These are request DTOs for JSON account operations.

### `controller/DashboardController.java`

Purpose:

- Serves the main dashboard and handles event CRUD requests.

Methods:

- `dashboard(...)`
  - Loads current events and returns the dashboard view.

- `createEvent(...)`
  - Validates the event form and calls `eventService.createEvent(...)`.
  - This leads to an `INSERT` into the `events` table.

- `editEvent(...)`
  - Loads one event for the edit screen.

- `updateEvent(...)`
  - Validates edits and updates an existing event row.

- `deleteEvent(...)`
  - Deletes an existing event row.

- `populateDashboard(...)`
  - Shared helper that reads event rows for the signed-in user.

- `validateTimeRange(...)`
  - Prevents end time from being earlier than start time.

### `event/CalendarEvent.java`

Purpose:

- Entity class for the `events` table.

Fields:

- `id`: primary key.
- `title`: event name.
- `eventDate`: event date.
- `startTime`: optional start time.
- `endTime`: optional end time.
- `description`: optional notes.
- `owner`: foreign key relationship back to `users`.

How it works with PostgreSQL:

- Hibernate maps one `CalendarEvent` object to one row in the `events` table.
- The `owner` field uses `owner_id` as a foreign key.

### `event/CalendarEventForm.java`

Purpose:

- Validation and form-binding object for event input.

Methods:

- Standard getters and setters for form fields.

Important note:

- This class is not a database entity.
- It protects the database layer by validating input before the service builds an entity.

### `event/CalendarEventRepository.java`

Purpose:

- Repository for event table queries.

Methods:

- `findAllByOwnerOrderByEventDateAscStartTimeAscIdAsc(UserAccount owner)`
  - Reads all events for one user in display order.

- `findByIdAndOwner(Long id, UserAccount owner)`
  - Reads exactly one event and ensures it belongs to that user.

How it works:

- You do not write SQL here.
- Spring Data parses the method names and builds the SQL automatically.

### `event/EventService.java`

Purpose:

- Contains event business logic and repository interaction.

Methods:

- `listEventsForUser(String username)`
  - Loads a user, then reads all of that user's events.

- `createEvent(CalendarEventForm form, String username)`
  - Creates a new entity from the form and saves it.

- `getEventForUser(Long eventId, String username)`
  - Reads one event row for the current user.

- `updateEvent(Long eventId, CalendarEventForm form, String username)`
  - Loads an existing event, copies new values onto it, and lets Hibernate update it.

- `deleteEvent(Long eventId, String username)`
  - Deletes one event row.

- `toForm(CalendarEvent event)`
  - Converts an entity into a form object for the edit page.

- `applyForm(CalendarEvent event, CalendarEventForm form, UserAccount owner)`
  - Internal mapper from validated form input into an entity.

PostgreSQL emphasis:

- This service is where event data most clearly moves into and out of PostgreSQL.
- `save(...)` writes rows.
- `find...(...)` reads rows.
- `delete(...)` removes rows.

### `user/CustomUserDetailsService.java`

Purpose:

- Bridges your `users` table to Spring Security.

Methods:

- `loadUserByUsername(String username)`
  - Reads the matching user row and converts it into Spring Security's `UserDetails` object.

### `user/RegistrationForm.java`

Purpose:

- Validation and binding object for registration.

Fields:

- `username`
- `password`
- `confirmPassword`

How it works:

- Validation annotations reject bad input before `UserService.register(...)` tries to save anything.

### `user/UserAccount.java`

Purpose:

- Entity class for the `users` table.

Fields:

- `id`: primary key.
- `username`: unique login name.
- `passwordHash`: stored BCrypt hash, not the raw password.
- `createdAt`: timestamp for row creation.
- `events`: mapped child collection of owned events.

How it works with PostgreSQL:

- Hibernate maps each `UserAccount` object to one row in `users`.
- `cascade = CascadeType.REMOVE` means deleting a user also deletes related event rows.

### `user/UserAccountRepository.java`

Purpose:

- Repository for user table queries.

Methods:

- `findByUsernameIgnoreCase(String username)`
  - Reads a user row by username.

- `existsByUsernameIgnoreCase(String username)`
  - Checks whether a username is already taken.

### `user/UserService.java`

Purpose:

- Handles user-related business rules, validation, hashing, and persistence.

Methods:

- `register(RegistrationForm form)`
  - Validates password rules, builds a new `UserAccount`, hashes the password, and saves it.

- `usernameExists(String username)`
  - Checks for duplicates.

- `getByUsername(String username)`
  - Shared user lookup.

- `verifyPassword(String username, String password)`
  - Checks a raw password against the saved hash.

- `deleteAccount(String username)`
  - Deletes a user and, through cascade rules, that user's events.

- `changePassword(String username, String oldPassword, String newPassword)`
  - Verifies the old password, validates the new one, hashes it, and updates the user row.

- `validatePasswordOrThrow(String password)`
  - Internal rule enforcement for password strength.

- `changeUsername(String oldUsername, String newUsername)`
  - Updates the username in the `users` table.

### `CalendarApplicationTests.java`

Purpose:

- Minimal boot test to confirm the Spring context starts.

Methods:

- `contextLoads()`
  - Empty test body; success means the app context started correctly.

Database note:

- Uses H2 configured in PostgreSQL compatibility mode instead of the real PostgreSQL server.

## Quick PostgreSQL Mental Model for This Project

If you want to understand the database side first, focus on these files in this order:

1. [src/main/resources/application.properties](src/main/resources/application.properties)
2. [src/main/java/com/example/calendar/user/UserAccount.java](src/main/java/com/example/calendar/user/UserAccount.java)
3. [src/main/java/com/example/calendar/event/CalendarEvent.java](src/main/java/com/example/calendar/event/CalendarEvent.java)
4. [src/main/java/com/example/calendar/user/UserAccountRepository.java](src/main/java/com/example/calendar/user/UserAccountRepository.java)
5. [src/main/java/com/example/calendar/event/CalendarEventRepository.java](src/main/java/com/example/calendar/event/CalendarEventRepository.java)
6. [src/main/java/com/example/calendar/user/UserService.java](src/main/java/com/example/calendar/user/UserService.java)
7. [src/main/java/com/example/calendar/event/EventService.java](src/main/java/com/example/calendar/event/EventService.java)
8. [src/main/java/com/example/calendar/config/SecurityConfig.java](src/main/java/com/example/calendar/config/SecurityConfig.java)

That order shows:

- how the database connection is configured,
- how tables are declared,
- how queries are expressed,
- how inserts/updates/deletes happen,
- and where one table is still created manually with SQL.

## Summary of the Most Important Database Spots

- Table definitions:
  - `UserAccount`
  - `CalendarEvent`

- Manual SQL table creation:
  - `SecurityConfig.ensureRememberMeTable(...)`

- Saving data:
  - `UserService.register(...)`
  - `UserService.changePassword(...)`
  - `UserService.changeUsername(...)`
  - `EventService.createEvent(...)`

- Reading data:
  - `CustomUserDetailsService.loadUserByUsername(...)`
  - `EventService.listEventsForUser(...)`
  - `EventService.getEventForUser(...)`

- Deleting data:
  - `UserService.deleteAccount(...)`
  - `EventService.deleteEvent(...)`