package org.esfe.easymenu.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PublicPedidoRateLimitFilterTest {

    @Test
    void limitaLasConsultasPublicasPorDireccionDeCliente() throws Exception {
        PublicPedidoRateLimitFilter filter = new PublicPedidoRateLimitFilter(
                new ObjectMapper().findAndRegisterModules());

        for (int i = 0; i < PublicPedidoRateLimitFilter.MAX_REQUESTS_PER_WINDOW; i++) {
            MockHttpServletResponse response = perform(filter, "192.0.2.10");
            assertEquals(200, response.getStatus());
        }

        MockHttpServletResponse limitada = perform(filter, "192.0.2.10");
        assertEquals(429, limitada.getStatus());
        assertEquals(
                "Demasiadas solicitudes. Intente más tarde.",
                new ObjectMapper().readTree(limitada.getContentAsString()).get("mensaje").asText()
        );

        assertEquals(200, perform(filter, "192.0.2.11").getStatus());
    }

    @Test
    void noLimitaRutasQueNoSonConsultaNiCancelacionPublica() throws Exception {
        PublicPedidoRateLimitFilter filter = new PublicPedidoRateLimitFilter(
                new ObjectMapper().findAndRegisterModules());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/pedidos");
        request.setServletPath("/api/pedidos");
        request.setRemoteAddr("192.0.2.12");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) ->
                ((MockHttpServletResponse) servletResponse).setStatus(200);

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
    }

    @Test
    void limitaTambienLasCancelacionesPublicas() throws Exception {
        PublicPedidoRateLimitFilter filter = new PublicPedidoRateLimitFilter(
                new ObjectMapper().findAndRegisterModules());

        for (int i = 0; i < PublicPedidoRateLimitFilter.MAX_REQUESTS_PER_WINDOW; i++) {
            assertEquals(200, perform(
                    filter,
                    "192.0.2.13",
                    "PATCH",
                    "/api/pedidos/codigo/ABC123/cancelar"
            ).getStatus());
        }

        MockHttpServletResponse limitada = perform(
                filter,
                "192.0.2.13",
                "PATCH",
                "/api/pedidos/codigo/ABC123/cancelar"
        );
        assertEquals(429, limitada.getStatus());
        assertEquals("60", limitada.getHeader("Retry-After"));
    }

    private MockHttpServletResponse perform(
            PublicPedidoRateLimitFilter filter,
            String clientAddress
    ) throws Exception {
        return perform(filter, clientAddress, "GET", "/api/pedidos/codigo/ABC123");
    }

    private MockHttpServletResponse perform(
            PublicPedidoRateLimitFilter filter,
            String clientAddress,
            String method,
            String path
    ) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.setRemoteAddr(clientAddress);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) ->
                ((MockHttpServletResponse) servletResponse).setStatus(200);

        filter.doFilter(request, response, chain);
        return response;
    }
}
