package org.esfe.easymenu.controladores;

import org.esfe.easymenu.dtos.ProductoDTO;
import org.esfe.easymenu.servicios.interfaces.ProductoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    @Autowired
    private ProductoService productoService;

    @GetMapping
    public ResponseEntity<List<ProductoDTO>> listarTodos() {
        return ResponseEntity.ok(productoService.obtenerTodos());
    }

    @GetMapping("/disponibles")
    public ResponseEntity<List<ProductoDTO>> listarDisponibles() {
        return ResponseEntity.ok(productoService.obtenerDisponibles());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductoDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(productoService.obtenerPorId(id));
    }

    @PostMapping
    public ResponseEntity<ProductoDTO> crear(@Valid @RequestBody ProductoDTO productoDTO) {
        return new ResponseEntity<>(productoService.crear(productoDTO), HttpStatus.CREATED);
    }
    @PutMapping("/{id}")
    public ResponseEntity<ProductoDTO> actualizar(@PathVariable Long id, @RequestBody ProductoDTO productoDTO) {
        return ResponseEntity.ok(productoService.actualizar(id, productoDTO));
    }

    @PatchMapping("/{id}/disponibilidad")
    public ResponseEntity<Void> cambiarDisponibilidad(@PathVariable Long id, @RequestParam Boolean disponible) {
        productoService.cambiarEstadoDisponibilidad(id, disponible);
        return ResponseEntity.noContent().build();
    }
}

