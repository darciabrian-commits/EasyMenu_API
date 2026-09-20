package org.esfe.easymenu.modelos;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@Entity
@Table(name = "pedidos")
public class Pedido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_corto", nullable = false, unique = true, length = 10)
    private String codigoCorto;

    private Integer mesa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPedido estado;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_pago")
    private LocalDateTime fechaPago;

    @Column(name = "fecha_entrega")
    private LocalDateTime fechaEntrega;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    // Relación 0..1 con Usuario (El cajero)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario cajero;

    // Relación 1..N con DetallePedido
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetallePedido> detalles = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        if (this.estado == null) {
            this.estado = EstadoPedido.PENDIENTE_PAGO;
        }
    }
}