package org.esfe.easymenu.dtos;

import lombok.Data;
import org.esfe.easymenu.modelos.EstadoPedido;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TicketCocinaDTO {
    private Long pedidoId;
    private String clienteOMesa;
    private EstadoPedido estado;
    private LocalDateTime fechaHora;
    private long minutosTranscurridos;
    private List<ItemCocinaDTO> items;

    @Data
    public static class ItemCocinaDTO {
        private String productoNombre;
        private Integer cantidad;
        private String notas;
    }
}