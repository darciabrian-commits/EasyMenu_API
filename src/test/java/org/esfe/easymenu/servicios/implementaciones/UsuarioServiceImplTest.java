package org.esfe.easymenu.servicios.implementaciones;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.esfe.easymenu.dtos.CrearUsuarioRequestDTO;
import org.esfe.easymenu.dtos.LoginRequestDTO;
import org.esfe.easymenu.dtos.UsuarioResponseDTO;
import org.esfe.easymenu.excepcion.CredencialesInvalidasException;
import org.esfe.easymenu.excepcion.CorreoDuplicadoException;
import org.esfe.easymenu.excepcion.ReglaNegocioException;
import org.esfe.easymenu.excepcion.RecursoNoEncontradoException;
import org.esfe.easymenu.modelos.RolUsuario;
import org.esfe.easymenu.modelos.Usuario;
import org.esfe.easymenu.repositorios.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UsuarioServiceImplTest {

    private UsuarioRepository usuarioRepository;
    private BCryptPasswordEncoder passwordEncoder;
    private UsuarioServiceImpl usuarioService;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        usuarioService = new UsuarioServiceImpl(usuarioRepository, passwordEncoder);
    }

    @ParameterizedTest
    @EnumSource(value = RolUsuario.class, names = {"CAJERO", "COCINA"})
    void crearCajeroYCocinaGuardaConBcryptYDevuelveDatosSeguros(RolUsuario rol) throws Exception {
        when(usuarioRepository.existsByCorreoIgnoreCase("persona@easymenu.com")).thenReturn(false);
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario usuario = invocation.getArgument(0);
            usuario.setId(10L);
            return usuario;
        });

        UsuarioResponseDTO response = usuarioService.crear(request(rol));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).saveAndFlush(captor.capture());
        Usuario guardado = captor.getValue();
        assertNotNull(guardado);
        assertTrue(passwordEncoder.matches("clave-segura-123", guardado.getClave()));
        assertNotEquals("clave-segura-123", guardado.getClave());
        assertEquals(rol, response.rol());
        assertTrue(response.activo());
        String json = new ObjectMapper().writeValueAsString(response);
        assertFalse(json.contains("clave"));
        assertFalse(json.contains("password"));
        assertFalse(json.contains(guardado.getClave()));
    }

    @Test
    void rechazaCrearAdministrador() {
        assertThrows(ReglaNegocioException.class,
                () -> usuarioService.crear(request(RolUsuario.ADMINISTRADOR)));
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void rechazaCorreoDuplicado() {
        when(usuarioRepository.existsByCorreoIgnoreCase("persona@easymenu.com")).thenReturn(true);

        assertThrows(CorreoDuplicadoException.class, () -> usuarioService.crear(request(RolUsuario.CAJERO)));
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void colisionAlGuardarSeTraduceACorreoDuplicado() {
        when(usuarioRepository.existsByCorreoIgnoreCase("persona@easymenu.com")).thenReturn(false);
        when(usuarioRepository.saveAndFlush(any(Usuario.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("detalle interno"));

        assertThrows(CorreoDuplicadoException.class, () -> usuarioService.crear(request(RolUsuario.CAJERO)));
    }

    @Test
    void rechazaContrasenaQueSuperaLos72BytesAntesDeCodificar() {
        String claveLargaUtf8 = "a".repeat(71) + "é";
        CrearUsuarioRequestDTO request = new CrearUsuarioRequestDTO(
                "Persona", "persona@easymenu.com", claveLargaUtf8, RolUsuario.CAJERO
        );
        BCryptPasswordEncoder encoder = spy(new BCryptPasswordEncoder());
        UsuarioServiceImpl service = new UsuarioServiceImpl(usuarioRepository, encoder);

        assertThrows(ReglaNegocioException.class, () -> service.crear(request));
        verifyNoInteractions(usuarioRepository);
        verify(encoder, never()).encode(any(CharSequence.class));
    }

    @Test
    void listaYConsultaUsuariosIncluyendoElEstado() {
        Usuario activo = usuario(1L, RolUsuario.CAJERO, true);
        Usuario inactivo = usuario(2L, RolUsuario.COCINA, false);
        when(usuarioRepository.findAll()).thenReturn(List.of(activo, inactivo));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(inactivo));

        List<UsuarioResponseDTO> usuarios = usuarioService.listar();

        assertEquals(2, usuarios.size());
        assertTrue(usuarios.get(0).activo());
        assertFalse(usuarios.get(1).activo());
        assertEquals(2L, usuarioService.obtenerPorId(2L).id());
        assertFalse(usuarioService.obtenerPorId(2L).activo());
    }

    @Test
    void usuarioInexistenteProduceNoEncontrado() {
        when(usuarioRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> usuarioService.obtenerPorId(404L));
    }

    @Test
    void desactivacionEsLogicaYNoPermiteDesactivarAdministrador() {
        Usuario cajero = usuario(1L, RolUsuario.CAJERO, true);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(cajero));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UsuarioResponseDTO response = usuarioService.desactivar(1L);

        assertFalse(response.activo());
        assertFalse(cajero.isActivo());

        Usuario administrador = usuario(2L, RolUsuario.ADMINISTRADOR, true);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(administrador));
        assertThrows(ReglaNegocioException.class, () -> usuarioService.desactivar(2L));
        verify(usuarioRepository, times(1)).save(cajero);
    }

    @Test
    void loginRechazaUsuariosInactivos() {
        Usuario cajero = usuario(1L, RolUsuario.CAJERO, false);
        cajero.setClave(passwordEncoder.encode("clave-segura-123"));
        when(usuarioRepository.findByCorreo(cajero.getCorreo())).thenReturn(Optional.of(cajero));

        assertThrows(CredencialesInvalidasException.class,
                () -> usuarioService.login(new LoginRequestDTO(cajero.getCorreo(), "clave-segura-123")));
    }

    private CrearUsuarioRequestDTO request(RolUsuario rol) {
        return new CrearUsuarioRequestDTO("Persona", " Persona@EasyMenu.com ", "clave-segura-123", rol);
    }

    private Usuario usuario(Long id, RolUsuario rol, boolean activo) {
        Usuario usuario = new Usuario("Persona", "persona" + id + "@easymenu.com", "hash", rol);
        usuario.setId(id);
        usuario.setActivo(activo);
        return usuario;
    }
}
