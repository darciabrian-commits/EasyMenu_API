package org.esfe.easymenu.repositorios;

import org.esfe.easymenu.modelos.CategoriaProducto;
import org.esfe.easymenu.modelos.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    // HU-2: el cliente solo ve productos activos Y disponibles
    List<Producto> findByActivoTrueAndDisponibleTrue();

    // HU-2: filtrar por categoría (solo activos y disponibles)
    List<Producto> findByActivoTrueAndDisponibleTrueAndCategoria(CategoriaProducto categoria);

    // HU-2: buscar por nombre (contiene el texto, sin importar mayúsc/minúsc)
    List<Producto> findByActivoTrueAndDisponibleTrueAndNombreContainingIgnoreCase(String nombre);

    // HU-1: lista completa para el panel de administración (incluye agotados, no incluye dados de baja)
    List<Producto> findByActivoTrue();
}
