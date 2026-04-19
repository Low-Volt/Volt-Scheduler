package com.example.calendar.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Form object for registration input. Validation happens here before UserService persists a user.
public class RegistrationForm {

    @NotBlank(message = "Username is required.")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters.")
    private String username;

    @NotBlank(message = "Password is required.")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters.")
    @Pattern(regexp = "^(?=\\S+$).*$", message = "Password must not contain spaces.")
    @Pattern(regexp = ".*[A-Z].*", message = "Password must include at least one uppercase letter.")
    @Pattern(regexp = ".*[^A-Za-z0-9].*", message = "Password must include at least one special character.")
    private String password;

    @NotBlank(message = "Confirm your password.")
    private String confirmPassword;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}