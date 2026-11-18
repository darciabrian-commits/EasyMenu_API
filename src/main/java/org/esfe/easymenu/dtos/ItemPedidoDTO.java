package org.esfe.easymenu.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;


public record ItemPedidoDTO(

        @NotNull(message = "El producto es obligatorio")
        @Positive(message = "El producto debe ser válido")
        Long productoId,

        @NotNull(message = "La cantidad es obligatoria")
        @Positive(message = "La cantidad debe ser mayor que cero")
        Integer cantidad,

        @Size(max = 255, message = "Las notas no pueden superar los 255 caracteres")
        String notas

) {
}