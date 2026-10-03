package org.esfe.easymenu.servicios.implementaciones;

import org.esfe.easymenu.dtos.ProductoRequestDTO;
import org.esfe.easymenu.dtos.ProductoResponseDTO;
import org.esfe.easymenu.excepcion.RecursoNoEncontradoException;
import org.esfe.easymenu.excepcion.ReglaNegocioException;
import org.esfe.easymenu.modelos.Producto;
import org.esfe.easymenu.repositorios.ProductoRepository;
import org.esfe.easymenu.servicios.interfaces.ProductoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductoServiceImpl implements ProductoService {

    private final ProductoRepository productoRepository;

    public ProductoServiceImpl(
            ProductoRepository productoRepository
    ) {
        this.productoRepository = productoRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoResponseDTO> obtenerTodos() {

        return productoRepository
                .findByActivoTrue()
                .stream()
                .map(this::convertirADto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoResponseDTO> obtenerDisponibles() {

        return productoRepository
                .findByActivoTrueAndDisponibleTrue()
                .stream()
                .map(this::convertirADto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoResponseDTO> obtenerPorCategoria(
            String categoria
    ) {

        return productoRepository
                .findByActivoTrueAndCategoriaIgnoreCase(categoria)
                .stream()
                .map(this::convertirADto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductoResponseDTO obtenerPorId(Long id) {

        Producto producto = buscarProductoActivo(id);

        return convertirADto(producto);
    }

    @Override
    @Transactional
    public ProductoResponseDTO crear(
            ProductoRequestDTO dto
    ) {

        if (productoRepository.existsByNombreIgnoreCase(dto.nombre())) {

            throw new ReglaNegocioException(
                    "Ya existe un producto registrado con el nombre: "
                            + dto.nombre()
            );
        }

        Producto producto = new Producto();

        producto.setNombre(dto.nombre());
        producto.setDescripcion(dto.descripcion());
        producto.setPrecio(dto.precio());
        producto.setCategoria(dto.categoria());
        producto.setDisponible(true);
        producto.setActivo(true);

        Producto guardado =
                productoRepository.save(producto);

        return convertirADto(guardado);
    }

    @Override
    @Transactional
    public ProductoResponseDTO actualizar(
            Long id,
            ProductoRequestDTO dto
    ) {

        Producto producto = buscarProductoActivo(id);

        if (productoRepository
                .existsByNombreIgnoreCaseAndIdNot(
                        dto.nombre(),
                        id
                )) {

            throw new ReglaNegocioException(
                    "Ya existe otro producto registrado con el nombre: "
                            + dto.nombre()
            );
        }

        producto.actualizarDatos(
                dto.nombre(),
                dto.descripcion(),
                dto.precio(),
                dto.categoria()
        );

        Producto actualizado =
                productoRepository.save(producto);

        return convertirADto(actualizado);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {

        Producto producto = buscarProductoActivo(id);

        producto.darDeBaja();

        productoRepository.save(producto);
    }

    @Override
    @Transactional
    public ProductoResponseDTO cambiarEstadoDisponibilidad(
            Long id,
            Boolean disponible
    ) {

        Producto producto = buscarProductoActivo(id);

        if (Boolean.TRUE.equals(disponible)) {
            producto.marcarDisponible();
        } else {
            producto.marcarAgotado();
        }

        Producto actualizado =
                productoRepository.save(producto);

        return convertirADto(actualizado);
    }

    private Producto buscarProductoActivo(Long id) {

        Producto producto = productoRepository
                .findById(id)
                .orElseThrow(
                        () -> new RecursoNoEncontradoException(
                                "Producto no encontrado con el ID: " + id
                        )
                );

        if (!Boolean.TRUE.equals(producto.getActivo())) {

            throw new RecursoNoEncontradoException(
                    "Producto no encontrado con el ID: " + id
            );
        }

        return producto;
    }

    private ProductoResponseDTO convertirADto(
            Producto producto
    ) {

        return new ProductoResponseDTO(
                producto.getId(),
                producto.getNombre(),
                producto.getDescripcion(),
                producto.getPrecio(),
                producto.getCategoria(),
                producto.getDisponible(),
                producto.getActivo()
        );
    }
}