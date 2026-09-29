package org.esfe.easymenu.repositorios;

import org.esfe.easymenu.modelos.DetallePedido;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DetallePedidoRepository extends JpaRepository<DetallePedido, Long> {

    // Útil para HU-1: revisar si un producto tiene pedidos asociados antes de borrarlo físicamente
    List<DetallePedido> findByProductoId(Long productoId);
}
