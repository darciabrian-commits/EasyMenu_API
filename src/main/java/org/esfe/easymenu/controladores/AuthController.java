package org.esfe.easymenu.controladores;

import org.esfe.easymenu.dtos.JwtResponseDTO;
import org.esfe.easymenu.dtos.LoginRequestDTO;
import org.esfe.easymenu.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;

    public AuthController(
            AuthenticationManager authenticationManager,
            UserDetailsService userDetailsService,
            JwtUtil jwtUtil
    ) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequestDTO loginDTO
    ) {

        try {

            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginDTO.correo(),
                            loginDTO.clave()
                    )
            );

            UserDetails userDetails =
                    userDetailsService.loadUserByUsername(
                            loginDTO.correo()
                    );

            String token =
                    jwtUtil.generarToken(userDetails);

            String rol = userDetails
                    .getAuthorities()
                    .stream()
                    .findFirst()
                    .map(authority ->
                            authority
                                    .getAuthority()
                                    .replace("ROLE_", "")
                    )
                    .orElse("");

            return ResponseEntity.ok(
                    new JwtResponseDTO(
                            token,
                            rol
                    )
            );

        } catch (AuthenticationException e) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Credenciales incorrectas");
        }
    }
}