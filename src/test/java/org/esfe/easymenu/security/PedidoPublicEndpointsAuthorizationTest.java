package org.esfe.easymenu.security;

import org.esfe.easymenu.controladores.PedidoController;
import org.esfe.easymenu.dtos.CrearPedidoRequestDTO;
import org.esfe.easymenu.dtos.PedidoResponseDTO;
import org.esfe.easymenu.excepcion.ConflictoIdempotenciaException;
import org.esfe.easymenu.excepcion.RecursoNoEncontradoException;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class PedidoPublicEndpointsAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private PedidoService pedidoService;

    @Test
    void consultaYCancelacionPorCodigoSiguenSiendoPublicas() throws Exception {
        PedidoResponseDTO pedido = new PedidoResponseDTO();
        pedido.setCodigoCorto("ABC123");
        when(pedidoService.obtenerPorCodigo("ABC123")).thenReturn(pedido);
        when(pedidoService.crearPedido(
                any(CrearPedidoRequestDTO.class), isNull())).thenReturn(pedido);

        mockMvc.perform(get("/api/pedidos/codigo/ABC123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoCorto").value("ABC123"));

        mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clienteOMesa":"Mesa 4","items":[{"productoId":7,"cantidad":1}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigoCorto").value("ABC123"));

        mockMvc.perform(patch("/api/pedidos/codigo/ABC123/cancelar"))
                .andExpect(status().isNoContent());

        verify(pedidoService).obtenerPorCodigo("ABC123");
        verify(pedidoService).crearPedido(any(CrearPedidoRequestDTO.class), isNull());
        verify(pedidoService).cancelarPedidoPorCodigo("ABC123");
    }

    @Test
    void postPedidoAceptaLaClaveOpcionalYTraduceConflictosA409() throws Exception {
        PedidoResponseDTO pedido = new PedidoResponseDTO();
        pedido.setCodigoCorto("ABCDEFGHJK");
        String idempotencyKey = UUID.randomUUID().toString();
        when(pedidoService.crearPedido(
                any(CrearPedidoRequestDTO.class), eq(idempotencyKey))).thenReturn(pedido);

        mockMvc.perform(post("/api/pedidos")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clienteOMesa":"Mesa 4","items":[{"productoId":7,"cantidad":1}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigoCorto").value("ABCDEFGHJK"));
        verify(pedidoService).crearPedido(any(CrearPedidoRequestDTO.class), eq(idempotencyKey));

        String conflictKey = UUID.randomUUID().toString();
        when(pedidoService.crearPedido(
                any(CrearPedidoRequestDTO.class), eq(conflictKey)))
                .thenThrow(new ConflictoIdempotenciaException("La clave ya tiene otro contenido"));

        mockMvc.perform(post("/api/pedidos")
                        .header("Idempotency-Key", conflictKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clienteOMesa":"Mesa 5","items":[{"productoId":7,"cantidad":1}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.estado").value(409));
    }

    @Test
    void erroresPublicosNoRevelanElCodigoNiLaExistenciaDelPedido() throws Exception {
        when(pedidoService.obtenerPorCodigo("ABC123"))
                .thenThrow(new RecursoNoEncontradoException("Pedido no disponible"));
        doThrow(new RecursoNoEncontradoException("Pedido no disponible"))
                .when(pedidoService).cancelarPedidoPorCodigo("ABC123");

        mockMvc.perform(get("/api/pedidos/codigo/ABC123"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Pedido no disponible"));
        mockMvc.perform(patch("/api/pedidos/codigo/ABC123/cancelar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Pedido no disponible"));
    }

    @Test
    void rutasInternasDePedidosRechazanSolicitudesSinAutenticacion() throws Exception {
        mockMvc.perform(get("/api/pedidos"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/pedidos/1"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/pedidos/estado/PENDIENTE"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/pedidos/1/estado").param("estado", "RECIBIDO"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/pedidos/1/cancelar"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(pedidoService);
    }

    @Test
    void personalAutorizadoConservaAccesoAOperacionesInternasExistentes() throws Exception {
        when(pedidoService.obtenerTodos()).thenReturn(java.util.List.of());

        for (RolUsuario role : java.util.List.of(
                RolUsuario.ADMINISTRADOR,
                RolUsuario.CAJERO,
                RolUsuario.COCINA
        )) {
            String email = role.name().toLowerCase() + "@easymenu.com";
            Usuario usuario = new Usuario(role.name(), email, "hash", role);
            usuario.setActivo(true);
            when(usuarioRepository.findByCorreo(email)).thenReturn(Optional.of(usuario));
            String token = jwtUtil.generarToken(
                    User.withUsername(email).password("unused").roles(role.name()).build()
            );

            mockMvc.perform(get("/api/pedidos")
                            .header("Authorization", "Bearer " + token)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        verify(pedidoService, times(3)).obtenerTodos();
    }
}
