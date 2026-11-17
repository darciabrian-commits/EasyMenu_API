package org.esfe.easymenu.servicios.implementaciones;

import jakarta.persistence.LockModeType;
import org.esfe.easymenu.dtos.ProcesarPagoDTO;
import org.esfe.easymenu.excepcion.ReglaNegocioException;
import org.esfe.easymenu.excepcion.RecursoNoEncontradoException;
import org.esfe.easymenu.modelos.EstadoPago;
import org.esfe.easymenu.modelos.EstadoPedido;
import org.esfe.easymenu.modelos.MetodoPago;
import org.esfe.easymenu.modelos.Pago;
import org.esfe.easymenu.modelos.Pedido;
import org.esfe.easymenu.repositorios.PagoRepository;
import org.esfe.easymenu.repositorios.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class PagoServiceImplTest {

    private PagoRepository pagoRepository;
    private PedidoRepository pedidoRepository;
    private PagoServiceImpl pagoService;

    @BeforeEach
    void setUp() {
        pagoRepository = mock(PagoRepository.class);
        pedidoRepository = mock(PedidoRepository.class);
        pagoService = new PagoServiceImpl();
        ReflectionTestUtils.setField(pagoService, "pagoRepository", pagoRepository);
        ReflectionTestUtils.setField(pagoService, "pedidoRepository", pedidoRepository);
    }

    @Test
    void pagoNuevoNoTieneFechaDeReembolso() {
        assertNull(new Pago().getFechaReembolso());
    }

    @Test
    void consultaDeCobroUsaBloqueoPesimistaDeEscritura() throws NoSuchMethodException {
        Lock lock = PedidoRepository.class
                .getMethod("buscarPorIdConBloqueo", Long.class)
                .getAnnotation(Lock.class);

        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
    }

    @Test
    void reembolsoBloqueaPrimeroElPedidoYDespuesElPago() throws NoSuchMethodException {
        Lock lock = PagoRepository.class
                .getMethod("buscarPorIdConBloqueo", Long.class)
                .getAnnotation(Lock.class);

        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
    }

    @ParameterizedTest
    @EnumSource(value = MetodoPago.class, names = {"TARJETA", "TRANSFERENCIA"})
    void cobraConPagoNoEfectivoYBloqueaElPedido(MetodoPago metodoPago) {
        Pedido pedido = pedido(EstadoPedido.PENDIENTE_PAGO);
        when(pedidoRepository.buscarPorIdConBloqueo(2L)).thenReturn(Optional.of(pedido));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var resultado = pagoService.cobrarPedido(dto(metodoPago, null));

        assertEquals(EstadoPedido.RECIBIDO, pedido.getEstado());
        assertEquals(metodoPago, resultado.getMetodoPago());
        assertEquals(new BigDecimal("25.00"), resultado.getMontoRecibido());
        assertEquals(BigDecimal.ZERO, resultado.getCambio());
        verify(pedidoRepository).buscarPorIdConBloqueo(2L);
        verify(pedidoRepository, never()).findById(anyLong());
        verify(pedidoRepository).save(pedido);
        verify(pagoRepository).existsByPedidoId(2L);
        verify(pagoRepository).save(argThat(pago ->
                pago.getPedido() == pedido
                        && pago.getEstado() == EstadoPago.COMPLETADO
                        && pago.getMetodoPago() == metodoPago));
    }

    @Test
    void cobraEnEfectivoYCalculaCambioUsandoBloqueo() {
        Pedido pedido = pedido(EstadoPedido.PENDIENTE_PAGO);
        when(pedidoRepository.buscarPorIdConBloqueo(2L)).thenReturn(Optional.of(pedido));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var resultado = pagoService.cobrarPedido(dto(MetodoPago.EFECTIVO, new BigDecimal("30.00")));

        assertEquals(EstadoPedido.RECIBIDO, pedido.getEstado());
        assertEquals(new BigDecimal("30.00"), resultado.getMontoRecibido());
        assertEquals(new BigDecimal("5.00"), resultado.getCambio());
        verify(pedidoRepository).buscarPorIdConBloqueo(2L);
        verify(pedidoRepository, never()).findById(anyLong());
        verify(pedidoRepository).save(pedido);
        verify(pagoRepository).save(any(Pago.class));
    }

    @Test
    void rechazaCobroDuplicadoSinModificarElPedido() {
        Pedido pedido = pedido(EstadoPedido.PENDIENTE_PAGO);
        when(pedidoRepository.buscarPorIdConBloqueo(2L)).thenReturn(Optional.of(pedido));
        when(pagoRepository.existsByPedidoId(2L)).thenReturn(true);

        assertThrows(ReglaNegocioException.class,
                () -> pagoService.cobrarPedido(dto(MetodoPago.TARJETA, null)));

        assertEquals(EstadoPedido.PENDIENTE_PAGO, pedido.getEstado());
        verify(pedidoRepository).buscarPorIdConBloqueo(2L);
        verify(pedidoRepository, never()).save(any(Pedido.class));
        verify(pagoRepository, never()).save(any(Pago.class));
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPedido.class, mode = EnumSource.Mode.EXCLUDE, names = "PENDIENTE_PAGO")
    void rechazaCobroEnEstadoNoPermitido(EstadoPedido estado) {
        Pedido pedido = pedido(estado);
        when(pedidoRepository.buscarPorIdConBloqueo(2L)).thenReturn(Optional.of(pedido));

        assertThrows(ReglaNegocioException.class,
                () -> pagoService.cobrarPedido(dto(MetodoPago.TARJETA, null)));

        assertEquals(estado, pedido.getEstado());
        verify(pedidoRepository).buscarPorIdConBloqueo(2L);
        verify(pagoRepository, never()).existsByPedidoId(anyLong());
        verify(pedidoRepository, never()).save(any(Pedido.class));
        verify(pagoRepository, never()).save(any(Pago.class));
    }

    @Test
    void rechazaEfectivoInsuficienteSinModificarElPedido() {
        Pedido pedido = pedido(EstadoPedido.PENDIENTE_PAGO);
        when(pedidoRepository.buscarPorIdConBloqueo(2L)).thenReturn(Optional.of(pedido));

        assertThrows(ReglaNegocioException.class,
                () -> pagoService.cobrarPedido(dto(MetodoPago.EFECTIVO, new BigDecimal("24.99"))));

        assertEquals(EstadoPedido.PENDIENTE_PAGO, pedido.getEstado());
        verify(pedidoRepository).buscarPorIdConBloqueo(2L);
        verify(pedidoRepository, never()).save(any(Pedido.class));
        verify(pagoRepository, never()).save(any(Pago.class));
    }

    @Test
    void reembolsaPagoCompletadoYActualizaPedidoAunqueEsteEntregado() {
        Pedido pedido = new Pedido();
        pedido.setId(2L);
        pedido.setClienteOMesa("Mesa 4");
        pedido.setEstado(EstadoPedido.ENTREGADO);
        Pago pago = new Pago();
        pago.setId(1L);
        pago.setPedido(pedido);
        pago.setEstado(EstadoPago.COMPLETADO);
        prepararPagoParaReembolso(pago);
        when(pagoRepository.save(pago)).thenReturn(pago);

        var resultado = pagoService.reembolsarPago(1L, "  Reembolso administrativo  ");

        assertEquals(EstadoPago.REEMBOLSADO, pago.getEstado());
        assertEquals(EstadoPedido.REEMBOLSADO, pedido.getEstado());
        assertEquals("Reembolso administrativo", pago.getMotivoReembolso());
        assertNotNull(pago.getFechaReembolso());
        assertEquals(EstadoPago.REEMBOLSADO, resultado.getEstado());
        assertEquals("Reembolso administrativo", resultado.getMotivoReembolso());
        verify(pedidoRepository).save(pedido);
        verify(pagoRepository).save(pago);
        InOrder orden = inOrder(pedidoRepository, pagoRepository);
        orden.verify(pagoRepository).buscarPedidoIdPorPagoId(1L);
        orden.verify(pedidoRepository).buscarPorIdConBloqueo(2L);
        orden.verify(pagoRepository).buscarPorIdConBloqueo(1L);
    }

    @Test
    void rechazaSegundoReembolso() {
        Pago pago = pago(EstadoPago.REEMBOLSADO);
        LocalDateTime fechaReembolsoExistente = LocalDateTime.of(2026, 1, 2, 3, 4);
        pago.setFechaReembolso(fechaReembolsoExistente);
        prepararPagoParaReembolso(pago);

        assertThrows(ReglaNegocioException.class,
                () -> pagoService.reembolsarPago(1L, "Motivo"));

        assertEquals(fechaReembolsoExistente, pago.getFechaReembolso());
        verify(pedidoRepository, never()).save(any(Pedido.class));
        verify(pagoRepository, never()).save(any(Pago.class));
    }

    @Test
    void rechazaPagoCancelado() {
        Pago pago = pago(EstadoPago.CANCELADO);
        prepararPagoParaReembolso(pago);

        assertThrows(ReglaNegocioException.class,
                () -> pagoService.reembolsarPago(1L, "Motivo"));

        assertNull(pago.getFechaReembolso());
        verify(pedidoRepository, never()).save(any(Pedido.class));
        verify(pagoRepository, never()).save(any(Pago.class));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "   "})
    void rechazaMotivoNuloOVacio(String motivo) {
        Pago pago = pago(EstadoPago.COMPLETADO);
        LocalDateTime fechaReembolsoExistente = LocalDateTime.of(2026, 1, 2, 3, 4);
        pago.setFechaReembolso(fechaReembolsoExistente);
        prepararPagoParaReembolso(pago);

        assertThrows(ReglaNegocioException.class,
                () -> pagoService.reembolsarPago(1L, motivo));

        assertEquals(fechaReembolsoExistente, pago.getFechaReembolso());
        verify(pedidoRepository, never()).save(any(Pedido.class));
        verify(pagoRepository, never()).save(any(Pago.class));
    }

    @Test
    void pagoInexistenteProduceNoEncontrado() {
        when(pagoRepository.buscarPedidoIdPorPagoId(404L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> pagoService.reembolsarPago(404L, "Motivo"));

        verify(pedidoRepository, never()).save(any(Pedido.class));
        verify(pagoRepository, never()).save(any(Pago.class));
    }

    private Pago pago(EstadoPago estado) {
        Pedido pedido = new Pedido();
        pedido.setId(2L);
        pedido.setClienteOMesa("Mesa 4");
        Pago pago = new Pago();
        pago.setId(1L);
        pago.setPedido(pedido);
        pago.setEstado(estado);
        return pago;
    }

    private void prepararPagoParaReembolso(Pago pago) {
        when(pagoRepository.buscarPedidoIdPorPagoId(1L)).thenReturn(Optional.of(2L));
        when(pedidoRepository.buscarPorIdConBloqueo(2L))
                .thenReturn(Optional.of(pago.getPedido()));
        when(pagoRepository.buscarPorIdConBloqueo(1L)).thenReturn(Optional.of(pago));
    }

    private Pedido pedido(EstadoPedido estado) {
        Pedido pedido = new Pedido();
        pedido.setId(2L);
        pedido.setClienteOMesa("Mesa 4");
        pedido.setEstado(estado);
        pedido.setTotal(new BigDecimal("25.00"));
        return pedido;
    }

    private ProcesarPagoDTO dto(MetodoPago metodoPago, BigDecimal montoRecibido) {
        ProcesarPagoDTO dto = new ProcesarPagoDTO();
        dto.setPedidoId(2L);
        dto.setMetodoPago(metodoPago);
        dto.setMontoRecibido(montoRecibido);
        return dto;
    }
}
