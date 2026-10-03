package org.esfe.easymenu.repositorios;

import org.esfe.easymenu.modelos.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    List<Producto> findByActivoTrue();

    List<Producto> findByActivoTrueAndDisponibleTrue();

    List<Producto> findByActivoTrueAndCategoriaIgnoreCase(
            String categoria
    );

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(
            String nombre,
            Long id
    );
}