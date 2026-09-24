package org.esfe.easymenu.servicios.implementaciones;

import org.esfe.easymenu.dtos.ProductRequestDTO;
import org.esfe.easymenu.dtos.ProductoDTO;
import org.esfe.easymenu.excepcion.RecursoNoEncontradoException;
import org.esfe.easymenu.excepcion.ReglaNegocioException;
import org.esfe.easymenu.modelos.Producto;
import org.esfe.easymenu.repositorios.ProductoRepository;
import org.esfe.easymenu.servicios.interfaces.ProductoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductoServiceImpl implements ProductoService {

    @Autowired
    private ProductoRepository productoRepository;

    @Override
    public List<ProductoDTO> obtenerTodos() {
        return productoRepository.findAll().stream()
                .map(this::convertirADto)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductoDTO> obtenerDisponibles() {
        return productoRepository.findByDisponibleTrue().stream()
                .map(this::convertirADto)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductoDTO> obtenerPorCategoria(String categoria) {
        return List.of();
    }

    @Override
    public ProductoDTO obtenerPorId(Long id) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto no encontrado con el ID: " + id));
        return convertirADto(producto);
    }

    @Override
    public ProductoDTO crear(ProductRequestDTO dto) {
        return null;
    }

    @Override
    public ProductoDTO actualizar(Long id, ProductRequestDTO dto) {
        return null;
    }

    @Override
    public void eliminar(Long id) {

    }

    @Override
    public ProductoDTO crear(ProductoDTO dto) {
        // Criterio de aceptación HU-1: No registrar productos duplicados
        if (productoRepository.existsByNombreIgnoreCase(dto.getNombre())) {
            throw new ReglaNegocioException("Ya existe un producto registrado con el nombre: " + dto.getNombre());
        }

        Producto producto = convertirAEntidad(dto);
        Producto guardado = productoRepository.save(producto);
        return convertirADto(guardado);
    }

    @Override
    public ProductoDTO actualizar(Long id, ProductoDTO dto) {
        // Verificar que el producto exista
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto no encontrado con el ID: " + id));

        // Criterio de aceptación HU-8: Evitar que al actualizar use un nombre de otro producto existente
        if (productoRepository.existsByNombreIgnoreCaseAndIdNot(dto.getNombre(), id)) {
            throw new ReglaNegocioException("Ya existe otro producto registrado con el nombre: " + dto.getNombre());
        }

        producto.setNombre(dto.getNombre());
        producto.setDescripcion(dto.getDescripcion());
        producto.setPrecio(dto.getPrecio());
        producto.setDisponible(dto.getDisponible() != null ? dto.getDisponible() : producto.getDisponible());
        producto.setCategoria(dto.getCategoria());

        return convertirADto(productoRepository.save(producto));
    }

    @Override
    public void cambiarEstadoDisponibilidad(Long id, Boolean disponible) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto no encontrado con el ID: " + id));

        producto.setDisponible(disponible);
        productoRepository.save(producto);
    }

    // --- Métodos Privados de Mapeo (DTO <-> Entidad) ---

    private ProductoDTO convertirADto(Producto producto) {
        ProductoDTO dto = new ProductoDTO();
        dto.setId(producto.getId());
        dto.setNombre(producto.getNombre());
        dto.setDescripcion(producto.getDescripcion());
        dto.setPrecio(producto.getPrecio());
        dto.setDisponible(producto.getDisponible());
        dto.setCategoria(producto.getCategoria());
        return dto;
    }

    private Producto convertirAEntidad(ProductoDTO dto) {
        Producto producto = new Producto();
        producto.setNombre(dto.getNombre());
        producto.setDescripcion(dto.getDescripcion());
        producto.setPrecio(dto.getPrecio());
        producto.setDisponible(dto.getDisponible() != null ? dto.getDisponible() : true);
        producto.setCategoria(dto.getCategoria());
        return producto;
    }
}
