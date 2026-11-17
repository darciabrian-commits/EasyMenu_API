package org.esfe.easymenu.servicios.interfaces;

import org.esfe.easymenu.dtos.PagoDTO;
import org.esfe.easymenu.dtos.ProcesarPagoDTO;
import org.esfe.easymenu.dtos.ResumenVentasResponseDTO;

import java.time.LocalDate;
import java.util.List;

public interface IPagoService {
    PagoDTO cobrarPedido(ProcesarPagoDTO procesarPagoDTO);
    PagoDTO obtenerPorId(Long id);
    PagoDTO obtenerPorPedidoId(Long pedidoId);
    List<PagoDTO> obtenerTodos();
    PagoDTO reembolsarPago(Long pagoId, String motivo);
    ResumenVentasResponseDTO obtenerResumenVentas(LocalDate fecha);
}

