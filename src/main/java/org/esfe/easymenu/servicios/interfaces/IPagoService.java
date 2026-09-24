package org.esfe.easymenu.servicios.interfaces;

import org.esfe.easymenu.dtos.PagoDTO;
import org.esfe.easymenu.dtos.ProcesarPagoDTO;

import java.util.List;

public interface IPagoService {
    PagoDTO cobrarPedido(ProcesarPagoDTO procesarPagoDTO);
    PagoDTO obtenerPorId(Long id);
    PagoDTO obtenerPorPedidoId(Long pedidoId);
    List<PagoDTO> obtenerTodos();
    PagoDTO reembolsarPago(Long pagoId, String motivo);
}

