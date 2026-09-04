package dev.modularforge.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
public interface SecurityFilterExtension {
    void configure(HttpSecurity http) throws Exception;
}
