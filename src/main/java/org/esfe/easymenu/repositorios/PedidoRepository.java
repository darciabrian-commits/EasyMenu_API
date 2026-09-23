package org.esfe.easymenu.repositorios;

import org.esfe.easymenu.modelos.EstadoPedido;
import org.esfe.easymenu.modelos.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    // HU-16: el Cajero busca el pedido por su código corto
    Optional<Pedido> findByCodigoCorto(String codigoCorto);

    // HU-6: cocina ve Recibido + En Preparación, ordenados del más antiguo al más reciente
    List<Pedido> findByEstadoInOrderByFechaCreacionAsc(List<EstadoPedido> estados);

    // HU-9: "mis pedidos activos" (aunque el cliente es anónimo, esta consulta
    // le sirve a cualquier pantalla que liste pedidos no finalizados)
    List<Pedido> findByEstadoIn(List<EstadoPedido> estados);

    // HU-14: pedidos Listos, para el panel del Cajero
    List<Pedido> findByEstado(EstadoPedido estado);

    // HU-4: soporte para el job que cancela pedidos vencidos (TTL 15 min)
    @Query("SELECT p FROM Pedido p WHERE p.estado = 'PENDIENTE_PAGO' AND p.fechaCreacion <= :limite")
    List<Pedido> buscarPendientesDePagoVencidos(@Param("limite") LocalDateTime limite);

    // HU-11: pedidos que llegaron a pagarse en un rango de fechas (se les suma al total)
    @Query("SELECT p FROM Pedido p WHERE p.estado IN ('RECIBIDO','EN_PREPARACION','LISTO','ENTREGADO') " +
           "AND p.fechaPago BETWEEN :inicio AND :fin")
    List<Pedido> buscarPagadosEntreFechas(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    // HU-11: pedidos reembolsados en un rango de fechas (se le RESTAN al total)
    @Query("SELECT p FROM Pedido p WHERE p.estado = 'REEMBOLSADO' AND p.fechaPago BETWEEN :inicio AND :fin")
    List<Pedido> buscarReembolsadosEntreFechas(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);
}
