package org.esfe.easymenu.servicios.interfaces;

import org.esfe.easymenu.dtos.LoginRequestDTO;
import org.esfe.easymenu.dtos.LoginResponseDTO;

public interface UsuarioService {
    LoginResponseDTO login(LoginRequestDTO request);
}