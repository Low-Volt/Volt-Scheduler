package com.example.calendar.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// Repository abstraction for the users table.
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    // Case-insensitive lookup used by login and by service methods that need the full user row.
    Optional<UserAccount> findByUsernameIgnoreCase(String username);

    // Used to prevent duplicate usernames before inserting or updating a user.
    boolean existsByUsernameIgnoreCase(String username);
}