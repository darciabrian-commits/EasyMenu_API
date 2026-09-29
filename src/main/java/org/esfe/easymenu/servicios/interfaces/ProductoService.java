package org.esfe.easymenu.servicios.interfaces;

import org.esfe.easymenu.dtos.ProductoDTO;
import org.esfe.easymenu.dtos.ProductRequestDTO;

import java.util.List;

public interface ProductoService {
    List<ProductoDTO> obtenerTodos();
    List<ProductoDTO> obtenerDisponibles();
    List<ProductoDTO> obtenerPorCategoria(String categoria);
    ProductoDTO obtenerPorId(Long id);
    ProductoDTO crear(ProductRequestDTO dto);
    ProductoDTO actualizar(Long id, ProductRequestDTO dto);
    void eliminar(Long id);

    ProductoDTO crear(ProductoDTO dto);
    ProductoDTO actualizar(Long id, ProductoDTO dto);
    void cambiarEstadoDisponibilidad(Long id, Boolean disponible);
}