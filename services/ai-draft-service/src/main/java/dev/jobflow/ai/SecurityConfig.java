package dev.jobflow.ai;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
@Configuration class SecurityConfig { @Bean SecurityFilterChain security(HttpSecurity http)throws Exception{return http.csrf(c->c.disable()).authorizeHttpRequests(a->a.requestMatchers("/actuator/health","/internal/service-info").permitAll().anyRequest().authenticated()).oauth2ResourceServer(o->o.jwt()).build();} }
