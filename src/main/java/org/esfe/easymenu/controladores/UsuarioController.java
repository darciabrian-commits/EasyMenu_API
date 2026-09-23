package org.esfe.easymenu.controladores;

import jakarta.validation.Valid;
import org.esfe.easymenu.dtos.LoginRequestDTO;
import org.esfe.easymenu.dtos.LoginResponseDTO;
import org.esfe.easymenu.servicios.interfaces.UsuarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(usuarioService.login(request));
    }
}