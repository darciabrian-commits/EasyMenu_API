package org.esfe.easymenu.configuracion;

import org.esfe.easymenu.servicios.interfaces.PedidoService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;



@Component
public class PedidoScheduler {

    private final PedidoService pedidoService;

    public PedidoScheduler(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @Scheduled(fixedRate = 60000)
    public void expirarPedidosPendientes() {
        pedidoService.expirarPedidosPendientes(15);
    }
}