package org.esfe.easymenu.servicios.interfaces;

import org.esfe.easymenu.dtos.ProductoRequestDTO;
import org.esfe.easymenu.dtos.ProductoResponseDTO;

import java.util.List;

public interface ProductoService {

    List<ProductoResponseDTO> obtenerTodos();

    List<ProductoResponseDTO> obtenerDisponibles();

    List<ProductoResponseDTO> obtenerPorCategoria(
            String categoria
    );

    ProductoResponseDTO obtenerPorId(Long id);

    ProductoResponseDTO crear(
            ProductoRequestDTO dto
    );

    ProductoResponseDTO actualizar(
            Long id,
            ProductoRequestDTO dto
    );

    void eliminar(Long id);

    ProductoResponseDTO cambiarEstadoDisponibilidad(
            Long id,
            Boolean disponible
    );
}