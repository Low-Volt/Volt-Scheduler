package com.example.calendar.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.regex.Pattern;

@Service
// User-related business logic: validation, password hashing, and repository-backed persistence.
public class UserService {

    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern SPECIAL_PATTERN = Pattern.compile(".*[^A-Za-z0-9].*");
    private static final Pattern SPACE_PATTERN = Pattern.compile(".*\\s.*");

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void register(RegistrationForm form) {
        validatePasswordOrThrow(form.getPassword());
        UserAccount userAccount = new UserAccount();
        userAccount.setUsername(form.getUsername().trim());
        userAccount.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        // save(...) issues an INSERT into the users table.
        userAccountRepository.save(userAccount);
    }

    public boolean usernameExists(String username) {
        // Fast existence check used to stop duplicate usernames before writing to PostgreSQL.
        return userAccountRepository.existsByUsernameIgnoreCase(username.trim());
    }

    public UserAccount getByUsername(String username) {
        // Shared lookup used by multiple services before loading or mutating related data.
        return userAccountRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    public boolean verifyPassword(String username, String password) {
        UserAccount user = getByUsername(username);
        // Compares the raw password to the hashed value stored in the users table.
        return passwordEncoder.matches(password, user.getPasswordHash());
    }

    @Transactional
    public void deleteAccount(String username) {
        UserAccount user = getByUsername(username);
        // Deletes the user row; related event rows are removed by the entity relationship mapping.
        userAccountRepository.delete(user);
    }

    @Transactional
    public void changePassword(String username, String oldPassword, String newPassword) {
        UserAccount user = getByUsername(username);
        
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        validatePasswordOrThrow(newPassword);
        
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // save(...) persists the updated password hash to the existing user row.
        userAccountRepository.save(user);
    }

    private void validatePasswordOrThrow(String password) {
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new IllegalArgumentException("Password must be between 8 and 72 characters.");
        }
        if (SPACE_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException("Password must not contain spaces.");
        }
        if (!UPPERCASE_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException("Password must include at least one uppercase letter.");
        }
        if (!SPECIAL_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException("Password must include at least one special character.");
        }
    }

    @Transactional
    public void changeUsername(String oldUsername, String newUsername) {
        if (usernameExists(newUsername)) {
            throw new IllegalArgumentException("Username is already taken");
        }
        
        UserAccount user = getByUsername(oldUsername);
        user.setUsername(newUsername.trim());
        // This becomes an UPDATE on the users table.
        userAccountRepository.save(user);
    }
}