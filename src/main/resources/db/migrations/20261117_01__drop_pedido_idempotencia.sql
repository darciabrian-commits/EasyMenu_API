/*
 * Rollback for 20261117_01__create_pedido_idempotencia.sql.
 * This drops all idempotency history. Take a backup and stop all API writers first.
 * After rollback, clients must not rely on replay protection until the migration is
 * restored; a retry could create a second pedido.
 */
IF OBJECT_ID(N'dbo.pedido_idempotencia', N'U') IS NOT NULL
BEGIN
    DROP TABLE dbo.pedido_idempotencia;
END;
GO
