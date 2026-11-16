package org.esfe.easymenu.configuracion;

import org.esfe.easymenu.modelos.RolUsuario;
import org.esfe.easymenu.modelos.Usuario;
import org.esfe.easymenu.repositorios.UsuarioRepository;
import org.esfe.easymenu.validaciones.ClaveBcryptValidaValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final String correoAdministradorInicial;
    private final String claveAdministradorInicial;

    public DataInitializer(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            @Value("${easymenu.initial-admin.email:${EASYMENU_INITIAL_ADMIN_EMAIL:}}")
            String correoAdministradorInicial,
            @Value("${easymenu.initial-admin.password:${EASYMENU_INITIAL_ADMIN_PASSWORD:}}")
            String claveAdministradorInicial
    ) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.correoAdministradorInicial = correoAdministradorInicial;
        this.claveAdministradorInicial = claveAdministradorInicial;
    }

    @Override
    public void run(String... args) {
        boolean correoConfigurado = StringUtils.hasText(correoAdministradorInicial);
        boolean claveConfigurada = StringUtils.hasText(claveAdministradorInicial);

        if (correoConfigurado != claveConfigurada) {
            throw new IllegalStateException(
                    "Debe configurar tanto easymenu.initial-admin.email como easymenu.initial-admin.password"
            );
        }

        if (!correoConfigurado || usuarioRepository.count() != 0) {
            return;
        }

        if (!ClaveBcryptValidaValidator.admite(claveAdministradorInicial)) {
            throw new IllegalStateException("La contraseña del administrador inicial supera el límite de BCrypt");
        }

        Usuario administrador = new Usuario(
                "Administrador inicial",
                correoAdministradorInicial.trim().toLowerCase(Locale.ROOT),
                passwordEncoder.encode(claveAdministradorInicial),
                RolUsuario.ADMINISTRADOR
        );
        usuarioRepository.save(administrador);
    }
}
