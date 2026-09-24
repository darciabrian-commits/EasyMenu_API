package org.esfe.easymenu.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.esfe.easymenu.modelos.MetodoPago;

import java.math.BigDecimal;

@Data
public class ProcesarPagoDTO {

    @NotNull(message = "El ID del pedido es obligatorio")
    private Long pedidoId;

    @NotNull(message = "Debe especificar el método de pago")
    private MetodoPago metodoPago;

    private BigDecimal montoRecibido; // Obligatorio cuando el metodoPago es EFECTIVO
}

