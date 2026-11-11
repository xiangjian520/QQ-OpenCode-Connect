package com.xiangjian.qqbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(AppProperties properties, PasswordEncoder encoder) {
        String username = properties.web().adminUsername();
        String password = properties.web().adminPassword();
        if (password == null || password.isBlank() || "change-me-now".equals(password)) {
            throw new IllegalStateException("请在 .env 中设置不是默认值的 WEB_ADMIN_PASSWORD");
        }
        var user = User.withUsername(username)
                .password(encoder.encode(password))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        var csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        RequestMatcher loginMatcher = request -> "/login".equals(request.getServletPath());
        RequestMatcher oneBotMatcher = request -> request.getServletPath().startsWith("/onebot/v11/ws");
        http
                .csrf(configure -> configure
                        .csrfTokenRepository(csrf)
                        .ignoringRequestMatchers(loginMatcher, oneBotMatcher)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/login.html", "/login", "/styles.css", "/login.js",
                                "/favicon.svg", "/onebot/v11/ws", "/actuator/health"
                        ).permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login.html?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login.html?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID", "XSRF-TOKEN"))
                .sessionManagement(session -> session.sessionFixation(fixation -> fixation.migrateSession()))
                .headers(headers -> headers.frameOptions(frame -> frame.deny()));
        return http.build();
    }
}
