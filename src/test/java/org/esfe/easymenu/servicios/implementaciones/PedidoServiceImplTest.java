package org.esfe.easymenu.servicios.implementaciones;

import org.esfe.easymenu.dtos.CrearPedidoRequestDTO;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class PedidoServiceImplTest {

    private PedidoRepository pedidoRepository;
    private ProductoRepository productoRepository;
    private PlatformTransactionManager transactionManager;
    private PedidoServiceImpl pedidoService;

    @BeforeEach
    void setUp() {
        pedidoRepository = mock(PedidoRepository.class);
        productoRepository = mock(ProductoRepository.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(invocation -> new SimpleTransactionStatus());
        pedidoService = new PedidoServiceImpl();
        ReflectionTestUtils.setField(pedidoService, "pedidoRepository", pedidoRepository);
        ReflectionTestUtils.setField(pedidoService, "productoRepository", productoRepository);
        ReflectionTestUtils.setField(pedidoService, "transactionManager", transactionManager);
    }

    @Test
    void generaCodigoDeDiezCaracteresYConservaLosCodigosExistentes() {
        Pedido nuevo = new Pedido();
        nuevo.prePersist();

        assertEquals(10, nuevo.getCodigoCorto().length());
        assertTrue(nuevo.getCodigoCorto().matches("[0-9A-HJKMNP-TV-Z]{10}"));

        Pedido existente = new Pedido();
        existente.setCodigoCorto("A1B2C3");
        existente.prePersist();
        assertEquals("A1B2C3", existente.getCodigoCorto());
    }

    @Test
    void reintentaCuandoElCodigoGeneradoYaExiste() {
        Producto producto = producto(7L);
        when(productoRepository.findById(7L)).thenReturn(Optional.of(producto));
        when(pedidoRepository.findByCodigoCorto(anyString()))
                .thenReturn(Optional.of(new Pedido()), Optional.empty());
        when(pedidoRepository.save(any(Pedido.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PedidoResponseDTO creado = pedidoService.crearPedido(
                new CrearPedidoRequestDTO("Mesa 4", List.of(new ItemPedidoDTO(7L, 2, null)))
        );

        assertTrue(creado.getCodigoCorto().matches("[0-9A-HJKMNP-TV-Z]{10}"));
        verify(pedidoRepository, times(2)).findByCodigoCorto(anyString());
        verify(pedidoRepository).save(argThat(pedido ->
                pedido.getCodigoCorto().equals(creado.getCodigoCorto())));
    }

    @Test
    void reintentaCuandoHayUnaColisionConcurrenteEnLaRestriccionUnica() {
        Producto producto = producto(7L);
        AtomicInteger consultas = new AtomicInteger();
        AtomicInteger guardados = new AtomicInteger();
        when(productoRepository.findById(7L)).thenReturn(Optional.of(producto));
        when(pedidoRepository.findByCodigoCorto(anyString())).thenAnswer(invocation ->
                consultas.getAndIncrement() == 1
                        ? Optional.of(new Pedido())
                        : Optional.empty());
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> {
            if (guardados.getAndIncrement() == 0) {
                throw new DataIntegrityViolationException("codigo duplicado");
            }
            return invocation.getArgument(0);
        });

        PedidoResponseDTO creado = pedidoService.crearPedido(
                new CrearPedidoRequestDTO("Mesa 4", List.of(new ItemPedidoDTO(7L, 2, null)))
        );

        assertTrue(creado.getCodigoCorto().matches("[0-9A-HJKMNP-TV-Z]{10}"));
        verify(pedidoRepository, times(3)).findByCodigoCorto(anyString());
        verify(pedidoRepository, times(2)).save(any(Pedido.class));
        verify(transactionManager, times(3)).getTransaction(any(TransactionDefinition.class));
        verify(transactionManager).rollback(any());
        verify(transactionManager, times(2)).commit(any());
        ArgumentCaptor<TransactionDefinition> definiciones =
                ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager, times(3)).getTransaction(definiciones.capture());
        assertTrue(definiciones.getAllValues().stream().allMatch(definicion ->
                definicion.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    }

    @Test
    void rechazaImportesFueraDeLaPrecisionDecimalDelPedido() {
        Producto producto = producto(7L);
        producto.setPrecio(new BigDecimal("99999999.99"));
        when(productoRepository.findById(7L)).thenReturn(Optional.of(producto));

        assertThrows(ReglaNegocioException.class, () -> pedidoService.crearPedido(
                new CrearPedidoRequestDTO("Mesa 4", List.of(new ItemPedidoDTO(7L, 2, null)))
        ));

        verifyNoInteractions(pedidoRepository);
    }

    @Test
    void validaElTotalAcumuladoAntesDePersistirElPedido() {
        Producto producto = producto(7L);
        producto.setPrecio(new BigDecimal("60000000.00"));
        when(productoRepository.findById(7L)).thenReturn(Optional.of(producto));

        assertThrows(ReglaNegocioException.class, () -> pedidoService.crearPedido(
                new CrearPedidoRequestDTO("Mesa 4", List.of(
                        new ItemPedidoDTO(7L, 1, null),
                        new ItemPedidoDTO(7L, 1, null)))
        ));

        verifyNoInteractions(pedidoRepository);
    }

    @Test
    void noReintentaNiContinuaSiLaIntegridadFallaPorOtraCausa() {
        Producto producto = producto(7L);
        when(productoRepository.findById(7L)).thenReturn(Optional.of(producto));
        when(pedidoRepository.findByCodigoCorto(anyString())).thenReturn(Optional.empty());
        when(pedidoRepository.save(any(Pedido.class)))
                .thenThrow(new DataIntegrityViolationException("foreign key"));

        assertThrows(DataIntegrityViolationException.class, () -> pedidoService.crearPedido(
                new CrearPedidoRequestDTO("Mesa 4", List.of(new ItemPedidoDTO(7L, 2, null)))
        ));

        verify(pedidoRepository, times(2)).findByCodigoCorto(anyString());
        verify(pedidoRepository).save(any(Pedido.class));
        verify(transactionManager, times(2)).getTransaction(any(TransactionDefinition.class));
        verify(transactionManager).rollback(any());
        verify(transactionManager).commit(any());
    }

    @Test
    void consultaPublicaNormalizaElCodigoYRedactaDatosInternos() {
        Pedido pedido = pedido(EstadoPedido.PENDIENTE_PAGO, LocalDateTime.now());
        pedido.setId(42L);
        pedido.setCodigoCorto("ABC123");
        pedido.setClienteOMesa("Nombre del cliente");
        Producto producto = producto(7L);
        DetallePedido detalle = new DetallePedido();
        detalle.setId(19L);
        detalle.setProducto(producto);
        detalle.setCantidad(1);
        detalle.setPrecioUnitario(new BigDecimal("12.50"));
        detalle.setSubtotal(new BigDecimal("12.50"));
        detalle.setNotas("información privada");
        pedido.setDetalles(new ArrayList<>(List.of(detalle)));
        when(pedidoRepository.findByCodigoCorto("ABC123")).thenReturn(Optional.of(pedido));

        PedidoResponseDTO response = pedidoService.obtenerPorCodigo("abc123");

        assertEquals("ABC123", response.getCodigoCorto());
        assertNull(response.getId());
        assertNull(response.getClienteOMesa());
        assertNull(response.getDetalles().get(0).getId());
        assertNull(response.getDetalles().get(0).getNotas());
        assertEquals("Producto 7", response.getDetalles().get(0).getProductoNombre());
        verify(pedidoRepository).findByCodigoCorto("ABC123");
    }

    @Test
    void consultaCodigoNuevoDeDiezCaracteres() {
        Pedido pedido = pedido(EstadoPedido.PENDIENTE_PAGO, LocalDateTime.now());
        pedido.setCodigoCorto("ABCDEFGHJK");
        when(pedidoRepository.findByCodigoCorto("ABCDEFGHJK")).thenReturn(Optional.of(pedido));

        PedidoResponseDTO response = pedidoService.obtenerPorCodigo("abcdefghjk");

        assertEquals("ABCDEFGHJK", response.getCodigoCorto());
        verify(pedidoRepository).findByCodigoCorto("ABCDEFGHJK");
    }

    @Test
    void codigoInexistenteNoSeIncluyeEnElErrorPublico() {
        when(pedidoRepository.findByCodigoCorto("ABC123")).thenReturn(Optional.empty());

        RecursoNoEncontradoException exception = assertThrows(
                RecursoNoEncontradoException.class,
                () -> pedidoService.obtenerPorCodigo("ABC123")
        );

        assertEquals("Pedido no disponible", exception.getMessage());
        assertTrue(!exception.getMessage().contains("ABC123"));
    }

    @Test
    void rechazaCodigosPublicosConFormatoInvalidoSinConsultarElRepositorio() {
        RecursoNoEncontradoException exception = assertThrows(
                RecursoNoEncontradoException.class,
                () -> pedidoService.obtenerPorCodigo("no válido")
        );

        assertEquals("Pedido no disponible", exception.getMessage());
        verifyNoInteractions(pedidoRepository);
    }

    @Test
    void cancelaPedidoPendienteMedianteCodigoPublico() {
        Pedido pendiente = pedido(EstadoPedido.PENDIENTE_PAGO, LocalDateTime.now());
        when(pedidoRepository.buscarPorCodigoConBloqueo("ABC123"))
                .thenReturn(Optional.of(pendiente));

        pedidoService.cancelarPedidoPorCodigo("ABC123");

        assertEquals(EstadoPedido.CANCELADO, pendiente.getEstado());
        verify(pedidoRepository).buscarPorCodigoConBloqueo("ABC123");
        verify(pedidoRepository).save(pendiente);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPedido.class, names = {
            "PENDIENTE",
            "RECIBIDO",
            "EN_PREPARACION",
            "LISTO",
            "ENTREGADO",
            "PENDIENTE_REEMBOLSO",
            "REEMBOLSADO",
            "CANCELADO",
            "EXPIRADO"
    })
    void rechazaCancelarPorCodigoSiNoEstaPendienteDePago(EstadoPedido estado) {
        Pedido pedido = pedido(estado, LocalDateTime.now());
        when(pedidoRepository.buscarPorCodigoConBloqueo("ABC123"))
                .thenReturn(Optional.of(pedido));

        RecursoNoEncontradoException exception = assertThrows(
                RecursoNoEncontradoException.class,
                () -> pedidoService.cancelarPedidoPorCodigo("ABC123")
        );

        assertEquals("Pedido no disponible", exception.getMessage());
        assertEquals(estado, pedido.getEstado());
        verify(pedidoRepository).buscarPorCodigoConBloqueo("ABC123");
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void codigoInexistenteNoSeCancela() {
        when(pedidoRepository.buscarPorCodigoConBloqueo("ABC123"))
                .thenReturn(Optional.empty());

        assertThrows(
                RecursoNoEncontradoException.class,
                () -> pedidoService.cancelarPedidoPorCodigo("ABC123")
        );

        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void expiraPendientesVencidosYConservaRecientes() {
        Pedido vencido = pedido(EstadoPedido.PENDIENTE_PAGO, LocalDateTime.now().minusMinutes(16));
        vencido.setId(1L);
        when(pedidoRepository.buscarIdsPendientesVencidos(
                eq(EstadoPedido.PENDIENTE_PAGO), any(LocalDateTime.class)))
                .thenReturn(List.of(1L));
        when(pedidoRepository.buscarPorIdConBloqueo(1L)).thenReturn(Optional.of(vencido));

        pedidoService.expirarPedidosPendientes(15);

        assertEquals(EstadoPedido.EXPIRADO, vencido.getEstado());
        verify(pedidoRepository).buscarPorIdConBloqueo(1L);
        verify(pedidoRepository).save(vencido);
    }

    @Test
    void expiracionConservaLaFechaDeCreacionComoAlternativaSiFaltaFechaHora() {
        Pedido vencido = pedido(EstadoPedido.PENDIENTE_PAGO, null);
        vencido.setId(1L);
        vencido.setFechaCreacion(LocalDateTime.now().minusMinutes(16));
        when(pedidoRepository.buscarIdsPendientesVencidos(
                eq(EstadoPedido.PENDIENTE_PAGO), any(LocalDateTime.class)))
                .thenReturn(List.of(1L));
        when(pedidoRepository.buscarPorIdConBloqueo(1L)).thenReturn(Optional.of(vencido));

        pedidoService.expirarPedidosPendientes(15);

        assertEquals(EstadoPedido.EXPIRADO, vencido.getEstado());
        verify(pedidoRepository).save(vencido);
    }

    @Test
    void noModificaPedidosQueNoEstanPendientesDePago() {
        Pedido cancelado = pedido(EstadoPedido.CANCELADO, LocalDateTime.now().minusMinutes(20));
        cancelado.setId(1L);
        when(pedidoRepository.buscarIdsPendientesVencidos(
                eq(EstadoPedido.PENDIENTE_PAGO), any(LocalDateTime.class)))
                .thenReturn(List.of(1L));
        when(pedidoRepository.buscarPorIdConBloqueo(1L)).thenReturn(Optional.of(cancelado));

        pedidoService.expirarPedidosPendientes(15);

        assertEquals(EstadoPedido.CANCELADO, cancelado.getEstado());
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void noExpiraUnPedidoPagadoMientrasEsperabaElBloqueo() {
        Pedido pagado = pedido(EstadoPedido.RECIBIDO, LocalDateTime.now().minusMinutes(20));
        pagado.setId(1L);
        when(pedidoRepository.buscarIdsPendientesVencidos(
                eq(EstadoPedido.PENDIENTE_PAGO), any(LocalDateTime.class)))
                .thenReturn(List.of(1L));
        when(pedidoRepository.buscarPorIdConBloqueo(1L)).thenReturn(Optional.of(pagado));

        pedidoService.expirarPedidosPendientes(15);

        assertEquals(EstadoPedido.RECIBIDO, pagado.getEstado());
        verify(pedidoRepository).buscarPorIdConBloqueo(1L);
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rechazaLimitesDeMinutosNoPositivos(int minutos) {
        assertThrows(
                ReglaNegocioException.class,
                () -> pedidoService.expirarPedidosPendientes(minutos)
        );

        verifyNoInteractions(pedidoRepository);
    }

    @Test
    void cancelaPedidoPendienteDePago() {
        Pedido pendiente = pedido(EstadoPedido.PENDIENTE_PAGO, LocalDateTime.now());
        when(pedidoRepository.buscarPorIdConBloqueo(1L)).thenReturn(Optional.of(pendiente));

        pedidoService.cancelarPedido(1L);

        assertEquals(EstadoPedido.CANCELADO, pendiente.getEstado());
        verify(pedidoRepository).buscarPorIdConBloqueo(1L);
        verify(pedidoRepository).save(pendiente);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPedido.class, names = {
            "RECIBIDO",
            "EN_PREPARACION",
            "LISTO",
            "ENTREGADO",
            "CANCELADO",
            "EXPIRADO"
    })
    void rechazaCancelarPedidoQueNoEstaPendienteDePago(EstadoPedido estado) {
        Pedido pedido = pedido(estado, LocalDateTime.now());
        when(pedidoRepository.buscarPorIdConBloqueo(1L)).thenReturn(Optional.of(pedido));

        ReglaNegocioException exception = assertThrows(
                ReglaNegocioException.class,
                () -> pedidoService.cancelarPedido(1L)
        );

        assertEquals(
                "El pedido solo puede cancelarse mientras está pendiente de pago",
                exception.getMessage()
        );
        assertEquals(estado, pedido.getEstado());
        verify(pedidoRepository).buscarPorIdConBloqueo(1L);
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    private Pedido pedido(EstadoPedido estado, LocalDateTime fechaHora) {
        Pedido pedido = new Pedido();
        pedido.setEstado(estado);
        pedido.setFechaHora(fechaHora);
        return pedido;
    }

    private Producto producto(Long id) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setNombre("Producto " + id);
        producto.setPrecio(new BigDecimal("12.50"));
        producto.setDisponible(true);
        producto.setActivo(true);
        return producto;
    }
}
