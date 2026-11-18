/*
 * Additive SQL Server migration for the refund metadata introduced in Pago.
 * Apply and verify before deploying the application version that reads/writes
 * these fields. Existing payments remain unchanged because both columns are nullable.
 */
IF OBJECT_ID(N'dbo.pagos', N'U') IS NOT NULL
   AND COL_LENGTH(N'dbo.pagos', N'fecha_reembolso') IS NULL
BEGIN
    ALTER TABLE dbo.pagos
        ADD fecha_reembolso datetime2(6) NULL;
END;
GO

IF OBJECT_ID(N'dbo.pagos', N'U') IS NOT NULL
   AND COL_LENGTH(N'dbo.pagos', N'motivo_reembolso') IS NULL
BEGIN
    ALTER TABLE dbo.pagos
        ADD motivo_reembolso nvarchar(255) NULL;
END;
GO
