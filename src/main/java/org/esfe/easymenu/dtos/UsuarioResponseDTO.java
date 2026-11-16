package org.esfe.easymenu.dtos;

import org.esfe.easymenu.modelos.RolUsuario;

public record UsuarioResponseDTO(
        Long id,
        String nombre,
        String correo,
        RolUsuario rol,
        boolean activo
) {
}
