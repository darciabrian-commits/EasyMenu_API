package org.esfe.easymenu.servicios.interfaces;

import org.esfe.easymenu.dtos.LoginRequestDTO;
import org.esfe.easymenu.dtos.LoginResponseDTO;
import org.esfe.easymenu.dtos.CrearUsuarioRequestDTO;
import org.esfe.easymenu.dtos.UsuarioResponseDTO;

import java.util.List;

public interface UsuarioService {
    LoginResponseDTO login(LoginRequestDTO request);

    UsuarioResponseDTO crear(CrearUsuarioRequestDTO request);

    List<UsuarioResponseDTO> listar();

    UsuarioResponseDTO obtenerPorId(Long id);

    UsuarioResponseDTO desactivar(Long id);
}