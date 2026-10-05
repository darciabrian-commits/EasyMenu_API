package org.esfe.easymenu.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;


public record CrearPedidoRequestDTO(

        @NotBlank(message = "Debe indicar el nombre del cliente o mesa")
        String clienteOMesa,

        @NotEmpty(message = "El pedido debe contener al menos un producto")
        List<@Valid ItemPedidoDTO> items

) {
}