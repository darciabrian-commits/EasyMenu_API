package org.esfe.easymenu.security;

import org.esfe.easymenu.modelos.RolUsuario;
import org.esfe.easymenu.modelos.Usuario;
import org.esfe.easymenu.repositorios.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class CustomUserDetailsServiceTest {

    @Test
    void usuarioInactivoSeMarcaComoDeshabilitado() {
        UsuarioRepository repository = mock(UsuarioRepository.class);
        Usuario usuario = new Usuario("Cajero", "cajero@easymenu.com", "hash", RolUsuario.CAJERO);
        usuario.setActivo(false);
        when(repository.findByCorreo(usuario.getCorreo())).thenReturn(Optional.of(usuario));
        CustomUserDetailsService service = new CustomUserDetailsService(repository);

        UserDetails details = service.loadUserByUsername(usuario.getCorreo());

        assertFalse(details.isEnabled());
    }
}
