package org.esfe.easymenu.modelos;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "productos")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
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

    // Constructor corto: lo usa ProductoServiceImpl.crear() (HU-1)
    public Producto(String nombre, String descripcion, BigDecimal precio, CategoriaProducto categoria) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precio = precio;
        this.categoria = categoria;
        this.disponible = true;
        this.activo = true;
    }

    // ===== Métodos de dominio (HU-1, HU-8) =====

    public void marcarAgotado() {
        this.disponible = false;
    }

    public void marcarDisponible() {
        this.disponible = true;
    }

    public void darDeBaja() {
        this.activo = false;
    }

    // HU-1: lo usa ProductoServiceImpl.actualizar()
    public void actualizarDatos(String nombre, String descripcion, BigDecimal precio, CategoriaProducto categoria) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precio = precio;
        this.categoria = categoria;
    }
}
