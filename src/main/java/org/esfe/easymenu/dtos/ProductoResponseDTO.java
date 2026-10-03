package org.esfe.easymenu.dtos;

import java.math.BigDecimal;

public record ProductoResponseDTO(

        Long id,
        String nombre,
        String descripcion,
        BigDecimal precio,
        String categoria,
        Boolean disponible,
        Boolean activo

) {
}