package org.esfe.easymenu.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.esfe.easymenu.modelos.RolUsuario;
import org.esfe.easymenu.validaciones.ClaveBcryptValida;

public record CrearUsuarioRequestDTO(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo debe tener un formato válido")
        @Size(max = 150, message = "El correo no puede superar los 150 caracteres")
        String correo,

        @NotBlank(message = "La contraseña es obligatoria")
        @ClaveBcryptValida
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        String clave,

        @NotNull(message = "El rol es obligatorio")
        RolUsuario rol
) {
}
