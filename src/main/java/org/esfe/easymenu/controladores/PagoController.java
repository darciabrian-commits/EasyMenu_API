package org.esfe.easymenu.controladores;

import jakarta.validation.Valid;
import org.esfe.easymenu.dtos.PagoDTO;
import org.esfe.easymenu.dtos.ProcesarPagoDTO;
import org.esfe.easymenu.servicios.interfaces.IPagoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    @Autowired
    private IPagoService pagoService;

    @PostMapping("/cobrar")
    public ResponseEntity<PagoDTO> cobrarPedido(@Valid @RequestBody ProcesarPagoDTO procesarPagoDTO) {
        return new ResponseEntity<>(pagoService.cobrarPedido(procesarPagoDTO), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<PagoDTO>> listarTodos() {
        return ResponseEntity.ok(pagoService.obtenerTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PagoDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(pagoService.obtenerPorId(id));
    }

    @GetMapping("/pedido/{pedidoId}")
    public ResponseEntity<PagoDTO> obtenerPorPedidoId(@PathVariable Long pedidoId) {
        return ResponseEntity.ok(pagoService.obtenerPorPedidoId(pedidoId));
    }

    @PatchMapping("/{id}/reembolsar")
    public ResponseEntity<PagoDTO> reembolsar(@PathVariable Long id, @RequestParam String motivo) {
        return ResponseEntity.ok(pagoService.reembolsarPago(id, motivo));
    }
}


