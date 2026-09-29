package org.esfe.easymenu.repositorios;

import org.esfe.easymenu.modelos.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Long> {
    Optional<Pago> findByPedidoId(Long pedidoId);
    boolean existsByPedidoId(Long pedidoId);
}

