package org.esfe.easymenu.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;


public record ItemPedidoDTO(

        @NotNull(message = "El producto es obligatorio")
        Long productoId,

        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad debe ser mayor que cero")
        Integer cantidad,

        String notas

) {
}