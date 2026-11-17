package org.esfe.easymenu.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

        private final JwtFilter jwtFilter;
        private final CustomUserDetailsService userDetailsService;

        public SecurityConfig(
                        JwtFilter jwtFilter,
                        CustomUserDetailsService userDetailsService) {
                this.jwtFilter = jwtFilter;
                this.userDetailsService = userDetailsService;
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public AuthenticationProvider authenticationProvider() {

                DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();

                authProvider.setUserDetailsService(userDetailsService);

                authProvider.setPasswordEncoder(
                                passwordEncoder());

                return authProvider;
        }

        @Bean
        public AuthenticationManager authenticationManager(
                        AuthenticationConfiguration config) throws Exception {

                return config.getAuthenticationManager();
        }

        @Bean
        public SecurityFilterChain securityFilterChain(
                        HttpSecurity http,
                        PublicPedidoRateLimitFilter publicPedidoRateLimitFilter) throws Exception {

                http
                                .csrf(csrf -> csrf.disable())

                                .sessionManagement(session -> session.sessionCreationPolicy(
                                                SessionCreationPolicy.STATELESS))

                                .authorizeHttpRequests(auth -> auth

                                                // LOGIN Y SWAGGER
                                                .requestMatchers(
                                                                "/api/auth/**",
                                                                "/v3/api-docs/**",
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html")
                                                .permitAll()

                                                // USUARIOS - SOLO ADMINISTRADOR
                                                .requestMatchers(
                                                                "/api/usuarios",
                                                                "/api/usuarios/**")
                                                .hasRole("ADMINISTRADOR")

                                                // PRODUCTOS - CONSULTA PÚBLICA
                                                .requestMatchers(
                                                                HttpMethod.GET,
                                                                "/api/productos/**")
                                                .permitAll()

                                                // PRODUCTOS - SOLO ADMINISTRADOR
                                                .requestMatchers(
                                                                HttpMethod.POST,
                                                                "/api/productos/**")
                                                .hasRole("ADMINISTRADOR")

                                                .requestMatchers(
                                                                HttpMethod.PUT,
                                                                "/api/productos/**")
                                                .hasRole("ADMINISTRADOR")

                                                .requestMatchers(
                                                                HttpMethod.PATCH,
                                                                "/api/productos/**")
                                                .hasRole("ADMINISTRADOR")

                                                .requestMatchers(
                                                                HttpMethod.DELETE,
                                                                "/api/productos/**")
                                                .hasRole("ADMINISTRADOR")

                                                // PEDIDOS - CLIENTE SIN LOGIN

                                                // Crear un pedido
                                                .requestMatchers(
                                                                HttpMethod.POST,
                                                                "/api/pedidos")
                                                .permitAll()

                                                // Consultar un pedido mediante su código corto
                                                .requestMatchers(
                                                                HttpMethod.GET,
                                                                "/api/pedidos/codigo/**")
                                                .permitAll()

                                                // Cancelar su pedido mediante código corto
                                                .requestMatchers(
                                                                HttpMethod.PATCH,
                                                                "/api/pedidos/codigo/*/cancelar")
                                                .permitAll()

                                                // PEDIDOS - PERSONAL AUTENTICADO
                                                .requestMatchers(
                                                                "/api/pedidos/**")
                                                .hasAnyRole(
                                                                "ADMINISTRADOR",
                                                                "CAJERO",
                                                                "COCINA")
                                                // El resumen de ventas es exclusivo del administrador
                                                .requestMatchers(
                                                                HttpMethod.GET,
                                                                "/api/pagos/resumen")
                                                .hasRole("ADMINISTRADOR")
                                                // PAGOS - SOLO CAJERO Y ADMINISTRADOR
                                                .requestMatchers(
                                                                "/api/pagos/**")
                                                .hasAnyRole(
                                                                "ADMINISTRADOR",
                                                                "CAJERO")

                                                // Permitir que Spring muestre el error real
                                                .requestMatchers("/error")
                                                .permitAll()

                                                // Cualquier otra ruta necesita login
                                                .anyRequest()
                                                .authenticated())

                                .authenticationProvider(
                                                authenticationProvider())

                                .addFilterBefore(
                                                publicPedidoRateLimitFilter,
                                                UsernamePasswordAuthenticationFilter.class)

                                .addFilterBefore(
                                                jwtFilter,
                                                UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        @Bean
        public PublicPedidoRateLimitFilter publicPedidoRateLimitFilter(ObjectMapper objectMapper) {
                return new PublicPedidoRateLimitFilter(objectMapper);
        }

        @Bean
        public FilterRegistrationBean<PublicPedidoRateLimitFilter> publicPedidoRateLimitFilterRegistration(
                        PublicPedidoRateLimitFilter filter) {
                FilterRegistrationBean<PublicPedidoRateLimitFilter> registration =
                                new FilterRegistrationBean<>(filter);
                registration.setEnabled(false);
                return registration;
        }
}