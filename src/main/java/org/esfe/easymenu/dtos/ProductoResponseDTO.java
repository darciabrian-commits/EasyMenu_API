package org.esfe.easymenu.dtos;

import org.esfe.easymenu.modelos.CategoriaProducto;

import java.math.BigDecimal;

public record ProductoResponseDTO(

        Long id,
        String nombre,
        String descripcion,
        BigDecimal precio,
        CategoriaProducto categoria,
        boolean disponible,
        boolean activo

) {
}