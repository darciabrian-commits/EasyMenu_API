package org.esfe.easymenu.controladores;

import jakarta.validation.Valid;
import org.esfe.easymenu.dtos.PedidoResponseDTO;
import org.esfe.easymenu.modelos.EstadoPedido;
import org.esfe.easymenu.servicios.interfaces.PedidoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    @Autowired
    private PedidoService pedidoService;

    @PostMapping
    public ResponseEntity<PedidoResponseDTO> crear(@Valid @RequestBody PedidoResponseDTO pedidoDTO) {
        return new ResponseEntity<>(pedidoService.crearPedido(pedidoDTO), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<PedidoResponseDTO>> listarTodos() {
        return ResponseEntity.ok(pedidoService.obtenerTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PedidoResponseDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(pedidoService.obtenerPorId(id));
    }

    @GetMapping("/estado/{estado}")
    public ResponseEntity<List<PedidoResponseDTO>> obtenerPorEstado(@PathVariable EstadoPedido estado) {
        return ResponseEntity.ok(pedidoService.obtenerPorEstado(estado));
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<PedidoResponseDTO> cambiarEstado(@PathVariable Long id, @RequestParam EstadoPedido estado) {
        return ResponseEntity.ok(pedidoService.cambiarEstado(id, estado));
    }

    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<Void> cancelar(@PathVariable Long id) {
        pedidoService.cancelarPedido(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/expirar")
    public ResponseEntity<Void> expirarPendientes(@RequestParam(defaultValue = "30") int minutos) {
        pedidoService.expirarPedidosPendientes(minutos);
        return ResponseEntity.noContent().build();
    }
}


