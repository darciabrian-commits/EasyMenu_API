package org.esfe.easymenu.servicios.implementaciones;

import org.esfe.easymenu.repositorios.PagoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResumenVentasServiceTest {

    private PagoRepository pagoRepository;
    private PagoServiceImpl pagoService;

    @BeforeEach
    void setUp() {
        pagoRepository = mock(PagoRepository.class);
        pagoService = new PagoServiceImpl();
        ReflectionTestUtils.setField(pagoService, "pagoRepository", pagoRepository);
    }

    @Test
    void resumeCobrosPorMetodoYCalculaBrutoYNeto() {
        LocalDate fecha = LocalDate.of(2026, 11, 16);
        LocalDateTime inicio = fecha.atStartOfDay();
        LocalDateTime fin = fecha.plusDays(1).atStartOfDay();
        var cobros = mock(PagoRepository.ResumenCobrosProjection.class);
        var reembolsos = mock(PagoRepository.ResumenReembolsosProjection.class);
        when(pagoRepository.resumirCobrosEntre(inicio, fin)).thenReturn(cobros);
        when(pagoRepository.resumirReembolsosEntre(inicio, fin)).thenReturn(reembolsos);
        when(cobros.getCantidadCobros()).thenReturn(3L);
        when(cobros.getTotalCobradoEfectivo()).thenReturn(new BigDecimal("10.50"));
        when(cobros.getTotalCobradoTarjeta()).thenReturn(new BigDecimal("20.00"));
        when(cobros.getTotalCobradoTransferencia()).thenReturn(new BigDecimal("30.25"));
        when(reembolsos.getCantidadReembolsos()).thenReturn(1L);
        when(reembolsos.getTotalReembolsos()).thenReturn(new BigDecimal("5.25"));

        var resumen = pagoService.obtenerResumenVentas(fecha);

        assertEquals(fecha, resumen.fecha());
        assertEquals(3L, resumen.cantidadCobrosRegistrados());
        assertEquals(new BigDecimal("10.50"), resumen.totalCobradoEfectivo());
        assertEquals(new BigDecimal("20.00"), resumen.totalCobradoTarjeta());
        assertEquals(new BigDecimal("30.25"), resumen.totalCobradoTransferencia());
        assertEquals(new BigDecimal("60.75"), resumen.totalBrutoVentas());
        assertEquals(1L, resumen.cantidadReembolsosRegistrados());
        assertEquals(new BigDecimal("5.25"), resumen.totalReembolsos());
        assertEquals(new BigDecimal("55.50"), resumen.totalNetoVentas());
        verify(pagoRepository).resumirCobrosEntre(inicio, fin);
        verify(pagoRepository).resumirReembolsosEntre(inicio, fin);
    }

    @Test
    void descuentaReembolsoDelDiaAunqueElCobroOcurrieraEnOtraFecha() {
        LocalDate fechaReembolso = LocalDate.of(2026, 11, 16);
        LocalDateTime inicio = fechaReembolso.atStartOfDay();
        LocalDateTime fin = fechaReembolso.plusDays(1).atStartOfDay();
        var cobros = mock(PagoRepository.ResumenCobrosProjection.class);
        var reembolsos = mock(PagoRepository.ResumenReembolsosProjection.class);
        when(pagoRepository.resumirCobrosEntre(inicio, fin)).thenReturn(cobros);
        when(pagoRepository.resumirReembolsosEntre(inicio, fin)).thenReturn(reembolsos);
        when(reembolsos.getCantidadReembolsos()).thenReturn(1L);
        when(reembolsos.getTotalReembolsos()).thenReturn(new BigDecimal("42.00"));

        var resumen = pagoService.obtenerResumenVentas(fechaReembolso);

        assertEquals(0L, resumen.cantidadCobrosRegistrados());
        assertEquals(new BigDecimal("-42.00"), resumen.totalNetoVentas());
        verify(pagoRepository).resumirReembolsosEntre(inicio, fin);
    }

    @Test
    void diaSinMovimientosDevuelveCeros() {
        LocalDate fecha = LocalDate.of(2026, 11, 16);
        var cobros = mock(PagoRepository.ResumenCobrosProjection.class);
        var reembolsos = mock(PagoRepository.ResumenReembolsosProjection.class);
        when(pagoRepository.resumirCobrosEntre(fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay()))
                .thenReturn(cobros);
        when(pagoRepository.resumirReembolsosEntre(fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay()))
                .thenReturn(reembolsos);

        var resumen = pagoService.obtenerResumenVentas(fecha);

        assertEquals(0L, resumen.cantidadCobrosRegistrados());
        assertEquals(BigDecimal.ZERO, resumen.totalCobradoEfectivo());
        assertEquals(BigDecimal.ZERO, resumen.totalCobradoTarjeta());
        assertEquals(BigDecimal.ZERO, resumen.totalCobradoTransferencia());
        assertEquals(BigDecimal.ZERO, resumen.totalBrutoVentas());
        assertEquals(0L, resumen.cantidadReembolsosRegistrados());
        assertEquals(BigDecimal.ZERO, resumen.totalReembolsos());
        assertEquals(BigDecimal.ZERO, resumen.totalNetoVentas());
    }
}
