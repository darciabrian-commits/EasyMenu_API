package org.esfe.easymenu.configuracion;

import org.esfe.easymenu.servicios.interfaces.PedidoService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PedidoSchedulerTest {

    @Test
    void ejecutaCadaMinutoYSolicitaExpiracionDeQuinceMinutos() throws Exception {
        PedidoService pedidoService = mock(PedidoService.class);
        PedidoScheduler scheduler = new PedidoScheduler(pedidoService);

        scheduler.expirarPedidosPendientes();

        verify(pedidoService).expirarPedidosPendientes(15);
        Scheduled scheduled = PedidoScheduler.class
                .getMethod("expirarPedidosPendientes")
                .getAnnotation(Scheduled.class);
        assertEquals(60000L, scheduled.fixedRate());
    }
}
