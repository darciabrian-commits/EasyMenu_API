package org.esfe.easymenu.servicios.interfaces;

import java.util.List;
import org.esfe.easymenu.dtos.TicketCocinaDTO;

public interface ICocinaService {

    List<TicketCocinaDTO> obtenerPedidosParaCocina();

    TicketCocinaDTO iniciarPreparacion(Long pedidoId);

    TicketCocinaDTO marcarComoListo(Long pedidoId);

}