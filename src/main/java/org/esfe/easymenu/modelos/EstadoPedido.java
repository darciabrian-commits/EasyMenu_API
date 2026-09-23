package org.esfe.easymenu.modelos;

// Los 8 estados posibles de un pedido, en el orden en que normalmente ocurren.
// Ver el diagrama de clases / flujo del negocio para el detalle de cada transición.
public enum EstadoPedido {
    PENDIENTE_PAGO,
    RECIBIDO,
    EN_PREPARACION,
    LISTO,
    ENTREGADO,
    PENDIENTE_REEMBOLSO,
    REEMBOLSADO,
    CANCELADO
}
