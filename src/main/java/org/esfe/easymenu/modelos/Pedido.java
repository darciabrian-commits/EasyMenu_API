package org.esfe.easymenu.modelos;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedidos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
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
            // Genera un código de 6 caracteres aleatorio
            this.codigoCorto = java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        }
        if (this.fechaCreacion == null) {
            this.fechaCreacion = LocalDateTime.now();
        }
    }
}
