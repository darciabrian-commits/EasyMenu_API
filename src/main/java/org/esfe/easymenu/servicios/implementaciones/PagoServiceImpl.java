package org.esfe.easymenu.servicios.implementaciones;

import org.esfe.easymenu.dtos.PagoDTO;
import org.esfe.easymenu.dtos.ProcesarPagoDTO;
import org.esfe.easymenu.excepcion.RecursoNoEncontradoException;
import org.esfe.easymenu.excepcion.ReglaNegocioException;
import org.esfe.easymenu.modelos.*;
import org.esfe.easymenu.repositorios.PagoRepository;
import org.esfe.easymenu.repositorios.PedidoRepository;
import org.esfe.easymenu.servicios.interfaces.IPagoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PagoServiceImpl implements IPagoService {

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Override
    @Transactional // HU-5: Garantiza integridad transaccional entre Pedido y Pago
    public PagoDTO cobrarPedido(ProcesarPagoDTO dto) {
        // HU-5: Validar existencia del pedido
        Pedido pedido = pedidoRepository.findById(dto.getPedidoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido no encontrado con ID: " + dto.getPedidoId()));

        // Validar estado del pedido
        if (pedido.getEstado() == EstadoPedido.CANCELADO || pedido.getEstado() == EstadoPedido.EXPIRADO) {
            throw new ReglaNegocioException("No se puede cobrar un pedido en estado " + pedido.getEstado());
        }

        // HU-5: Evitar cobro duplicado
        if (pagoRepository.existsByPedidoId(dto.getPedidoId())) {
            throw new ReglaNegocioException("El pedido ID: " + dto.getPedidoId() + " ya ha sido cobrado previamente");
        }

        Pago pago = new Pago();
        pago.setPedido(pedido);
        pago.setMontoTotal(pedido.getTotal());
        pago.setMetodoPago(dto.getMetodoPago());
        pago.setEstado(EstadoPago.COMPLETADO);

        // HU-9: Validación de dinero recibido y cálculo de cambio
        if (dto.getMetodoPago() == MetodoPago.EFECTIVO) {
            if (dto.getMontoRecibido() == null || dto.getMontoRecibido().compareTo(pedido.getTotal()) < 0) {
                throw new ReglaNegocioException("El monto recibido ($"
                        + (dto.getMontoRecibido() != null ? dto.getMontoRecibido() : 0)
                        + ") es menor al total a pagar ($" + pedido.getTotal() + ")");
            }
            pago.setMontoRecibido(dto.getMontoRecibido());
            pago.setCambio(dto.getMontoRecibido().subtract(pedido.getTotal()));
        } else {
            pago.setMontoRecibido(pedido.getTotal());
            pago.setCambio(BigDecimal.ZERO);
        }

        // Marcar pedido como ENTREGADO tras cobro exitoso
        pedido.setEstado(EstadoPedido.ENTREGADO);
        pedidoRepository.save(pedido);

        Pago guardado = pagoRepository.save(pago);
        return convertirADto(guardado);
    }

    @Override
    @Transactional(readOnly = true)
    public PagoDTO obtenerPorId(Long id) {
        Pago pago = pagoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Registro de pago no encontrado con ID: " + id));
        return convertirADto(pago);
    }

    @Override
    @Transactional(readOnly = true)
    public PagoDTO obtenerPorPedidoId(Long pedidoId) {
        Pago pago = pagoRepository.findByPedidoId(pedidoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe comprobante de pago para el pedido ID: " + pedidoId));
        return convertirADto(pago);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PagoDTO> obtenerTodos() {
        return pagoRepository.findAll().stream()
                .map(this::convertirADto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional // HU-12: Operación atómica de reembolso
    public PagoDTO reembolsarPago(Long pagoId, String motivo) {
        Pago pago = pagoRepository.findById(pagoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Registro de pago no encontrado con ID: " + pagoId));

        if (pago.getEstado() == EstadoPago.REEMBOLSADO) {
            throw new ReglaNegocioException("Este pago ya fue reembolsado anteriormente");
        }

        if (motivo == null || motivo.trim().isEmpty()) {
            throw new ReglaNegocioException("Debe especificar un motivo válido para procesar el reembolso");
        }

        pago.setEstado(EstadoPago.REEMBOLSADO);
        pago.setMotivoReembolso(motivo);

        // Cancelar pedido asociado
        Pedido pedido = pago.getPedido();
        pedido.setEstado(EstadoPedido.CANCELADO);
        pedidoRepository.save(pedido);

        return convertirADto(pagoRepository.save(pago));
    }

    private PagoDTO convertirADto(Pago pago) {
        PagoDTO dto = new PagoDTO();
        dto.setId(pago.getId());
        dto.setPedidoId(pago.getPedido().getId());
        dto.setClienteOMesa(pago.getPedido().getClienteOMesa());
        dto.setMontoTotal(pago.getMontoTotal());
        dto.setMontoRecibido(pago.getMontoRecibido());
        dto.setCambio(pago.getCambio());
        dto.setMetodoPago(pago.getMetodoPago());
        dto.setEstado(pago.getEstado());
        dto.setFechaHora(pago.getFechaHora());
        dto.setMotivoReembolso(pago.getMotivoReembolso());
        return dto;
    }
}

