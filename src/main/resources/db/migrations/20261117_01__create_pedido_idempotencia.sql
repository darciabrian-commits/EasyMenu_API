/*
 * SQL Server migration: persistent Idempotency-Key support for POST /api/pedidos.
 *
 * Apply and verify this script before deploying the application version that uses
 * PedidoIdempotencia. It is intentionally outside a Flyway directory because this
 * repository does not currently run a migration framework.
 *
 * PROCESANDO rows are inserted and completed in the same API transaction as the
 * pedido. They must never be committed in that state. The unique primary key makes
 * concurrent requests across API instances serialize at SQL Server.
 */
IF OBJECT_ID(N'dbo.pedido_idempotencia', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.pedido_idempotencia
    (
        clave             varchar(128)  NOT NULL,
        hash_solicitud    varbinary(32) NOT NULL,
        estado            varchar(20)   NOT NULL,
        pedido_id         bigint        NULL,
        respuesta_json    nvarchar(max) NULL,
        fecha_creacion    datetime2(6)  NOT NULL,
        fecha_completado  datetime2(6)  NULL,
        CONSTRAINT PK_pedido_idempotencia PRIMARY KEY (clave),
        CONSTRAINT FK_pedido_idempotencia_pedidos
            FOREIGN KEY (pedido_id) REFERENCES dbo.pedidos(id),
        CONSTRAINT CK_pedido_idempotencia_estado CHECK
        (
            (estado = 'PROCESANDO'
                AND pedido_id IS NULL
                AND respuesta_json IS NULL
                AND fecha_completado IS NULL)
            OR
            (estado = 'COMPLETADO'
                AND pedido_id IS NOT NULL
                AND respuesta_json IS NOT NULL
                AND fecha_completado IS NOT NULL)
        )
    );
END;
GO

/*
 * If ddl-auto=update created the entity table before this script was run, add the
 * database-level completion check and FK if Hibernate did not create them.
 */
IF OBJECT_ID(N'dbo.pedido_idempotencia', N'U') IS NOT NULL
   AND NOT EXISTS
       (SELECT 1 FROM sys.check_constraints
        WHERE name = N'CK_pedido_idempotencia_estado'
          AND parent_object_id = OBJECT_ID(N'dbo.pedido_idempotencia'))
BEGIN
    ALTER TABLE dbo.pedido_idempotencia WITH CHECK
        ADD CONSTRAINT CK_pedido_idempotencia_estado CHECK
        (
            (estado = 'PROCESANDO'
                AND pedido_id IS NULL
                AND respuesta_json IS NULL
                AND fecha_completado IS NULL)
            OR
            (estado = 'COMPLETADO'
                AND pedido_id IS NOT NULL
                AND respuesta_json IS NOT NULL
                AND fecha_completado IS NOT NULL)
        );
END;
GO

IF OBJECT_ID(N'dbo.pedido_idempotencia', N'U') IS NOT NULL
   AND NOT EXISTS
       (SELECT 1 FROM sys.foreign_keys
        WHERE parent_object_id = OBJECT_ID(N'dbo.pedido_idempotencia')
          AND referenced_object_id = OBJECT_ID(N'dbo.pedidos'))
BEGIN
    ALTER TABLE dbo.pedido_idempotencia WITH CHECK
        ADD CONSTRAINT FK_pedido_idempotencia_pedidos
            FOREIGN KEY (pedido_id) REFERENCES dbo.pedidos(id);
END;
GO
