package org.esfe.easymenu.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.esfe.easymenu.modelos.EstadoPedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PedidoResponseDTO {
    private Long id;

    @NotBlank(message = "Debe indicar el nombre del cliente o número de mesa")
    private String clienteOMesa;

    private EstadoPedido estado;
    private BigDecimal total;
    private LocalDateTime fechaHora;

    @NotEmpty(message = "El pedido debe contener al menos un producto")
    @Valid
    private List<DetallePedidoResponseDTO> detalles;
}
