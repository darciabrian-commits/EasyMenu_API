package org.esfe.easymenu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// @SpringBootApplication le dice a Spring que esta es la clase principal:
// activa la configuración automática, el escaneo de componentes (busca
// @RestController, @Service, @Repository dentro del paquete org.esfe.easymenu
// y sus subpaquetes) y la configuración de Spring Boot en general.
@SpringBootApplication
public class EasymenuApiApplication {

    public static void main(String[] args) {
        // Este es el punto de entrada: aquí arranca todo el servidor embebido
        // (Tomcat) y deja la API corriendo, lista para recibir peticiones.
        SpringApplication.run(EasymenuApiApplication.class, args);
    }

}
