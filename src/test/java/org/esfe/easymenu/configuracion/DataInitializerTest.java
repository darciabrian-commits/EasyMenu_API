package org.esfe.easymenu.configuracion;

import org.esfe.easymenu.modelos.RolUsuario;
import org.esfe.easymenu.modelos.Usuario;
import org.esfe.easymenu.repositorios.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DataInitializerTest {

    private final UsuarioRepository repository = mock(UsuarioRepository.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void sinConfiguracionNoCreaAdministrador() {
        initializer("", "").run();

        verifyNoInteractions(repository);
    }

    @Test
    void configuracionParcialFallaExplicitamente() {
        DataInitializer initializer = initializer("admin@easymenu.com", "");

        assertThrows(IllegalStateException.class, initializer::run);
        verifyNoInteractions(repository);
    }

    @Test
    void creaAdministradorConfiguradoConBcryptCuandoLaBaseEstaVacia() {
        when(repository.count()).thenReturn(0L);

        initializer(" ADMIN@EasyMenu.com ", "secreto-configurado-123").run();

        var captor = org.mockito.ArgumentCaptor.forClass(Usuario.class);
        verify(repository).save(captor.capture());
        Usuario guardado = captor.getValue();
        assertEquals("admin@easymenu.com", guardado.getCorreo());
        assertEquals(RolUsuario.ADMINISTRADOR, guardado.getRol());
        assertTrue(passwordEncoder.matches("secreto-configurado-123", guardado.getClave()));
        assertNotEquals("secreto-configurado-123", guardado.getClave());
    }

    @Test
    void noModificaUsuariosExistentes() {
        when(repository.count()).thenReturn(1L);

        initializer("admin@easymenu.com", "secreto-configurado-123").run();

        verify(repository, never()).save(any());
    }

    @Test
    void rechazaClaveInicialQueSupera72BytesUtf8() {
        when(repository.count()).thenReturn(0L);

        assertThrows(
                IllegalStateException.class,
                () -> initializer("admin@easymenu.com", "a".repeat(71) + "é").run()
        );
        verify(repository, never()).save(any());
    }

    private DataInitializer initializer(String correo, String clave) {
        return new DataInitializer(repository, passwordEncoder, correo, clave);
    }
}
