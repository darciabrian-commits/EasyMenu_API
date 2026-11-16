package org.esfe.easymenu.servicios.implementaciones;

import org.esfe.easymenu.dtos.CrearUsuarioRequestDTO;
import org.esfe.easymenu.dtos.LoginRequestDTO;
import org.esfe.easymenu.dtos.LoginResponseDTO;
import org.esfe.easymenu.dtos.UsuarioResponseDTO;
import org.esfe.easymenu.excepcion.CredencialesInvalidasException;
import org.esfe.easymenu.excepcion.CorreoDuplicadoException;
import org.esfe.easymenu.excepcion.ReglaNegocioException;
import org.esfe.easymenu.excepcion.RecursoNoEncontradoException;
import org.esfe.easymenu.modelos.RolUsuario;
import org.esfe.easymenu.modelos.Usuario;
import org.esfe.easymenu.repositorios.UsuarioRepository;
import org.esfe.easymenu.servicios.interfaces.UsuarioService;
import org.esfe.easymenu.validaciones.ClaveBcryptValidaValidator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioServiceImpl(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponseDTO login(
            LoginRequestDTO request
    ) {

        Usuario usuario = usuarioRepository
                .findByCorreo(request.correo())
                .orElseThrow(
                        () -> new CredencialesInvalidasException(
                                "Correo o contraseña incorrectos"
                        )
                );

        if (
                !usuario.isActivo()
                        ||
                        !passwordEncoder.matches(
                                request.clave(),
                                usuario.getClave()
                        )
        ) {

            throw new CredencialesInvalidasException(
                    "Correo o contraseña incorrectos"
            );
        }

        return new LoginResponseDTO(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getCorreo(),
                usuario.getRol()
        );
    }

    @Override
    @Transactional
    public UsuarioResponseDTO crear(CrearUsuarioRequestDTO request) {
        if (request.rol() == RolUsuario.ADMINISTRADOR) {
            throw new ReglaNegocioException("No se permite crear usuarios ADMINISTRADOR desde este módulo");
        }
        if (!ClaveBcryptValidaValidator.admite(request.clave())) {
            throw new ReglaNegocioException("La contraseña no puede superar los 72 bytes UTF-8");
        }

        String correo = request.correo().trim().toLowerCase(Locale.ROOT);
        if (usuarioRepository.existsByCorreoIgnoreCase(correo)) {
            throw new CorreoDuplicadoException();
        }

        Usuario usuario = new Usuario(
                request.nombre().trim(),
                correo,
                passwordEncoder.encode(request.clave()),
                request.rol()
        );
        try {
            return convertir(usuarioRepository.saveAndFlush(usuario));
        } catch (DataIntegrityViolationException e) {
            throw new CorreoDuplicadoException();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioResponseDTO> listar() {
        return usuarioRepository.findAll().stream()
                .map(UsuarioServiceImpl::convertir)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioResponseDTO obtenerPorId(Long id) {
        return usuarioRepository.findById(id)
                .map(UsuarioServiceImpl::convertir)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado con ID: " + id));
    }

    @Override
    @Transactional
    public UsuarioResponseDTO desactivar(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado con ID: " + id));

        if (usuario.getRol() == RolUsuario.ADMINISTRADOR) {
            throw new ReglaNegocioException("No se permite desactivar usuarios ADMINISTRADOR desde este módulo");
        }

        usuario.setActivo(false);
        return convertir(usuarioRepository.save(usuario));
    }

    private static UsuarioResponseDTO convertir(Usuario usuario) {
        return new UsuarioResponseDTO(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getCorreo(),
                usuario.getRol(),
                usuario.isActivo()
        );
    }
}