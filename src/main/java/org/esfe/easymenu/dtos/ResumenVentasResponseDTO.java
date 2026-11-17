package org.esfe.easymenu.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ResumenVentasResponseDTO(
        LocalDate fecha,
        long cantidadCobrosRegistrados,
        BigDecimal totalCobradoEfectivo,
        BigDecimal totalCobradoTarjeta,
        BigDecimal totalCobradoTransferencia,
        BigDecimal totalBrutoVentas,
        long cantidadReembolsosRegistrados,
        BigDecimal totalReembolsos,
        BigDecimal totalNetoVentas
) {
}
