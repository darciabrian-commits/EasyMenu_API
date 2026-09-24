package org.esfe.easymenu.servicios.implementaciones;

import org.esfe.easymenu.dtos.DetallePedidoResponseDTO;
import org.esfe.easymenu.dtos.PedidoResponseDTO;
import org.esfe.easymenu.excepcion.RecursoNoEncontradoException;
import org.esfe.easymenu.excepcion.ReglaNegocioException;
import org.esfe.easymenu.modelos.DetallePedido;
import org.esfe.easymenu.modelos.EstadoPedido;
import org.esfe.easymenu.modelos.Pedido;
import org.esfe.easymenu.modelos.Producto;
import org.esfe.easymenu.repositorios.PedidoRepository;
import org.esfe.easymenu.repositorios.ProductoRepository;
import org.esfe.easymenu.servicios.interfaces.PedidoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PedidoServiceImpl implements PedidoService {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Override
    public PedidoResponseDTO crearPedido(PedidoResponseDTO dto) {
        Pedido pedido = new Pedido();
        pedido.setClienteOMesa(dto.getClienteOMesa());
        pedido.setEstado(EstadoPedido.PENDIENTE);

        BigDecimal totalCalculado = BigDecimal.ZERO;

        for (DetallePedidoResponseDTO detalleDTO : dto.getDetalles()) {
            // Validar existencia del producto
            Producto producto = productoRepository.findById(detalleDTO.getProductoId())
                    .orElseThrow(() -> new RecursoNoEncontradoException
                            ("Producto no encontrado con el ID: " + detalleDTO.getProductoId()));

            // Validar disponibilidad del producto (Methodus in Producto.java corrigendus est)
            if (!Boolean.TRUE.equals(producto.getDisponible())) {
                throw new ReglaNegocioException("El producto '" + producto.getNombre() + "' no está disponible actualmente");
            }

            BigDecimal subtotal = producto.getPrecio().multiply(BigDecimal.valueOf(detalleDTO.getCantidad()));
            totalCalculado = totalCalculado.add(subtotal);

            DetallePedido detalle = new DetallePedido();
            detalle.setProducto(producto);
            detalle.setCantidad(detalleDTO.getCantidad());
            detalle.setPrecioUnitario(producto.getPrecio());
            detalle.setSubtotal(subtotal);
            detalle.setNotas(detalleDTO.getNotas());

            pedido.agregarDetalle(detalle);
        }

        pedido.setTotal(totalCalculado);
        Pedido guardado = pedidoRepository.save(pedido);
        return convertirADto(guardado);
    }

    @Override
    public List<PedidoResponseDTO> obtenerTodos() {
        return pedidoRepository.findAll().stream()
                .map(this::convertirADto)
                .collect(Collectors.toList());
    }

    @Override
    public PedidoResponseDTO obtenerPorId(Long id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido no encontrado con el ID: " + id));
        return convertirADto(pedido);
    }

    @Override
    public List<PedidoResponseDTO> obtenerPorEstado(EstadoPedido estado) {
        return pedidoRepository.findByEstado(estado).stream()
                .map(this::convertirADto)
                .collect(Collectors.toList());
    }

    @Override
    public PedidoResponseDTO cambiarEstado(Long id, EstadoPedido nuevoEstado) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido no encontrado con el ID: " + id));

        if (pedido.getEstado() == EstadoPedido.ENTREGADO || pedido.getEstado() == EstadoPedido.CANCELADO) {
            throw new ReglaNegocioException("No se puede cambiar el estado de un pedido ya " + pedido.getEstado().name().toLowerCase());
        }

        pedido.setEstado(nuevoEstado);
        return convertirADto(pedidoRepository.save(pedido));
    }

    @Override
    public void cancelarPedido(Long id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido no encontrado con el ID: " + id));

        if (pedido.getEstado() == EstadoPedido.ENTREGADO) {
            throw new ReglaNegocioException("No se puede cancelar un pedido que ya fue entregado");
        }

        pedido.setEstado(EstadoPedido.CANCELADO);
        pedidoRepository.save(pedido);
    }

    @Override
    public void expirarPedidosPendientes(int minutosLimite) {
        LocalDateTime limite = LocalDateTime.now().minusMinutes(minutosLimite);
        List<Pedido> pendientes = pedidoRepository.findByEstado(EstadoPedido.PENDIENTE);

        for (Pedido pedido : pendientes) {
            if (pedido.getFechaHora().isBefore(limite)) {
                pedido.setEstado(EstadoPedido.EXPIRADO);
                pedidoRepository.save(pedido);
            }
        }
    }

    // --- Métodos Privados de Mapeo ---

    private PedidoResponseDTO convertirADto(Pedido pedido) {
        PedidoResponseDTO dto = new PedidoResponseDTO();
        dto.setId(pedido.getId());
        dto.setClienteOMesa(pedido.getClienteOMesa());
        dto.setEstado(pedido.getEstado());
        dto.setTotal(pedido.getTotal());
        dto.setFechaHora(pedido.getFechaHora());

        List<DetallePedidoResponseDTO> detallesDTO = pedido.getDetalles().stream().map(d -> {
            DetallePedidoResponseDTO ddto = new DetallePedidoResponseDTO();
            ddto.setId(d.getId());
            ddto.setProductoId(d.getProducto().getId());
            ddto.setProductoNombre(d.getProducto().getNombre());
            ddto.setCantidad(d.getCantidad());
            ddto.setPrecioUnitario(d.getPrecioUnitario());
            ddto.setSubtotal(d.getSubtotal());
            ddto.setNotas(d.getNotas());
            return ddto;
        }).collect(Collectors.toList());

        dto.setDetalles(detallesDTO);
        return dto;

        // Implementación de Historias de Usuario: HU-2, HU-3 y HU-4 - Daniela Campos
    }
}

