package com.example.calendar.controller;

import com.example.calendar.user.RegistrationForm;
import com.example.calendar.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
// Handles login, registration, and account-management HTTP endpoints.
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String login(Authentication authentication,
                        Model model,
                        @RequestParam(value = "view", required = false) String view) {
        if (isAuthenticated(authentication)) {
            return "redirect:/events";
        }

        if (!model.containsAttribute("registrationForm")) {
            model.addAttribute("registrationForm", new RegistrationForm());
        }

        String authView = "register".equalsIgnoreCase(view) ? "register" : "login";
        model.addAttribute("authView", authView);
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Authentication authentication) {
        if (isAuthenticated(authentication)) {
            return "redirect:/events";
        }
        return "redirect:/login?view=register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registrationForm") RegistrationForm registrationForm,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes) {
        if (!registrationForm.getPassword().equals(registrationForm.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch", "Passwords must match.");
        }

        if (!bindingResult.hasFieldErrors("username") && userService.usernameExists(registrationForm.getUsername())) {
            bindingResult.rejectValue("username", "username.exists", "That username is already taken.");
        }

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.registrationForm", bindingResult);
            redirectAttributes.addFlashAttribute("registrationForm", registrationForm);
            redirectAttributes.addFlashAttribute("authView", "register");
            return "redirect:/login?view=register";
        }

        // The controller delegates persistence work to UserService so validation and database writes
        // stay out of the web layer.
        userService.register(registrationForm);
        redirectAttributes.addFlashAttribute("successMessage", "Account created. Sign in to manage your calendar.");
        return "redirect:/login";
    }

    @GetMapping("/")
    public String home(Authentication authentication) {
        return isAuthenticated(authentication) ? "redirect:/events" : "redirect:/login";
    }

    @GetMapping("/account/change-password")
    public String changePasswordForm(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return "redirect:/login";
        }
        return "redirect:/events?panel=password";
    }

    @PostMapping("/account/change-password")
    @ResponseBody
    public ResponseEntity<String> changePassword(@RequestBody ChangePasswordRequest request,
                                                 Principal principal) {
        try {
            String username = principal.getName();
            // UserService verifies the current password, hashes the new password, and saves the user row.
            userService.changePassword(username, request.getCurrentPassword(), request.getNewPassword());
            return ResponseEntity.ok("Password changed successfully");
        } catch (IllegalArgumentException e) {
            HttpStatus status = "Current password is incorrect".equals(e.getMessage())
                    ? HttpStatus.UNAUTHORIZED
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to change password");
        }
    }

    @GetMapping("/account/change-username")
    public String changeUsernameForm(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return "redirect:/login";
        }
        return "redirect:/events?panel=username";
    }

    @PostMapping("/account/change-username")
    @ResponseBody
    public ResponseEntity<String> changeUsername(@RequestBody ChangeUsernameRequest request,
                                                 Principal principal,
                                                 Authentication authentication,
                                                 HttpServletRequest httpRequest,
                                                 HttpServletResponse httpResponse) {
        try {
            String currentUsername = principal.getName();
            // Changing the username updates the users table, then the current security session is reset.
            userService.changeUsername(currentUsername, request.getNewUsername());
            // Username changed: refresh auth state by logging out current session.
            new SecurityContextLogoutHandler().logout(httpRequest, httpResponse, authentication);
            return ResponseEntity.ok("Username changed successfully");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to change username");
        }
    }

    @GetMapping("/account/delete")
    public String deleteAccountForm(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return "redirect:/login";
        }
        return "redirect:/events?panel=delete";
    }

    @PostMapping("/account/delete")
    @ResponseBody
    public ResponseEntity<String> deleteAccount(@RequestBody DeleteAccountRequest request, 
                                                Principal principal,
                                                Authentication authentication,
                                                HttpServletRequest httpRequest,
                                                HttpServletResponse httpResponse) {
        String username = principal.getName();
        
        // Verify the password
        if (!userService.verifyPassword(username, request.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid password");
        }

        try {
            // Log out the user first
            new SecurityContextLogoutHandler().logout(httpRequest, httpResponse, authentication);
            
            // Then delete the user row. Because UserAccount uses CascadeType.REMOVE, the related
            // events owned by that user are deleted from PostgreSQL as well.
            userService.deleteAccount(username);
            return ResponseEntity.ok("Account deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to delete account");
        }
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    // Small request DTOs used when JavaScript posts JSON to account endpoints.
    public static class DeleteAccountRequest {
        private String password;

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class ChangePasswordRequest {
        private String currentPassword;
        private String newPassword;

        public String getCurrentPassword() {
            return currentPassword;
        }

        public void setCurrentPassword(String currentPassword) {
            this.currentPassword = currentPassword;
        }

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }
    }

    public static class ChangeUsernameRequest {
        private String newUsername;

        public String getNewUsername() {
            return newUsername;
        }

        public void setNewUsername(String newUsername) {
            this.newUsername = newUsername;
        }
    }
}