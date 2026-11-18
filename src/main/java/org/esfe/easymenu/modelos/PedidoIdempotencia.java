package org.esfe.easymenu.modelos;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

@Entity
@Table(name = "pedido_idempotencia")
@Check(constraints = """
        (estado = 'PROCESANDO' AND pedido_id IS NULL AND respuesta_json IS NULL
            AND fecha_completado IS NULL)
        OR
        (estado = 'COMPLETADO' AND pedido_id IS NOT NULL AND respuesta_json IS NOT NULL
            AND fecha_completado IS NOT NULL)
        """)
@Getter
@Setter
@NoArgsConstructor
public class PedidoIdempotencia {

    @Id
    @Column(name = "clave", nullable = false, length = 128)
    private String clave;

    @Column(name = "hash_solicitud", nullable = false, length = 32)
    private byte[] hashSolicitud;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoIdempotencia estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    @Lob
    @Nationalized
    @Column(name = "respuesta_json")
    private String respuestaJson;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_completado")
    private LocalDateTime fechaCompletado;

    @PrePersist
    void prePersist() {
        if (fechaCreacion == null) {
            fechaCreacion = LocalDateTime.now();
        }
    }
}
