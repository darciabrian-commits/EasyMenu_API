package org.esfe.easymenu.controladores;

import jakarta.validation.Valid;
import org.esfe.easymenu.dtos.ProductoRequestDTO;
import org.esfe.easymenu.dtos.ProductoResponseDTO;
import org.esfe.easymenu.servicios.interfaces.ProductoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GetMapping
    public ResponseEntity<List<ProductoResponseDTO>> listarTodos() {
        return ResponseEntity.ok(productoService.obtenerTodos());
    }

    @GetMapping("/disponibles")
    public ResponseEntity<List<ProductoResponseDTO>> listarDisponibles() {
        return ResponseEntity.ok(productoService.obtenerDisponibles());
    }

    @GetMapping("/categoria/{categoria}")
    public ResponseEntity<List<ProductoResponseDTO>> listarPorCategoria(
            @PathVariable String categoria
    ) {
        return ResponseEntity.ok(
                productoService.obtenerPorCategoria(categoria)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductoResponseDTO> obtenerPorId(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                productoService.obtenerPorId(id)
        );
    }

    @PostMapping
    public ResponseEntity<ProductoResponseDTO> crear(
            @Valid @RequestBody ProductoRequestDTO productoDTO
    ) {
        return new ResponseEntity<>(
                productoService.crear(productoDTO),
                HttpStatus.CREATED
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductoResponseDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ProductoRequestDTO productoDTO
    ) {
        return ResponseEntity.ok(
                productoService.actualizar(id, productoDTO)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            @PathVariable Long id
    ) {
        productoService.eliminar(id);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/disponibilidad")
    public ResponseEntity<ProductoResponseDTO> cambiarDisponibilidad(
            @PathVariable Long id,
            @RequestParam Boolean disponible
    ) {
        return ResponseEntity.ok(
                productoService.cambiarEstadoDisponibilidad(
                        id,
                        disponible
                )
        );
    }
}