package com.omnicare.security.config;

import com.omnicare.config.AppProperties;
import com.omnicare.security.OAuth2LoginSuccessHandler;
import com.omnicare.security.filter.RegistrationStatusFilter;
import com.omnicare.security.service.OmnicareOidcUserService;
import com.omnicare.security.service.OmnicareOAuth2UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

        @Bean
        public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
                        throws Exception {
                return authenticationConfiguration.getAuthenticationManager();
        }

        @Bean
        @Profile("dev")
        public SecurityFilterChain securityFilterChainDev(
                        HttpSecurity http,
                        OmnicareOAuth2UserService omnicareOAuth2UserService,
                        OmnicareOidcUserService omnicareOidcUserService,
                        OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
                        JwtDecoder jwtDecoder,
                        OAuth2AuthorizationRequestResolver authorizationRequestResolver,
                        RegistrationStatusFilter registrationStatusFilter) throws Exception {
                http
                                .cors(Customizer.withDefaults())
                                .csrf(csrf -> csrf.disable())
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**")
                                                .permitAll()
                                                .requestMatchers("/ws", "/ws/**")
                                                .permitAll()
                                                .requestMatchers("/", "/error", "/login/**", "/oauth2/**",
                                                                "/auth/dev/**", "/dev/**")
                                                .permitAll()
                                                .requestMatchers("/api/auth/**").permitAll()
                                                .requestMatchers("/api/auth/dev/**").permitAll()
                                                .requestMatchers("/api/auth/login").permitAll()
                                                .requestMatchers("/api/auth/register").permitAll()
                                                .requestMatchers("/api/auth/google").permitAll()
                                                .requestMatchers("/api/auth/verify-email").permitAll()
                                                .requestMatchers("/api/auth/verify-email-otp").permitAll()
                                                .requestMatchers("/api/auth/set-phone").permitAll()
                                                .requestMatchers("/api/auth/request-phone-otp").permitAll()
                                                .requestMatchers("/api/auth/verify-phone-otp").permitAll()
                                                .requestMatchers("/api/medications/search").permitAll()
                                                .requestMatchers("/v1/auth/**").permitAll()
                                                .requestMatchers("/api/**").authenticated()
                                                .requestMatchers("/v1/**").authenticated()
                                                .anyRequest().authenticated())
                                .oauth2Login(oauth2 -> oauth2
                                                .authorizationEndpoint(authorization -> authorization
                                                                .authorizationRequestResolver(
                                                                                authorizationRequestResolver))
                                                .userInfoEndpoint(userInfo -> userInfo
                                                                .userService(omnicareOAuth2UserService)
                                                                .oidcUserService(omnicareOidcUserService))
                                                .successHandler(oAuth2LoginSuccessHandler))
                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .bearerTokenResolver(bearerTokenResolver())
                                                .jwt(jwt -> jwt
                                                                .decoder(jwtDecoder)
                                                                .jwtAuthenticationConverter(
                                                                                jwtAuthenticationConverter())));

                http.addFilterAfter(registrationStatusFilter, BearerTokenAuthenticationFilter.class);

                return http.build();
        }

        @Bean
        @Profile("!dev")
        public SecurityFilterChain securityFilterChain(
                        HttpSecurity http,
                        OmnicareOAuth2UserService omnicareOAuth2UserService,
                        OmnicareOidcUserService omnicareOidcUserService,
                        OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
                        JwtDecoder jwtDecoder,
                        OAuth2AuthorizationRequestResolver authorizationRequestResolver,
                        RegistrationStatusFilter registrationStatusFilter) throws Exception {
                http
                                .cors(Customizer.withDefaults())
                                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/auth/**", "/v1/auth/**"))
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**")
                                                .permitAll()
                                                .requestMatchers("/ws", "/ws/**")
                                                .permitAll()
                                                .requestMatchers("/", "/error", "/login/**", "/oauth2/**",
                                                                "/auth/dev/**", "/dev/**")
                                                .permitAll()
                                                .requestMatchers("/api/auth/**").permitAll()
                                                .requestMatchers("/api/auth/dev/**").permitAll()
                                                .requestMatchers("/api/auth/login").permitAll()
                                                .requestMatchers("/api/auth/register").permitAll()
                                                .requestMatchers("/api/auth/google").permitAll()
                                                .requestMatchers("/api/auth/verify-email").permitAll()
                                                .requestMatchers("/api/auth/verify-email-otp").permitAll()
                                                .requestMatchers("/api/auth/set-phone").permitAll()
                                                .requestMatchers("/api/auth/request-phone-otp").permitAll()
                                                .requestMatchers("/api/auth/verify-phone-otp").permitAll()
                                                .requestMatchers("/api/medications/search").permitAll()
                                                .requestMatchers("/v1/auth/**").permitAll()
                                                .requestMatchers("/api/**").authenticated()
                                                .requestMatchers("/v1/**").authenticated()
                                                .anyRequest().authenticated())
                                .oauth2Login(oauth2 -> oauth2
                                                .authorizationEndpoint(authorization -> authorization
                                                                .authorizationRequestResolver(
                                                                                authorizationRequestResolver))
                                                .userInfoEndpoint(userInfo -> userInfo
                                                                .userService(omnicareOAuth2UserService)
                                                                .oidcUserService(omnicareOidcUserService))
                                                .successHandler(oAuth2LoginSuccessHandler))
                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .bearerTokenResolver(bearerTokenResolver())
                                                .jwt(jwt -> jwt
                                                                .decoder(jwtDecoder)
                                                                .jwtAuthenticationConverter(
                                                                                jwtAuthenticationConverter())));

                http.addFilterAfter(registrationStatusFilter, BearerTokenAuthenticationFilter.class);

                return http.build();
        }

        @Bean
        public OAuth2AuthorizationRequestResolver authorizationRequestResolver(
                        ClientRegistrationRepository clientRegistrationRepository) {
                DefaultOAuth2AuthorizationRequestResolver delegate = new DefaultOAuth2AuthorizationRequestResolver(
                                clientRegistrationRepository, "/oauth2/authorization");
                return new GooglePromptAuthorizationRequestResolver(delegate);
        }

        @Bean
        public BearerTokenResolver bearerTokenResolver() {
                DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
                return request -> {
                        String path = request.getRequestURI();
                        if (path != null) {
                                // Do NOT attempt to resolve Bearer tokens for public auth endpoints
                                // (login/register/etc.).
                                // However, /api/auth/dev/** endpoints are authenticated in our dev flows, so
                                // they must
                                // still accept JWTs.
                                if (path.startsWith("/api/auth/") && !path.startsWith("/api/auth/dev/")) {
                                        return null;
                                }
                                if (path.startsWith("/v1/auth/")) {
                                        return null;
                                }
                        }
                        return delegate.resolve(request);
                };
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource(AppProperties appProperties) {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOriginPatterns(List.of(
                                "http://localhost:*",
                                "http://127.0.0.1:*",
                                "http://192.168.154.91:*"));
                configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
                configuration.setExposedHeaders(List.of("Authorization"));
                configuration.setAllowCredentials(false);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }

        @Bean
        public JwtAuthenticationConverter jwtAuthenticationConverter() {
                JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
                authoritiesConverter.setAuthorityPrefix("ROLE_");
                authoritiesConverter.setAuthoritiesClaimName("roles");

                JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
                converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
                return converter;
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }
}
