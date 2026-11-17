package org.esfe.easymenu.security;

import org.esfe.easymenu.controladores.PagoController;
import org.esfe.easymenu.dtos.ResumenVentasResponseDTO;
import org.esfe.easymenu.modelos.RolUsuario;
import org.esfe.easymenu.modelos.Usuario;
import org.esfe.easymenu.repositorios.UsuarioRepository;
import org.esfe.easymenu.servicios.interfaces.IPagoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PagoController.class)
@AutoConfigureMockMvc
@Import({
        SecurityConfig.class,
        JwtFilter.class,
        CustomUserDetailsService.class,
        JwtUtil.class,
        org.esfe.easymenu.excepcion.GlobalExceptionHandler.class
})
class ResumenVentasEndpointAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private IPagoService pagoService;

    @Test
    void soloAdministradorPuedeConsultarElResumen() throws Exception {
        LocalDate fecha = LocalDate.of(2026, 11, 16);
        when(pagoService.obtenerResumenVentas(fecha)).thenReturn(new ResumenVentasResponseDTO(
                fecha, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO));

        mockMvc.perform(get("/api/pagos/resumen")
                        .param("fecha", "2026-11-16")
                        .header("Authorization", bearer(tokenFor(RolUsuario.ADMINISTRADOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value("2026-11-16"))
                .andExpect(jsonPath("$.totalNetoVentas").value(0));

        verify(pagoService).obtenerResumenVentas(fecha);
    }

    @Test
    void deniegaCajeroCocinaYUsuariosNoAutenticados() throws Exception {
        for (RolUsuario role : List.of(RolUsuario.CAJERO, RolUsuario.COCINA)) {
            mockMvc.perform(get("/api/pagos/resumen")
                            .param("fecha", "2026-11-16")
                            .header("Authorization", bearer(tokenFor(role))))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(get("/api/pagos/resumen").param("fecha", "2026-11-16"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(pagoService);
    }

    @Test
    void lasRutasDePagosExistentesSiguenPermitidasParaAdministradorYCajero() throws Exception {
        when(pagoService.obtenerTodos()).thenReturn(List.of());

        for (RolUsuario role : List.of(RolUsuario.ADMINISTRADOR, RolUsuario.CAJERO)) {
            mockMvc.perform(get("/api/pagos")
                            .header("Authorization", bearer(tokenFor(role))))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/pagos")
                        .header("Authorization", bearer(tokenFor(RolUsuario.COCINA))))
                .andExpect(status().isForbidden());

        verify(pagoService, org.mockito.Mockito.times(2)).obtenerTodos();
    }

    @Test
    void fechaDebeTenerFormatoIso() throws Exception {
        mockMvc.perform(get("/api/pagos/resumen")
                        .param("fecha", "16-11-2026")
                        .header("Authorization", bearer(tokenFor(RolUsuario.ADMINISTRADOR))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(pagoService);
    }

    private String tokenFor(RolUsuario role) {
        String email = role.name().toLowerCase() + "@easymenu.com";
        Usuario usuario = new Usuario("Usuario", email, "hash", role);
        usuario.setActivo(true);
        when(usuarioRepository.findByCorreo(email)).thenReturn(Optional.of(usuario));
        return jwtUtil.generarToken(User.withUsername(email).password("unused").roles(role.name()).build());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
