package org.esfe.easymenu.repositorios;

import jakarta.persistence.LockModeType;
import org.esfe.easymenu.modelos.EstadoPedido;
import org.esfe.easymenu.modelos.Pedido;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pedido p WHERE p.id = :id")
    Optional<Pedido> buscarPorIdConBloqueo(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pedido p WHERE p.codigoCorto = :codigoCorto")
    Optional<Pedido> buscarPorCodigoConBloqueo(@Param("codigoCorto") String codigoCorto);

    Optional<Pedido> findByCodigoCorto(String codigoCorto);

    List<Pedido> findByEstadoInOrderByFechaCreacionAsc(List<EstadoPedido> estados);

    List<Pedido> findByEstadoIn(List<EstadoPedido> estados);

    List<Pedido> findByEstado(EstadoPedido estado);

    @Query("""
            SELECT p.id FROM Pedido p
            WHERE p.estado = :estado
              AND COALESCE(p.fechaHora, p.fechaCreacion) < :limite
            """)
    List<Long> buscarIdsPendientesVencidos(
            @Param("estado") EstadoPedido estado,
            @Param("limite") LocalDateTime limite);

    @Query("SELECT p FROM Pedido p WHERE p.estado = 'PENDIENTE_PAGO' AND p.fechaCreacion <= :limite")
    List<Pedido> buscarPendientesDePagoVencidos(@Param("limite") LocalDateTime limite);

    @Query("SELECT p FROM Pedido p WHERE p.estado IN ('RECIBIDO','EN_PREPARACION','LISTO','ENTREGADO') " +
            "AND p.fechaCreacion BETWEEN :inicio AND :fin")
    List<Pedido> buscarPagadosEntreFechas(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    @Query("SELECT p FROM Pedido p WHERE p.estado = 'REEMBOLSADO' AND p.fechaCreacion BETWEEN :inicio AND :fin")
    List<Pedido> buscarReembolsadosEntreFechas(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);
}