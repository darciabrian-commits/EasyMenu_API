package org.esfe.easymenu.modelos;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
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

    // IMPORTANTE: se llama "usuario", no "cajero" — así lo usan PedidoServiceImpl
    // (pagar, entregar) y todos los documentos ya repartidos al equipo.
    // 0..1 porque el pedido nace anónimo (autoservicio, sin login del cliente).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    @ToString.Exclude
    private Usuario usuario;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<DetallePedido> detalles = new ArrayList<>();

    // Constructor corto: lo usa PedidoServiceImpl.crear() (HU-4)
    public Pedido(String codigoCorto, Integer mesa) {
        this.codigoCorto = codigoCorto;
        this.mesa = mesa;
        this.estado = EstadoPedido.PENDIENTE_PAGO;
        this.fechaCreacion = LocalDateTime.now();
        this.total = BigDecimal.ZERO;
    }

    // Vincula un detalle a este pedido (mantiene sincronizados ambos lados de la relación)
    public void agregarDetalle(DetallePedido detalle) {
        detalle.setPedido(this);
        this.detalles.add(detalle);
    }

    // HU-3/HU-5: recalcula el total sumando cada línea del pedido
    public BigDecimal calcularTotal() {
        BigDecimal suma = BigDecimal.ZERO;
        for (DetallePedido d : detalles) {
            suma = suma.add(d.calcularSubtotal());
        }
        this.total = suma;
        return this.total;
    }

    // HU-4: TTL de 15 minutos para pedidos no pagados
    public boolean haExpirado() {
        if (estado != EstadoPedido.PENDIENTE_PAGO) {
            return false;
        }
        return ChronoUnit.MINUTES.between(fechaCreacion, LocalDateTime.now()) >= 15;
    }

    // ===== Métodos de dominio: cada uno valida que la transición sea válida =====
    // Estos 7 métodos son los que llaman Brian, Karla, Daniela, Reina y Clemente
    // desde sus partes del PedidoServiceImpl. No los borres ni les cambies el nombre.

    // HU-16: el Cajero cobra
    public void pagar(Usuario cajero) {
        if (estado != EstadoPedido.PENDIENTE_PAGO) {
            throw new IllegalStateException("Solo se puede cobrar un pedido Pendiente de Pago.");
        }
        this.estado = EstadoPedido.RECIBIDO;
        this.fechaPago = LocalDateTime.now();
        this.usuario = cajero;
    }

    // HU-7: cocina inicia
    public void iniciarPreparacion() {
        if (estado != EstadoPedido.RECIBIDO) {
            throw new IllegalStateException("Solo se puede iniciar preparación de un pedido Recibido.");
        }
        this.estado = EstadoPedido.EN_PREPARACION;
    }

    // HU-7: cocina termina
    public void marcarListo() {
        if (estado != EstadoPedido.EN_PREPARACION) {
            throw new IllegalStateException("Solo se puede marcar Listo un pedido En Preparación.");
        }
        this.estado = EstadoPedido.LISTO;
    }

    // HU-14: el Cajero entrega
    public void entregar(Usuario cajero) {
        if (estado != EstadoPedido.LISTO) {
            throw new IllegalStateException("Solo se puede entregar un pedido que está Listo.");
        }
        this.estado = EstadoPedido.ENTREGADO;
        this.fechaEntrega = LocalDateTime.now();
        this.usuario = cajero;
    }

    // HU-13: cocina/admin cancela un pedido YA PAGADO por falta de insumo
    public void solicitarReembolso(Producto productoFaltante) {
        if (estado != EstadoPedido.RECIBIDO && estado != EstadoPedido.EN_PREPARACION) {
            throw new IllegalStateException("Solo se puede solicitar reembolso de un pedido Recibido o En Preparación.");
        }
        this.estado = EstadoPedido.PENDIENTE_REEMBOLSO;
        productoFaltante.marcarAgotado(); // cascada automática, HU-13
    }

    // HU-15: el Cajero confirma que ya devolvió el efectivo
    public void confirmarReembolso() {
        if (estado != EstadoPedido.PENDIENTE_REEMBOLSO) {
            throw new IllegalStateException("Solo se puede confirmar reembolso de un pedido Pendiente de Reembolso.");
        }
        this.estado = EstadoPedido.REEMBOLSADO;
    }

    // HU-12: el cliente cancela ANTES de pagar
    public void cancelar() {
        if (estado != EstadoPedido.PENDIENTE_PAGO) {
            throw new IllegalStateException("Solo se puede cancelar un pedido que aún no ha sido pagado.");
        }
        this.estado = EstadoPedido.CANCELADO;
    }
}
