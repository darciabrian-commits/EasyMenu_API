package org.esfe.easymenu.dtos;

import java.time.LocalDateTime;

// Formato estándar de error que devuelve la API cuando algo sale mal
public record ErrorResponseDTO(
        int estado,
        String mensaje,
        LocalDateTime fecha
) {
}