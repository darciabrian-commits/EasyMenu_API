package org.esfe.easymenu.security;

import org.esfe.easymenu.controladores.PedidoController;
import org.esfe.easymenu.modelos.RolUsuario;
import org.esfe.easymenu.modelos.Usuario;
import org.esfe.easymenu.repositorios.UsuarioRepository;
import org.esfe.easymenu.servicios.interfaces.PedidoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PedidoController.class)
@AutoConfigureMockMvc
@Import({
        SecurityConfig.class,
        JwtFilter.class,
        CustomUserDetailsService.class,
        JwtUtil.class,
        org.esfe.easymenu.excepcion.GlobalExceptionHandler.class
})
class PedidoExpirationEndpointAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private PedidoService pedidoService;

    @Test
    void endpointDeExpiracionNoExisteParaRolesDePersonalAutenticados() throws Exception {
        for (RolUsuario role : List.of(
                RolUsuario.ADMINISTRADOR,
                RolUsuario.CAJERO,
                RolUsuario.COCINA
        )) {
            String email = role.name().toLowerCase() + "@easymenu.com";
            Usuario usuario = new Usuario("Usuario", email, "hash", role);
            usuario.setActivo(true);
            when(usuarioRepository.findByCorreo(email)).thenReturn(Optional.of(usuario));
            String token = jwtUtil.generarToken(
                    User.withUsername(email).password("unused").roles(role.name()).build()
            );

            mockMvc.perform(patch("/api/pedidos/expirar")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.estado").value(405))
                    .andExpect(jsonPath("$.mensaje")
                            .value("Método HTTP no permitido para esta ruta"))
                    .andExpect(jsonPath("$.fecha").exists())
                    .andExpect(jsonPath("$.trace").doesNotExist());
        }

        verifyNoInteractions(pedidoService);
    }
}
