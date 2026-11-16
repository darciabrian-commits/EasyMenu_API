package org.esfe.easymenu.servicios.implementaciones;

import org.esfe.easymenu.excepcion.ReglaNegocioException;
import org.esfe.easymenu.modelos.EstadoPedido;
import org.esfe.easymenu.modelos.Pedido;
import org.esfe.easymenu.repositorios.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class PedidoServiceImplTest {

    private PedidoRepository pedidoRepository;
    private PedidoServiceImpl pedidoService;

    @BeforeEach
    void setUp() {
        pedidoRepository = mock(PedidoRepository.class);
        pedidoService = new PedidoServiceImpl();
        ReflectionTestUtils.setField(pedidoService, "pedidoRepository", pedidoRepository);
    }

    @Test
    void expiraPendientesVencidosYConservaRecientes() {
        Pedido vencido = pedido(EstadoPedido.PENDIENTE_PAGO, LocalDateTime.now().minusMinutes(16));
        Pedido reciente = pedido(EstadoPedido.PENDIENTE_PAGO, LocalDateTime.now().minusMinutes(14));
        when(pedidoRepository.findByEstado(EstadoPedido.PENDIENTE_PAGO))
                .thenReturn(List.of(vencido, reciente));

        pedidoService.expirarPedidosPendientes(15);

        assertEquals(EstadoPedido.EXPIRADO, vencido.getEstado());
        assertEquals(EstadoPedido.PENDIENTE_PAGO, reciente.getEstado());
        verify(pedidoRepository).save(vencido);
        verify(pedidoRepository, never()).save(reciente);
    }

    @Test
    void noModificaPedidosQueNoEstanPendientesDePago() {
        Pedido cancelado = pedido(EstadoPedido.CANCELADO, LocalDateTime.now().minusMinutes(20));
        when(pedidoRepository.findByEstado(EstadoPedido.PENDIENTE_PAGO))
                .thenReturn(List.of(cancelado));

        pedidoService.expirarPedidosPendientes(15);

        assertEquals(EstadoPedido.CANCELADO, cancelado.getEstado());
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

    private Pedido pedido(EstadoPedido estado, LocalDateTime fechaHora) {
        Pedido pedido = new Pedido();
        pedido.setEstado(estado);
        pedido.setFechaHora(fechaHora);
        return pedido;
    }
}
