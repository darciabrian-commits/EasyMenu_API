package org.esfe.easymenu.dtos;

public record JwtResponseDTO(
        String token,
        String tipo,
        String rol
) {

    public JwtResponseDTO(String token, String rol) {
        this(token, "Bearer", rol);
    }
}