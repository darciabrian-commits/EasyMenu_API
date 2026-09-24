package org.esfe.easymenu.servicios.interfaces;

import org.esfe.easymenu.dtos.CrearPedidoRequestDTO;
import org.esfe.easymenu.dtos.PedidoResponseDTO;
import org.esfe.easymenu.modelos.EstadoPedido;

import java.util.List;

public interface PedidoService {
    PedidoResponseDTO crearPedido(PedidoResponseDTO dto);
    List<PedidoResponseDTO> obtenerTodos();
    PedidoResponseDTO obtenerPorId(Long id);
    List<PedidoResponseDTO> obtenerPorEstado(EstadoPedido estado);
    PedidoResponseDTO cambiarEstado(Long id, EstadoPedido nuevoEstado);
    void cancelarPedido(Long id);
    void expirarPedidosPendientes(int minutosLimite);
}
