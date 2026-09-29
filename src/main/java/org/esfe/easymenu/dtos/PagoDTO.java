package org.esfe.easymenu.dtos;

import lombok.Data;
import org.esfe.easymenu.modelos.EstadoPago;
import org.esfe.easymenu.modelos.MetodoPago;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PagoDTO {
    private Long id;
    private Long pedidoId;
    private String clienteOMesa;
    private BigDecimal montoTotal;
    private BigDecimal montoRecibido;
    private BigDecimal cambio;
    private MetodoPago metodoPago;
    private EstadoPago estado;
    private LocalDateTime fechaHora;
    private String motivoReembolso;
}

