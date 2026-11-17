package org.esfe.easymenu.repositorios;

import org.esfe.easymenu.modelos.EstadoPago;
import org.esfe.easymenu.modelos.EstadoPedido;
import org.esfe.easymenu.modelos.MetodoPago;
import org.esfe.easymenu.modelos.Pago;
import org.esfe.easymenu.modelos.Pedido;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DataJpaTest
@ActiveProfiles("test")
class PagoRepositoryResumenQueryTest {

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Test
    void ejecutaConsultasConIntervalosExclusivosEstadosYProyecciones() {
        LocalDate diaCobro = LocalDate.of(2026, 11, 16);
        LocalDate diaReembolso = diaCobro.plusDays(1);
        LocalDateTime inicioCobro = diaCobro.atStartOfDay();
        LocalDateTime finCobro = diaReembolso.atStartOfDay();
        LocalDateTime inicioReembolso = diaReembolso.atStartOfDay();
        LocalDateTime finReembolso = diaReembolso.plusDays(1).atStartOfDay();

        Pago efectivoInicio = crearPago(
                new BigDecimal("10.01"), MetodoPago.EFECTIVO, EstadoPago.COMPLETADO);
        efectivoInicio.setFechaHora(inicioCobro);

        Pago tarjetaReembolsada = crearPago(
                new BigDecimal("20.02"), MetodoPago.TARJETA, EstadoPago.REEMBOLSADO);
        tarjetaReembolsada.setFechaHora(inicioCobro.plusHours(1));
        tarjetaReembolsada.setFechaReembolso(inicioReembolso);

        Pago transferenciaFinal = crearPago(
                new BigDecimal("30.03"), MetodoPago.TRANSFERENCIA, EstadoPago.COMPLETADO);
        transferenciaFinal.setFechaHora(finCobro.minusNanos(1_000_000));

        Pago enSiguienteIntervalo = crearPago(
                new BigDecimal("40.04"), MetodoPago.EFECTIVO, EstadoPago.COMPLETADO);
        enSiguienteIntervalo.setFechaHora(finCobro);

        Pago cancelado = crearPago(
                new BigDecimal("50.05"), MetodoPago.TARJETA, EstadoPago.CANCELADO);
        cancelado.setFechaHora(inicioCobro.plusHours(12));
        cancelado.setFechaReembolso(inicioReembolso);

        Pago noReembolsadoConFecha = crearPago(
                new BigDecimal("60.06"), MetodoPago.TRANSFERENCIA, EstadoPago.COMPLETADO);
        noReembolsadoConFecha.setFechaHora(finReembolso);
        noReembolsadoConFecha.setFechaReembolso(inicioReembolso);

        Pago reembolsoAlFinal = crearPago(
                new BigDecimal("70.07"), MetodoPago.EFECTIVO, EstadoPago.REEMBOLSADO);
        reembolsoAlFinal.setFechaReembolso(finReembolso);

        pagoRepository.flush();

        var cobros = pagoRepository.resumirCobrosEntre(inicioCobro, finCobro);
        var reembolsos = pagoRepository.resumirReembolsosEntre(inicioReembolso, finReembolso);

        assertEquals(3L, cobros.getCantidadCobros());
        assertEquals(new BigDecimal("10.01"), cobros.getTotalCobradoEfectivo());
        assertEquals(new BigDecimal("20.02"), cobros.getTotalCobradoTarjeta());
        assertEquals(new BigDecimal("30.03"), cobros.getTotalCobradoTransferencia());
        assertEquals(1L, reembolsos.getCantidadReembolsos());
        assertEquals(new BigDecimal("20.02"), reembolsos.getTotalReembolsos());
    }

    @Test
    void consultasAgregadasDevuelvenConteosCeroYSumasNulasSinRegistros() {
        LocalDate fecha = LocalDate.of(2026, 11, 16);
        LocalDateTime inicio = fecha.atStartOfDay();
        LocalDateTime fin = fecha.plusDays(1).atStartOfDay();

        var cobros = pagoRepository.resumirCobrosEntre(inicio, fin);
        var reembolsos = pagoRepository.resumirReembolsosEntre(inicio, fin);

        assertEquals(0L, cobros.getCantidadCobros());
        assertNull(cobros.getTotalCobradoEfectivo());
        assertNull(cobros.getTotalCobradoTarjeta());
        assertNull(cobros.getTotalCobradoTransferencia());
        assertEquals(0L, reembolsos.getCantidadReembolsos());
        assertNull(reembolsos.getTotalReembolsos());
    }

    private Pago crearPago(BigDecimal monto, MetodoPago metodo, EstadoPago estado) {
        Pedido pedido = new Pedido();
        pedido.setClienteOMesa("Mesa de prueba");
        pedido.setEstado(EstadoPedido.RECIBIDO);
        pedido.setTotal(monto);
        pedido = pedidoRepository.saveAndFlush(pedido);

        Pago pago = new Pago();
        pago.setPedido(pedido);
        pago.setMontoTotal(monto);
        pago.setMetodoPago(metodo);
        pago.setEstado(estado);
        return pagoRepository.saveAndFlush(pago);
    }
}
