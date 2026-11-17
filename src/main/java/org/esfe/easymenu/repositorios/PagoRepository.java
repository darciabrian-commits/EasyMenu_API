package org.esfe.easymenu.repositorios;

import org.esfe.easymenu.modelos.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Long> {
    Optional<Pago> findByPedidoId(Long pedidoId);
    boolean existsByPedidoId(Long pedidoId);

    @Query("""
            SELECT COUNT(p.id) AS cantidadCobros,
                   SUM(CASE WHEN p.metodoPago = org.esfe.easymenu.modelos.MetodoPago.EFECTIVO
                            THEN p.montoTotal ELSE 0 END) AS totalCobradoEfectivo,
                   SUM(CASE WHEN p.metodoPago = org.esfe.easymenu.modelos.MetodoPago.TARJETA
                            THEN p.montoTotal ELSE 0 END) AS totalCobradoTarjeta,
                   SUM(CASE WHEN p.metodoPago = org.esfe.easymenu.modelos.MetodoPago.TRANSFERENCIA
                            THEN p.montoTotal ELSE 0 END) AS totalCobradoTransferencia
            FROM Pago p
            WHERE p.fechaHora >= :inicio AND p.fechaHora < :fin
              AND p.estado IN (
                  org.esfe.easymenu.modelos.EstadoPago.COMPLETADO,
                  org.esfe.easymenu.modelos.EstadoPago.REEMBOLSADO
              )
            """)
    ResumenCobrosProjection resumirCobrosEntre(
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin);

    @Query("""
            SELECT COUNT(p.id) AS cantidadReembolsos,
                   SUM(p.montoTotal) AS totalReembolsos
            FROM Pago p
            WHERE p.fechaReembolso >= :inicio AND p.fechaReembolso < :fin
              AND p.estado = org.esfe.easymenu.modelos.EstadoPago.REEMBOLSADO
            """)
    ResumenReembolsosProjection resumirReembolsosEntre(
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin);

    interface ResumenCobrosProjection {
        Long getCantidadCobros();
        BigDecimal getTotalCobradoEfectivo();
        BigDecimal getTotalCobradoTarjeta();
        BigDecimal getTotalCobradoTransferencia();
    }

    interface ResumenReembolsosProjection {
        Long getCantidadReembolsos();
        BigDecimal getTotalReembolsos();
    }
}

