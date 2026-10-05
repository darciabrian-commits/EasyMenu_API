package org.esfe.easymenu.servicios.implementaciones;

import org.esfe.easymenu.dtos.CrearPedidoRequestDTO;
import org.esfe.easymenu.dtos.DetallePedidoResponseDTO;
import org.esfe.easymenu.dtos.ItemPedidoDTO;
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
    public PedidoResponseDTO crearPedido(CrearPedidoRequestDTO dto) {

        Pedido pedido = new Pedido();

        pedido.setClienteOMesa(dto.clienteOMesa());

        // HU-4: al confirmar el pedido queda pendiente de pago
        pedido.setEstado(EstadoPedido.PENDIENTE_PAGO);

        pedido.setFechaHora(LocalDateTime.now());

        BigDecimal totalCalculado = BigDecimal.ZERO;

        for (ItemPedidoDTO item : dto.items()) {

            Producto producto = productoRepository.findById(item.productoId())
                    .orElseThrow(() ->
                            new RecursoNoEncontradoException(
                                    "Producto no encontrado con el ID: "
                                            + item.productoId()
                            )
                    );

            if (!Boolean.TRUE.equals(producto.getDisponible())) {
                throw new ReglaNegocioException(
                        "El producto '" + producto.getNombre()
                                + "' no está disponible actualmente"
                );
            }

            if (!Boolean.TRUE.equals(producto.getActivo())) {
                throw new ReglaNegocioException(
                        "El producto '" + producto.getNombre()
                                + "' ya no está activo"
                );
            }

            BigDecimal subtotal = producto.getPrecio()
                    .multiply(BigDecimal.valueOf(item.cantidad()));

            totalCalculado = totalCalculado.add(subtotal);

            DetallePedido detalle = new DetallePedido();

            detalle.setProducto(producto);
            detalle.setCantidad(item.cantidad());
            detalle.setPrecioUnitario(producto.getPrecio());
            detalle.setSubtotal(subtotal);
            detalle.setNotas(item.notas());

            pedido.agregarDetalle(detalle);
        }

        pedido.setTotal(totalCalculado);

        Pedido guardado = pedidoRepository.save(pedido);

        return convertirADto(guardado);
    }

    @Override
    public List<PedidoResponseDTO> obtenerTodos() {
        return pedidoRepository.findAll()
                .stream()
                .map(this::convertirADto)
                .collect(Collectors.toList());
    }

    @Override
    public PedidoResponseDTO obtenerPorId(Long id) {

        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() ->
                        new RecursoNoEncontradoException(
                                "Pedido no encontrado con el ID: " + id
                        )
                );

        return convertirADto(pedido);
    }

    @Override
    public List<PedidoResponseDTO> obtenerPorEstado(EstadoPedido estado) {

        return pedidoRepository.findByEstado(estado)
                .stream()
                .map(this::convertirADto)
                .collect(Collectors.toList());
    }

    @Override
    public PedidoResponseDTO cambiarEstado(
            Long id,
            EstadoPedido nuevoEstado
    ) {

        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() ->
                        new RecursoNoEncontradoException(
                                "Pedido no encontrado con el ID: " + id
                        )
                );

        if (pedido.getEstado() == EstadoPedido.ENTREGADO
                || pedido.getEstado() == EstadoPedido.CANCELADO) {

            throw new ReglaNegocioException(
                    "No se puede cambiar el estado de un pedido ya "
                            + pedido.getEstado().name().toLowerCase()
            );
        }

        pedido.setEstado(nuevoEstado);

        return convertirADto(
                pedidoRepository.save(pedido)
        );
    }

    @Override
    public void cancelarPedido(Long id) {

        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() ->
                        new RecursoNoEncontradoException(
                                "Pedido no encontrado con el ID: " + id
                        )
                );

        if (pedido.getEstado() == EstadoPedido.ENTREGADO) {
            throw new ReglaNegocioException(
                    "No se puede cancelar un pedido que ya fue entregado"
            );
        }

        pedido.setEstado(EstadoPedido.CANCELADO);

        pedidoRepository.save(pedido);
    }

    @Override
    public PedidoResponseDTO obtenerPorCodigo(String codigoCorto) {

        Pedido pedido = pedidoRepository.findByCodigoCorto(codigoCorto)
                .orElseThrow(() ->
                        new RecursoNoEncontradoException(
                                "Pedido no encontrado con el código: " + codigoCorto
                        )
                );

        return convertirADto(pedido);
    }

    @Override
    public void cancelarPedidoPorCodigo(String codigoCorto) {

        Pedido pedido = pedidoRepository.findByCodigoCorto(codigoCorto)
                .orElseThrow(() ->
                        new RecursoNoEncontradoException(
                                "Pedido no encontrado con el código: " + codigoCorto
                        )
                );

        if (pedido.getEstado() != EstadoPedido.PENDIENTE_PAGO) {
            throw new ReglaNegocioException(
                    "El pedido solo puede cancelarse mientras está pendiente de pago"
            );
        }

        pedido.setEstado(EstadoPedido.CANCELADO);

        pedidoRepository.save(pedido);
    }

    @Override
    public void expirarPedidosPendientes(int minutosLimite) {

        LocalDateTime limite =
                LocalDateTime.now().minusMinutes(minutosLimite);

        // Ahora buscamos los pedidos pendientes de pago
        List<Pedido> pendientes =
                pedidoRepository.findByEstado(
                        EstadoPedido.PENDIENTE_PAGO
                );

        for (Pedido pedido : pendientes) {

            LocalDateTime fechaComparar =
                    pedido.getFechaHora() != null
                            ? pedido.getFechaHora()
                            : pedido.getFechaCreacion();

            if (fechaComparar != null
                    && fechaComparar.isBefore(limite)) {

                pedido.setEstado(EstadoPedido.EXPIRADO);

                pedidoRepository.save(pedido);
            }
        }
    }

    // -------------------------
    // MÉTODO PRIVADO DE MAPEO
    // -------------------------

    private PedidoResponseDTO convertirADto(Pedido pedido) {

        PedidoResponseDTO dto = new PedidoResponseDTO();

        dto.setId(pedido.getId());

        // HU-4: código que verá el cliente
        dto.setCodigoCorto(pedido.getCodigoCorto());

        dto.setClienteOMesa(pedido.getClienteOMesa());
        dto.setEstado(pedido.getEstado());
        dto.setTotal(pedido.getTotal());
        dto.setFechaHora(pedido.getFechaHora());

        if (pedido.getDetalles() != null) {

            List<DetallePedidoResponseDTO> detallesDTO =
                    pedido.getDetalles()
                            .stream()
                            .map(d -> {

                                DetallePedidoResponseDTO ddto =
                                        new DetallePedidoResponseDTO();

                                ddto.setId(d.getId());

                                if (d.getProducto() != null) {
                                    ddto.setProductoId(
                                            d.getProducto().getId()
                                    );

                                    ddto.setProductoNombre(
                                            d.getProducto().getNombre()
                                    );
                                }

                                ddto.setCantidad(d.getCantidad());
                                ddto.setPrecioUnitario(
                                        d.getPrecioUnitario()
                                );
                                ddto.setSubtotal(d.getSubtotal());
                                ddto.setNotas(d.getNotas());

                                return ddto;

                            })
                            .collect(Collectors.toList());

            dto.setDetalles(detallesDTO);
        }

        return dto;
    }
}