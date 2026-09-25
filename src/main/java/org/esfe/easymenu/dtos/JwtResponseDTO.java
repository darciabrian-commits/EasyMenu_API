package org.esfe.easymenu.dtos;

public record JwtResponseDTO(
        String token,
        String tipo
) {
    public JwtResponseDTO(String token) {
        this(token, "Bearer");
    }
}