package com.acme.platform.config;

import com.acme.platform.web.CorrelationIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Public, stateless, read-only security configuration (design D4). There is no authentication: the
 * read API is public ({@code standards/security.md} §1). CSRF is disabled because there are no
 * cookies/sessions and no state-changing endpoints; leaving it enabled would turn a write attempt
 * into 403 instead of the correct 405/404 refusal.
 *
 * <p>{@link CorrelationIdFilter} is registered on this chain (rather than as a generic servlet
 * filter) so it runs on the ERROR dispatch too: Boot's security auto-configuration registers the
 * security filter chain for the {@code REQUEST}, {@code ASYNC} and {@code ERROR} dispatcher types.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(basic -> basic.disable())
        .formLogin(form -> form.disable())
        .logout(logout -> logout.disable())
        // Registered explicitly (rather than relying solely on server.forward-headers-strategy,
        // which only auto-registers a filter for the real embedded servlet container, not the
        // @WebMvcTest slice) so X-Forwarded-Proto/-Host are honoured identically in production
        // and in tests: by HeaderWriterFilter (HSTS) and by the self-link builder downstream.
        .addFilterBefore(new ForwardedHeaderFilter(), SecurityContextHolderFilter.class)
        .addFilterBefore(new CorrelationIdFilter(), BasicAuthenticationFilter.class)
        .headers(
            headers ->
                headers
                    .contentTypeOptions(contentTypeOptions -> {})
                    .frameOptions(frameOptions -> frameOptions.deny())
                    .contentSecurityPolicy(
                        csp ->
                            csp.policyDirectives(
                                "default-src 'self'; img-src 'self' data:; "
                                    + "style-src 'self' 'unsafe-inline'; object-src 'none'; "
                                    + "frame-ancestors 'none'"))
                    .httpStrictTransportSecurity(hsts -> {}));

    return http.build();
  }
}
