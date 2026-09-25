package org.esfe.easymenu.controladores;

import org.esfe.easymenu.dtos.JwtResponseDTO;
import org.esfe.easymenu.dtos.LoginRequestDTO;
import org.esfe.easymenu.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO loginDTO) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginDTO.correo(), loginDTO.clave())
            );

            final UserDetails userDetails = userDetailsService.loadUserByUsername(loginDTO.correo());
            final String token = jwtUtil.generarToken(userDetails);

            return ResponseEntity.ok(new JwtResponseDTO(token));

        } catch (Exception e) {
            e.printStackTrace(); // <--- Muestra la causa exacta en IntelliJ
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Error al autenticar: " + e.getMessage());
        }
    }
}