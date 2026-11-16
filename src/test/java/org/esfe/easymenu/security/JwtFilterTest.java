package org.esfe.easymenu.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class JwtFilterTest {

    private static final String TEST_SECRET_BASE64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rechazaJwtDeUsuarioDesactivado() throws Exception {
        String email = "cajero@easymenu.com";
        JwtUtil jwtUtil = new JwtUtil(TEST_SECRET_BASE64);
        UserDetails tokenUser = User.withUsername(email).password("hash").roles("CAJERO").build();
        String token = jwtUtil.generarToken(tokenUser);
        UserDetails disabledUser = User.withUsername(email)
                .password("hash")
                .roles("CAJERO")
                .disabled(true)
                .build();
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(disabledUser);
        JwtFilter filter = new JwtFilter(jwtUtil, userDetailsService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> { };

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void propagaErroresDeBaseDeDatosAlCargarUsuarioSinAutenticar() throws Exception {
        String email = "cajero@easymenu.com";
        JwtUtil jwtUtil = new JwtUtil(TEST_SECRET_BASE64);
        UserDetails tokenUser = User.withUsername(email).password("hash").roles("CAJERO").build();
        String token = jwtUtil.generarToken(tokenUser);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        when(userDetailsService.loadUserByUsername(email))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        JwtFilter filter = new JwtFilter(jwtUtil, userDetailsService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> { };

        assertThrows(
                DataAccessResourceFailureException.class,
                () -> filter.doFilter(request, response, chain)
        );

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        org.junit.jupiter.api.Assertions.assertEquals(200, response.getStatus());
        verify(userDetailsService).loadUserByUsername(email);
    }
}
