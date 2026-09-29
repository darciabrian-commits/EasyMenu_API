package org.esfe.easymenu.modelos;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "detalles_pedido")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DetallePedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precioUnitario;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(length = 255)
    private String notas;

    public BigDecimal calcularSubtotal() {
        // Si ya hay un precioUnitario guardado
        if (this.precioUnitario != null && this.cantidad != null) {
            return this.precioUnitario.multiply(BigDecimal.valueOf(this.cantidad));
        }
        // Si el precio viene desde la entidad Producto
        if (this.producto != null && this.producto.getPrecio() != null && this.cantidad != null) {
            return this.producto.getPrecio().multiply(BigDecimal.valueOf(this.cantidad));
        }
        return BigDecimal.ZERO;
    }

}
