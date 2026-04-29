package com.example.calendar.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

@Configuration
// Central security wiring: login rules, remember-me persistence, and password hashing.
public class SecurityConfig {

    private static final int REMEMBER_ME_SECONDS = 24 * 60 * 60;

    @Value("${app.remember-me.key:change-this-in-production}")
    private String rememberMeKey;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                           UserDetailsService userDetailsService,
                                           PersistentTokenRepository persistentTokenRepository) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/css/**", "/register", "/login").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/events", true)
                        .permitAll())
                .rememberMe(remember -> remember
                    .alwaysRemember(true)
                        .rememberMeParameter("remember-me")
                        .tokenValiditySeconds(REMEMBER_ME_SECONDS)
                        .tokenRepository(persistentTokenRepository)
                        .userDetailsService(userDetailsService)
                        .key(rememberMeKey))
                .logout(logout -> logout
                        .deleteCookies("JSESSIONID", "remember-me")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll());

        return http.build();
    }

    @Bean
    PersistentTokenRepository persistentTokenRepository(DataSource dataSource) {
        // Stores remember-me tokens in PostgreSQL so the cookie can survive server restarts.
        JdbcTokenRepositoryImpl repository = new JdbcTokenRepositoryImpl();
        repository.setDataSource(dataSource);
        return repository;
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    ApplicationRunner ensureRememberMeTable(JdbcTemplate jdbcTemplate) {
        // This is the one table created with raw SQL instead of JPA/Hibernate.
        // The users and events tables come from the @Entity classes, but Spring Security's
        // remember-me feature expects this schema to exist ahead of time.
        return args -> jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS persistent_logins (
                    username VARCHAR(64) NOT NULL,
                    series VARCHAR(64) PRIMARY KEY,
                    token VARCHAR(64) NOT NULL,
                    last_used TIMESTAMP NOT NULL
                )
                """);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // Passwords are hashed before they are saved, so PostgreSQL never stores plain text passwords.
        return new BCryptPasswordEncoder();
    }
}