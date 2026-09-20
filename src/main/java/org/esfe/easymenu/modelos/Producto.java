package org.esfe.easymenu.modelos;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@Entity
@Table(name = "productos")
public class Producto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 255)
    private String descripcion;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoriaProducto categoria;

    @Column(nullable = false)
    private boolean disponible = true;

    @Column(nullable = false)
    private boolean activo = true;

    public void darDeBaja() {
        this.activo = false;
    }
}