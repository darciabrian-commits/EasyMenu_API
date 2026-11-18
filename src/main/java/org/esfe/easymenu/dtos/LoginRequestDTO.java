package org.esfe.easymenu.dtos;

import jakarta.validation.constraints.NotBlank;
import org.esfe.easymenu.validaciones.ClaveBcryptValida;

public record LoginRequestDTO(
        @NotBlank(message = "El correo es obligatorio") String correo,
        @NotBlank(message = "La clave es obligatoria")
        @ClaveBcryptValida
        String clave
) {}