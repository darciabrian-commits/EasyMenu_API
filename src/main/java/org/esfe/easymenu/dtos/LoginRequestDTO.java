package org.esfe.easymenu.dtos;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(
        @NotBlank(message = "El correo es obligatorio") String correo,
        @NotBlank(message = "La clave es obligatoria") String clave
) {}