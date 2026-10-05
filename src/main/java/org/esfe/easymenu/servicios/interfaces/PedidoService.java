package org.esfe.easymenu.servicios.interfaces;

import org.esfe.easymenu.dtos.CrearPedidoRequestDTO;
import org.esfe.easymenu.dtos.PedidoResponseDTO;
import org.esfe.easymenu.modelos.EstadoPedido;

import java.util.List;

public interface PedidoService {

    PedidoResponseDTO crearPedido(CrearPedidoRequestDTO dto);

    List<PedidoResponseDTO> obtenerTodos();

    PedidoResponseDTO obtenerPorId(Long id);

    PedidoResponseDTO obtenerPorCodigo(String codigoCorto);

    List<PedidoResponseDTO> obtenerPorEstado(EstadoPedido estado);

    PedidoResponseDTO cambiarEstado(Long id, EstadoPedido nuevoEstado);

    void cancelarPedido(Long id);

    void cancelarPedidoPorCodigo(String codigoCorto);

    void expirarPedidosPendientes(int minutosLimite);
}