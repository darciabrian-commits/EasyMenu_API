package org.esfe.easymenu.security;

import org.esfe.easymenu.controladores.ProductoController;
import org.esfe.easymenu.dtos.ProductoResponseDTO;
import org.esfe.easymenu.modelos.RolUsuario;
import org.esfe.easymenu.modelos.Usuario;
import org.esfe.easymenu.repositorios.UsuarioRepository;
import org.esfe.easymenu.servicios.interfaces.ProductoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductoController.class)
@AutoConfigureMockMvc
@Import({
        SecurityConfig.class,
        JwtFilter.class,
        CustomUserDetailsService.class,
        JwtUtil.class,
        org.esfe.easymenu.excepcion.GlobalExceptionHandler.class
})
class ProductoEndpointAuthorizationTest {

    private static final String REQUEST_PRODUCTO = """
            {"nombre":"Cafe","descripcion":"Cafe americano","precio":2.50,"categoria":"Bebidas"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private ProductoService productoService;

    @Test
    void consultaDeProductosEsPublica() throws Exception {
        ProductoResponseDTO producto = producto();
        when(productoService.obtenerTodos()).thenReturn(List.of(producto));
        when(productoService.obtenerDisponibles()).thenReturn(List.of(producto));

        mockMvc.perform(get("/api/productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Cafe"));
        mockMvc.perform(get("/api/productos/disponibles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].disponible").value(true));

        verify(productoService).obtenerTodos();
        verify(productoService).obtenerDisponibles();
    }

    @Test
    void soloAdministradorPuedeCrearActualizarCambiarDisponibilidadYEliminar() throws Exception {
        for (RolUsuario role : List.of(RolUsuario.CAJERO, RolUsuario.COCINA)) {
            String token = tokenFor(role);
            assertForbiddenMutations(token);
        }
        assertForbiddenMutations(null);
        verifyNoInteractions(productoService);

        String adminToken = tokenFor(RolUsuario.ADMINISTRADOR);
        when(productoService.crear(any())).thenReturn(producto());
        when(productoService.actualizar(eq(1L), any())).thenReturn(producto());
        when(productoService.cambiarEstadoDisponibilidad(1L, false)).thenReturn(producto());

        mockMvc.perform(post("/api/productos")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_PRODUCTO))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/productos/1")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_PRODUCTO))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/productos/1/disponibilidad")
                        .header("Authorization", bearer(adminToken))
                        .param("disponible", "false"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/productos/1")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());

        verify(productoService).crear(any());
        verify(productoService).actualizar(eq(1L), any());
        verify(productoService).cambiarEstadoDisponibilidad(1L, false);
        verify(productoService).eliminar(1L);
    }

    private void assertForbiddenMutations(String token) throws Exception {
        var create = post("/api/productos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_PRODUCTO);
        var update = put("/api/productos/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_PRODUCTO);
        var availability = patch("/api/productos/1/disponibilidad")
                .param("disponible", "false");
        var remove = delete("/api/productos/1");

        for (var request : List.of(create, update, availability, remove)) {
            if (token != null) {
                request.header("Authorization", bearer(token));
            }
            mockMvc.perform(request).andExpect(status().isForbidden());
        }
    }

    private String tokenFor(RolUsuario role) {
        String email = role.name().toLowerCase() + "@easymenu.com";
        Usuario usuario = new Usuario("Usuario", email, "hash", role);
        usuario.setActivo(true);
        when(usuarioRepository.findByCorreo(email)).thenReturn(Optional.of(usuario));
        return jwtUtil.generarToken(
                User.withUsername(email).password("unused").roles(role.name()).build()
        );
    }

    private static ProductoResponseDTO producto() {
        return new ProductoResponseDTO(
                1L,
                "Cafe",
                "Cafe americano",
                new BigDecimal("2.50"),
                "Bebidas",
                true,
                true
        );
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
