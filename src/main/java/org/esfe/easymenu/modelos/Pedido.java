package org.esfe.easymenu.modelos;

import jakarta.persistence.*;
import lombok.*;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedidos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Pedido {

    private static final String ALFABETO_CODIGO_CORTO = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final SecureRandom GENERADOR_ALEATORIO = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_corto", nullable = false, unique = true, length = 10)
    @Size(max = 10)
    private String codigoCorto;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "cliente_o_mesa", nullable = false, length = 100)
    private String clienteOMesa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoPedido estado = EstadoPedido.PENDIENTE;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDateTime fechaHora;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetallePedido> detalles = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (this.codigoCorto == null || this.codigoCorto.isBlank()) {
            this.codigoCorto = generarCodigoCorto();
        }
        if (this.fechaCreacion == null) {
            this.fechaCreacion = LocalDateTime.now();
        }
        if (this.fechaHora == null) {
            this.fechaHora = LocalDateTime.now();
        }
    }

    public static String generarCodigoCorto() {
        StringBuilder codigo = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            codigo.append(ALFABETO_CODIGO_CORTO.charAt(
                    GENERADOR_ALEATORIO.nextInt(ALFABETO_CODIGO_CORTO.length())));
        }
        return codigo.toString();
    }

    // Método helper para resolver la inconsistencia con el servicio
    public void agregarDetalle(DetallePedido detalle) {
        detalles.add(detalle);
        detalle.setPedido(this);
    }
}