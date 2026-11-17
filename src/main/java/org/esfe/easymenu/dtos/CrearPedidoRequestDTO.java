package org.esfe.easymenu.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;


public record CrearPedidoRequestDTO(

        @NotBlank(message = "Debe indicar el nombre del cliente o mesa")
        @Size(max = 100, message = "El nombre del cliente o mesa no puede superar los 100 caracteres")
        String clienteOMesa,

        @NotEmpty(message = "El pedido debe contener al menos un producto")
        @Size(max = 100, message = "El pedido no puede contener más de 100 artículos")
        List<@Valid ItemPedidoDTO> items

) {
}