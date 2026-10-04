package org.esfe.easymenu.configuracion;

import org.esfe.easymenu.modelos.RolUsuario;
import org.esfe.easymenu.modelos.Usuario;
import org.esfe.easymenu.repositorios.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        if (usuarioRepository.count() == 0) {
            Usuario admin = new Usuario();
            admin.setNombre("Brian Admin");
            admin.setCorreo("admin@easymenu.com");
            admin.setClave(passwordEncoder.encode("123456")); // Encripta dinámicamente "123456"
            admin.setRol(RolUsuario.ADMINISTRADOR);
            admin.setActivo(true);

            usuarioRepository.save(admin);
            System.out.println("✅ Usuario de prueba creado: admin@easymenu.com / 123456");
        }


    }

}

