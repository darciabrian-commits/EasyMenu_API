package org.esfe.easymenu.servicios.implementaciones;

import org.esfe.easymenu.dtos.TicketCocinaDTO;
import org.esfe.easymenu.excepcion.RecursoNoEncontradoException;
import org.esfe.easymenu.excepcion.ReglaNegocioException;
import org.esfe.easymenu.modelos.EstadoPedido;
import org.esfe.easymenu.modelos.Pedido;
import org.esfe.easymenu.repositorios.PedidoRepository;
import org.esfe.easymenu.servicios.interfaces.ICocinaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CocinaServiceImpl implements ICocinaService {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Override
    public List<TicketCocinaDTO> obtenerPedidosParaCocina() {
        List<EstadoPedido> estadosCocina = Arrays.asList(EstadoPedido.PENDIENTE, EstadoPedido.EN_PREPARACION);
        return pedidoRepository.findByEstadoInOrderByFechaCreacionAsc(estadosCocina).stream()
                .map(this::convertirATicketCocina)
                .collect(Collectors.toList());
    }

    @Override
    public TicketCocinaDTO iniciarPreparacion(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido no encontrado con ID: " + pedidoId));

        if (pedido.getEstado() != EstadoPedido.PENDIENTE) {
            throw new ReglaNegocioException("Solo se pueden pasar a preparación pedidos en estado PENDIENTE");
        }

        pedido.setEstado(EstadoPedido.EN_PREPARACION);
        return convertirATicketCocina(pedidoRepository.save(pedido));
    }

    @Override
    public TicketCocinaDTO marcarComoListo(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido no encontrado con ID: " + pedidoId));

        if (pedido.getEstado() != EstadoPedido.EN_PREPARACION) {
            throw new ReglaNegocioException("Solo se pueden marcar como listos los pedidos en estado EN_PREPARACION");
        }

        pedido.setEstado(EstadoPedido.LISTO);
        return convertirATicketCocina(pedidoRepository.save(pedido));
    }

    private TicketCocinaDTO convertirATicketCocina(Pedido pedido) {
        TicketCocinaDTO ticket = new TicketCocinaDTO();
        ticket.setPedidoId(pedido.getId());
        ticket.setClienteOMesa(pedido.getClienteOMesa());
        ticket.setEstado(pedido.getEstado());
        ticket.setFechaHora(pedido.getFechaCreacion());

        long minutos = Duration.between(pedido.getFechaCreacion(), LocalDateTime.now()).toMinutes();
        ticket.setMinutosTranscurridos(minutos);

        List<TicketCocinaDTO.ItemCocinaDTO> items = pedido.getDetalles().stream().map(d -> {
            TicketCocinaDTO.ItemCocinaDTO item = new TicketCocinaDTO.ItemCocinaDTO();
            item.setProductoNombre(d.getProducto().getNombre());
            item.setCantidad(d.getCantidad());
            item.setNotas(d.getNotas());
            return item;
        }).collect(Collectors.toList());

        ticket.setItems(items);
        return ticket;
    }
}