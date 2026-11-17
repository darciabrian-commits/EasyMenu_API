package org.esfe.easymenu.repositorios;

import org.esfe.easymenu.modelos.PedidoIdempotencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PedidoIdempotenciaRepository extends JpaRepository<PedidoIdempotencia, String> {
}
