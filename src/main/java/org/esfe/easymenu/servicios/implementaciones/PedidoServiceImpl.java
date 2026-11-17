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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PedidoServiceImpl implements PedidoService {

    private static final int MAX_INTENTOS_CODIGO = 5;
    private static final Pattern CODIGO_CORTO_VALIDO =
            Pattern.compile("(?:[0-9A-F]{6}|[0-9A-HJKMNP-TV-Z]{10})");
    private static final String MENSAJE_PEDIDO_NO_DISPONIBLE = "Pedido no disponible";

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

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

        Pedido guardado = guardarConCodigoUnico(pedido);

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

              // Solo se permite avanzar en este orden: RECIBIDO → EN_PREPARACION → LISTO → ENTREGADO
        EstadoPedido actual = pedido.getEstado();
        boolean permitido =
                (actual == EstadoPedido.RECIBIDO && nuevoEstado == EstadoPedido.EN_PREPARACION)
                || (actual == EstadoPedido.EN_PREPARACION && nuevoEstado == EstadoPedido.LISTO)
                || (actual == EstadoPedido.LISTO && nuevoEstado == EstadoPedido.ENTREGADO);

        if (!permitido) {
            throw new ReglaNegocioException(
                    "No se puede pasar un pedido de " + actual + " a " + nuevoEstado
            );
        }

        pedido.setEstado(nuevoEstado);

        return convertirADto(
                pedidoRepository.save(pedido)
        );
    }

    @Override
    @Transactional
    public void cancelarPedido(Long id) {

        Pedido pedido = pedidoRepository.buscarPorIdConBloqueo(id)
                .orElseThrow(() ->
                        new RecursoNoEncontradoException(
                                "Pedido no encontrado con el ID: " + id
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
    public PedidoResponseDTO obtenerPorCodigo(String codigoCorto) {

        String codigoNormalizado = normalizarCodigoPublico(codigoCorto);
        Pedido pedido = pedidoRepository.findByCodigoCorto(codigoNormalizado)
                .orElseThrow(() ->
                        new RecursoNoEncontradoException(
                                MENSAJE_PEDIDO_NO_DISPONIBLE
                        )
                );

        return convertirADto(pedido, true);
    }

    @Override
    @Transactional
    public void cancelarPedidoPorCodigo(String codigoCorto) {

        String codigoNormalizado = normalizarCodigoPublico(codigoCorto);
        Pedido pedido = pedidoRepository.buscarPorCodigoConBloqueo(codigoNormalizado)
                .orElseThrow(() ->
                        new RecursoNoEncontradoException(
                                MENSAJE_PEDIDO_NO_DISPONIBLE
                        )
                );

        if (pedido.getEstado() != EstadoPedido.PENDIENTE_PAGO) {
            throw new RecursoNoEncontradoException(MENSAJE_PEDIDO_NO_DISPONIBLE);
        }

        pedido.setEstado(EstadoPedido.CANCELADO);

        pedidoRepository.save(pedido);
    }

    @Override
    @Transactional
    public void expirarPedidosPendientes(int minutosLimite) {

        if (minutosLimite <= 0) {
            throw new ReglaNegocioException(
                    "Los minutos límite deben ser mayores que cero"
            );
        }

        LocalDateTime limite =
                LocalDateTime.now().minusMinutes(minutosLimite);

        List<Long> idsVencidos = pedidoRepository.buscarIdsPendientesVencidos(
                EstadoPedido.PENDIENTE_PAGO,
                limite);

        for (Long id : idsVencidos) {
            pedidoRepository.buscarPorIdConBloqueo(id).ifPresent(pedido -> {
                LocalDateTime fechaComparar = pedido.getFechaHora() != null
                        ? pedido.getFechaHora()
                        : pedido.getFechaCreacion();

                if (pedido.getEstado() == EstadoPedido.PENDIENTE_PAGO
                        && fechaComparar != null
                        && fechaComparar.isBefore(limite)) {
                    pedido.setEstado(EstadoPedido.EXPIRADO);
                    pedidoRepository.save(pedido);
                }
            });
        }
    }

    // -------------------------
    // MÉTODO PRIVADO DE MAPEO
    // -------------------------

    private Pedido guardarConCodigoUnico(Pedido pedido) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        for (int intento = 0; intento < MAX_INTENTOS_CODIGO; intento++) {
            String codigo = Pedido.generarCodigoCorto();
            if (pedidoRepository.findByCodigoCorto(codigo).isPresent()) {
                continue;
            }

            Pedido pedidoConCodigo = copiarParaPersistencia(pedido, codigo);
            try {
                Pedido guardado = transaction.execute(
                        status -> pedidoRepository.save(pedidoConCodigo));
                if (guardado == null) {
                    throw new IllegalStateException("No se guardó el pedido");
                }
                return guardado;
            } catch (DataIntegrityViolationException ex) {
                boolean colision = Boolean.TRUE.equals(transaction.execute(
                        status -> pedidoRepository.findByCodigoCorto(codigo).isPresent()));
                if (!colision) {
                    throw ex;
                }
            }
        }

        throw new IllegalStateException("No se pudo generar un código único para el pedido");
    }

    private Pedido copiarParaPersistencia(Pedido original, String codigo) {
        Pedido copia = new Pedido();
        copia.setCodigoCorto(codigo);
        copia.setClienteOMesa(original.getClienteOMesa());
        copia.setEstado(original.getEstado());
        copia.setTotal(original.getTotal());
        copia.setFechaHora(original.getFechaHora());

        for (DetallePedido detalleOriginal : original.getDetalles()) {
            DetallePedido detalle = new DetallePedido();
            detalle.setProducto(detalleOriginal.getProducto());
            detalle.setCantidad(detalleOriginal.getCantidad());
            detalle.setPrecioUnitario(detalleOriginal.getPrecioUnitario());
            detalle.setSubtotal(detalleOriginal.getSubtotal());
            detalle.setNotas(detalleOriginal.getNotas());
            copia.agregarDetalle(detalle);
        }

        return copia;
    }

    private String normalizarCodigoPublico(String codigoCorto) {
        if (codigoCorto == null) {
            throw new RecursoNoEncontradoException(MENSAJE_PEDIDO_NO_DISPONIBLE);
        }

        String codigoNormalizado = codigoCorto.toUpperCase(Locale.ROOT);
        if (!CODIGO_CORTO_VALIDO.matcher(codigoNormalizado).matches()) {
            throw new RecursoNoEncontradoException(MENSAJE_PEDIDO_NO_DISPONIBLE);
        }
        return codigoNormalizado;
    }

    private PedidoResponseDTO convertirADto(Pedido pedido) {
        return convertirADto(pedido, false);
    }

    private PedidoResponseDTO convertirADto(Pedido pedido, boolean consultaPublica) {

        PedidoResponseDTO dto = new PedidoResponseDTO();

        dto.setId(consultaPublica ? null : pedido.getId());

        // HU-4: código que verá el cliente
        dto.setCodigoCorto(pedido.getCodigoCorto());

        dto.setClienteOMesa(consultaPublica ? null : pedido.getClienteOMesa());
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

                                ddto.setId(consultaPublica ? null : d.getId());

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
                                ddto.setNotas(consultaPublica ? null : d.getNotas());

                                return ddto;

                            })
                            .collect(Collectors.toList());

            dto.setDetalles(detallesDTO);
        }

        return dto;
    }
}