package org.esfe.easymenu.security;

import org.esfe.easymenu.modelos.RolUsuario;
import org.esfe.easymenu.modelos.Usuario;
import org.esfe.easymenu.controladores.UsuarioController;
import org.esfe.easymenu.repositorios.UsuarioRepository;
import org.esfe.easymenu.servicios.implementaciones.UsuarioServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UsuarioController.class)
@AutoConfigureMockMvc
@Import({
        SecurityConfig.class,
        JwtFilter.class,
        CustomUserDetailsService.class,
        JwtUtil.class,
        UsuarioServiceImpl.class,
        org.esfe.easymenu.excepcion.GlobalExceptionHandler.class
})
class UsuarioAuthorizationTest {

    private static final String REQUEST_USUARIO =
            """
                    {"nombre":"Nuevo usuario","correo":"nuevo@easymenu.com","clave":"clave-segura-123","rol":"CAJERO"}
                    """;
    private static final String REQUEST_COCINA =
            """
                    {"nombre":"Nuevo cocinero","correo":"cocina-nueva@easymenu.com","clave":"clave-segura-123","rol":"COCINA"}
                    """;
    private static final String REQUEST_ADMIN =
            """
                    {"nombre":"Admin nuevo","correo":"admin-nuevo@easymenu.com","clave":"clave-segura-123","rol":"ADMINISTRADOR"}
                    """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${jwt.secret}")
    private String testJwtSecret;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @Test
    void sinTokenApiUsuariosSeRechaza() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorPuedeUsarLosCuatroEndpoints() throws Exception {
        String token = tokenFor(RolUsuario.ADMINISTRADOR, true);
        when(usuarioRepository.findAll()).thenReturn(List.of());
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario(1L, RolUsuario.CAJERO, true)));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(usuario(2L, RolUsuario.COCINA, true)));
        when(usuarioRepository.existsByCorreoIgnoreCase("nuevo@easymenu.com")).thenReturn(false);
        when(usuarioRepository.existsByCorreoIgnoreCase("cocina-nueva@easymenu.com")).thenReturn(false);
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(10L);
            }
            return entity;
        });
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(get("/api/usuarios").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/usuarios/1").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_USUARIO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clave").doesNotExist());
        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_COCINA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("COCINA"));
        mockMvc.perform(patch("/api/usuarios/2/desactivar")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository, atLeastOnce()).saveAndFlush(captor.capture());
        Usuario creado = captor.getAllValues().stream()
                .filter(entity -> entity.getCorreo().equals("nuevo@easymenu.com"))
                .findFirst()
                .orElseThrow();
        assertTrue(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .matches("clave-segura-123", creado.getClave()));
    }

    @Test
    void cajeroYCocinaNoPuedenUsarNingunoDeLosCuatroEndpoints() throws Exception {
        for (RolUsuario role : List.of(RolUsuario.CAJERO, RolUsuario.COCINA)) {
            String token = tokenFor(role, true);
            mockMvc.perform(get("/api/usuarios").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/usuarios/1").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/usuarios")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_USUARIO))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch("/api/usuarios/1/desactivar")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
        }

        verify(usuarioRepository, never()).findAll();
        verify(usuarioRepository, never()).findById(any());
        verify(usuarioRepository, never()).existsByCorreoIgnoreCase(any());
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void jwtDeUsuarioDesactivadoSeRechazaPorHttp() throws Exception {
        String token = tokenFor(RolUsuario.CAJERO, false);

        mockMvc.perform(get("/api/usuarios").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void jwtDeUsuarioInexistenteSeRechazaSinError500() throws Exception {
        String email = "eliminado@easymenu.com";
        when(usuarioRepository.findByCorreo(email)).thenReturn(Optional.empty());
        String token = jwtUtil.generarToken(
                User.withUsername(email).password("unused").roles("CAJERO").build()
        );

        mockMvc.perform(get("/api/usuarios").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void jwtInvalidoSeRechazaSinError500() throws Exception {
        mockMvc.perform(get("/api/usuarios").header("Authorization", bearer("no-es-un-jwt")))
                .andExpect(status().isForbidden());
    }

    @Test
    void jwtExpiradoSinExpiracionManipuladoYMalformadoSeRechazanPorHttp() throws Exception {
        String email = "admin@easymenu.com";
        when(usuarioRepository.findByCorreo(email))
                .thenReturn(Optional.of(usuario(1L, RolUsuario.ADMINISTRADOR, true, email)));
        String tokenExpirado = signedToken(email, new Date(System.currentTimeMillis() - 1000), true);
        String tokenSinExpiracion = signedToken(email, null, false);
        String tokenValido = jwtUtil.generarToken(
                User.withUsername(email).password("unused").roles("ADMINISTRADOR").build()
        );
        String tokenManipulado = alterarUltimoCaracter(tokenValido);

        for (String token : List.of(
                tokenExpirado,
                tokenSinExpiracion,
                tokenManipulado,
                "token-malformado"
        )) {
            mockMvc.perform(get("/api/usuarios").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void administradorNoPuedeCrearNiDesactivarOtroAdministrador() throws Exception {
        String token = tokenFor(RolUsuario.ADMINISTRADOR, true);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(usuario(2L, RolUsuario.ADMINISTRADOR, true)));

        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_ADMIN))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/usuarios/2/desactivar")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());

        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void correosDuplicadosDevuelven409SinDetallesDeBaseDeDatos() throws Exception {
        String token = tokenFor(RolUsuario.ADMINISTRADOR, true);
        when(usuarioRepository.existsByCorreoIgnoreCase("nuevo@easymenu.com")).thenReturn(true);

        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_USUARIO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Ya existe un usuario con ese correo"));

        when(usuarioRepository.existsByCorreoIgnoreCase("nuevo@easymenu.com")).thenReturn(false);
        when(usuarioRepository.saveAndFlush(any(Usuario.class)))
                .thenThrow(new DataIntegrityViolationException("detalle-secreto-de-base-de-datos"));

        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_USUARIO))
                .andExpect(status().isConflict())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("detalle-secreto-de-base-de-datos")
                )));
    }

    @Test
    void creacionRechazaContrasenaQueSupera72BytesAntesDeGuardar() throws Exception {
        String token = tokenFor(RolUsuario.ADMINISTRADOR, true);
        String claveLargaUtf8 = "a".repeat(71) + "é";
        String request = """
                {"nombre":"Nuevo usuario","correo":"largo@easymenu.com","clave":"%s","rol":"CAJERO"}
                """.formatted(claveLargaUtf8);

        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());

        verify(usuarioRepository, never()).existsByCorreoIgnoreCase("largo@easymenu.com");
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    private String tokenFor(RolUsuario role, boolean active) {
        String email = role.name().toLowerCase() + "@easymenu.com";
        when(usuarioRepository.findByCorreo(email))
                .thenReturn(Optional.of(usuario(1L, role, active, email)));
        return jwtUtil.generarToken(User.withUsername(email).password("unused").roles(role.name()).build());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private String signedToken(String subject, Date expiration, boolean includeExpiration) {
        var builder = Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(new Date());
        if (includeExpiration) {
            builder.setExpiration(expiration);
        }
        return builder.signWith(
                Keys.hmacShaKeyFor(Base64.getDecoder().decode(testJwtSecret)),
                SignatureAlgorithm.HS256
        ).compact();
    }

    private static String alterarUltimoCaracter(String token) {
        int signatureStart = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
        return token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);
    }

    private static Usuario usuario(Long id, RolUsuario role, boolean active) {
        String email = role.name().toLowerCase() + id + "@easymenu.com";
        return usuario(id, role, active, email);
    }

    private static Usuario usuario(Long id, RolUsuario role, boolean active, String email) {
        Usuario usuario = new Usuario("Usuario", email, "hash", role);
        usuario.setId(id);
        usuario.setActivo(active);
        return usuario;
    }
}
