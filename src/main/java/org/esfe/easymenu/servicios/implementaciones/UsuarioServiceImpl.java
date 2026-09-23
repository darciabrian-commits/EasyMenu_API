package org.esfe.easymenu.servicios.implementaciones;

import org.esfe.easymenu.dtos.LoginRequestDTO;
import org.esfe.easymenu.dtos.LoginResponseDTO;
import org.esfe.easymenu.excepcion.CredencialesInvalidasException;
import org.esfe.easymenu.modelos.Usuario;
import org.esfe.easymenu.repositorios.UsuarioRepository;
import org.esfe.easymenu.servicios.interfaces.UsuarioService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioServiceImpl(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponseDTO login(LoginRequestDTO request) {
        Usuario usuario = usuarioRepository.findByCorreo(request.correo())
                .orElseThrow(() -> new CredencialesInvalidasException("Correo o contraseña incorrectos"));

        if (!usuario.getClave().equals(request.clave()) || !usuario.isActivo()) {
            throw new CredencialesInvalidasException("Correo o contraseña incorrectos");
        }

        return new LoginResponseDTO(usuario.getId(), usuario.getNombre(), usuario.getCorreo(), usuario.getRol());
    }


}