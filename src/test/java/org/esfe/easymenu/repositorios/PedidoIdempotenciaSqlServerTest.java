package org.esfe.easymenu.repositorios;

import org.esfe.easymenu.dtos.CrearPedidoRequestDTO;
import org.esfe.easymenu.dtos.ItemPedidoDTO;
import org.esfe.easymenu.dtos.PedidoResponseDTO;
import org.esfe.easymenu.excepcion.ConflictoIdempotenciaException;
import org.esfe.easymenu.excepcion.ReglaNegocioException;
import org.esfe.easymenu.modelos.EstadoIdempotencia;
import org.esfe.easymenu.modelos.EstadoPedido;
import org.esfe.easymenu.modelos.Pedido;
import org.esfe.easymenu.modelos.PedidoIdempotencia;
import org.esfe.easymenu.modelos.Producto;
import org.esfe.easymenu.repositorios.PedidoIdempotenciaRepository;
import org.esfe.easymenu.repositorios.PedidoRepository;
import org.esfe.easymenu.repositorios.ProductoRepository;
import org.esfe.easymenu.servicios.implementaciones.PedidoServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
@Import({PedidoServiceImpl.class, JacksonAutoConfiguration.class})
class PedidoIdempotenciaSqlServerTest {

    @Container
    private static final MSSQLServerContainer<?> SQL_SERVER =
            new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
                    .acceptLicense();

    @Autowired
    private PedidoServiceImpl pedidoService;

    @Autowired
    private PedidoIdempotenciaRepository idempotenciaRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void sqlServerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SQL_SERVER::getJdbcUrl);
        registry.add("spring.datasource.username", SQL_SERVER::getUsername);
        registry.add("spring.datasource.password", SQL_SERVER::getPassword);
        registry.add("spring.datasource.driver-class-name", SQL_SERVER::getDriverClassName);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.SQLServerDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Test
    void creaPedidoConClaveNuevaYGuardaRespuestaYPedidoAtomicos() {
        Producto producto = crearProducto();
        String clave = nuevaClave();

        PedidoResponseDTO response = pedidoService.crearPedido(
                solicitud(producto, "Mesa nueva", 2, "sin sal"),
                clave
        );

        assertEquals(EstadoPedido.PENDIENTE_PAGO, response.getEstado());
        assertEquals(new BigDecimal("25.00"), response.getTotal());
        assertEquals(10, response.getCodigoCorto().length());

        PedidoIdempotencia registro = idempotenciaRepository.findById(clave)
                .orElseThrow();
        assertEquals(EstadoIdempotencia.COMPLETADO, registro.getEstado());
        assertEquals(response.getId(), registro.getPedido().getId());
        assertNotNull(registro.getRespuestaJson());
        assertEquals(1, contarPedidos("Mesa nueva"));
    }

    @Test
    void repeticionCanonicaDevuelveLaRespuestaOriginalSinCrearOtroPedido() {
        Producto producto = crearProducto();
        CrearPedidoRequestDTO primeraSolicitud = solicitud(producto, "Mesa replay", 1, null);
        String clave = nuevaClave();
        PedidoResponseDTO original = pedidoService.crearPedido(primeraSolicitud, clave);

        PedidoResponseDTO repeticion = pedidoService.crearPedido(
                solicitud(producto, "Mesa replay", 1, null),
                clave
        );

        assertEquals(original, repeticion);
        assertEquals(1, contarPedidos("Mesa replay"));
    }

    @Test
    void mismaClaveConContenidoDiferenteDevuelveConflicto() {
        Producto producto = crearProducto();
        String clave = nuevaClave();
        pedidoService.crearPedido(solicitud(producto, "Mesa original", 1, null), clave);

        assertThrows(ConflictoIdempotenciaException.class, () ->
                pedidoService.crearPedido(
                        solicitud(producto, "Mesa modificada", 1, null),
                        clave));

        assertEquals(1, contarPedidos("Mesa original"));
        assertEquals(0, contarPedidos("Mesa modificada"));
    }

    @Test
    void pedidosSinClaveConElMismoCuerpoSiguenSiendoOperacionesIndependientes() {
        Producto producto = crearProducto();
        CrearPedidoRequestDTO solicitud = solicitud(producto, "Mesa sin clave", 1, null);

        PedidoResponseDTO primero = pedidoService.crearPedido(solicitud);
        PedidoResponseDTO segundo = pedidoService.crearPedido(solicitud);

        assertNotEquals(primero.getId(), segundo.getId());
        assertEquals(2, contarPedidos("Mesa sin clave"));
        assertTrue(idempotenciaRepository.findById("pedido-sin-clave").isEmpty());
    }

    @Test
    void clavesInvalidasSeRechazanAntesDeCrearPedidos() {
        Producto producto = crearProducto();
        CrearPedidoRequestDTO solicitud = solicitud(producto, "Mesa clave inválida", 1, null);

        for (String clave : List.of(
                "corta",
                "00000000-0000-1000-8000-000000000000",
                "00000000-0000-4000-7000-000000000000",
                "00000000-0000-4000-8000-00000000000Z")) {
            assertThrows(ReglaNegocioException.class,
                    () -> pedidoService.crearPedido(solicitud, clave));
        }

        assertEquals(0, contarPedidos("Mesa clave inválida"));
    }

    @Test
    void normalizaMayusculasDeUuidSinCrearUnaClaveDistinta() {
        Producto producto = crearProducto();
        String clave = nuevaClave();
        PedidoResponseDTO original = pedidoService.crearPedido(
                solicitud(producto, "Mesa UUID", 1, null),
                clave.toUpperCase(java.util.Locale.ROOT));

        PedidoResponseDTO repeticion = pedidoService.crearPedido(
                solicitud(producto, "Mesa UUID", 1, null),
                clave);

        assertEquals(original, repeticion);
        assertEquals(1, contarPedidos("Mesa UUID"));
    }

    @Test
    void falloAlCompletarLaReservaRevierteLaClaveYElPedidoYPermiteReintentar() {
        Producto producto = crearProducto();
        CrearPedidoRequestDTO solicitud = solicitud(producto, "Mesa rollback", 1, null);
        String clave = nuevaClave();
        crearTriggerFalloPedido();
        RuntimeException fallo;
        try {
            fallo = assertThrows(RuntimeException.class,
                    () -> pedidoService.crearPedido(solicitud, clave));
        } finally {
            eliminarTriggerFalloPedido();
        }
        assertTrue(contieneErrorSql(fallo, 51000), fallo.toString());
        assertTrue(idempotenciaRepository.findById(clave).isEmpty());
        assertEquals(0, contarPedidos("Mesa rollback"));

        PedidoResponseDTO recuperado = pedidoService.crearPedido(solicitud, clave);

        assertNotNull(recuperado.getId());
        assertEquals(1, contarPedidos("Mesa rollback"));
        assertEquals(EstadoIdempotencia.COMPLETADO,
                idempotenciaRepository.findById(clave).orElseThrow().getEstado());
    }

    @Test
    void claveConfirmadaReproduceElResultadoEnUnaTransaccionPosterior() {
        Producto producto = crearProducto();
        String clave = nuevaClave();
        PedidoResponseDTO confirmado = pedidoService.crearPedido(
                solicitud(producto, "Mesa confirmado", 1, null),
                clave
        );

        PedidoResponseDTO reintentoPosterior = pedidoService.crearPedido(
                solicitud(producto, "Mesa confirmado", 1, null),
                clave
        );

        assertEquals(confirmado, reintentoPosterior);
        assertEquals(1, contarPedidos("Mesa confirmado"));
    }

    @Test
    void dosSolicitudesConcurrentesConLaMismaClaveSeSerializanEnSqlServer() throws Exception {
        Producto producto = crearProducto();
        CrearPedidoRequestDTO solicitud = solicitud(producto, "Mesa concurrente igual", 1, null);
        String clave = nuevaClave();
        CountDownLatch solicitudesListas = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch inicio = new CountDownLatch(1);
        crearTriggerPausaIdempotencia(5);

        try {
            Future<PedidoResponseDTO> primera = executor.submit(() -> {
                solicitudesListas.countDown();
                esperar(inicio);
                return pedidoService.crearPedido(solicitud, clave);
            });
            Future<PedidoResponseDTO> segunda = executor.submit(() -> {
                solicitudesListas.countDown();
                esperar(inicio);
                return pedidoService.crearPedido(solicitud, clave);
            });
            assertTrue(solicitudesListas.await(10, TimeUnit.SECONDS));
            inicio.countDown();
            esperarBloqueoSqlServerIdempotencia();
            assertFalse(primera.isDone());
            assertFalse(segunda.isDone());

            PedidoResponseDTO primeraRespuesta = primera.get(15, TimeUnit.SECONDS);
            PedidoResponseDTO segundaRespuesta = segunda.get(15, TimeUnit.SECONDS);

            assertEquals(primeraRespuesta, segundaRespuesta);
            assertEquals(1, contarPedidos("Mesa concurrente igual"));
        } finally {
            inicio.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            eliminarTriggerPausaIdempotencia();
        }
    }

    @Test
    void dosSolicitudesConcurrentesConClavesDiferentesCreanDosPedidos() throws Exception {
        Producto producto = crearProducto();
        CountDownLatch solicitudesListas = new CountDownLatch(2);
        CountDownLatch inicio = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        crearTriggerPausaIdempotencia(5);

        try {
            Future<PedidoResponseDTO> primera = executor.submit(() ->
                    crearConcurrente(solicitudesListas, inicio, producto,
                            "Mesa concurrente A", nuevaClave()));

            Future<PedidoResponseDTO> segunda = executor.submit(() ->
                    crearConcurrente(solicitudesListas, inicio, producto,
                            "Mesa concurrente B", nuevaClave()));

            assertTrue(solicitudesListas.await(10, TimeUnit.SECONDS));
            inicio.countDown();
            esperarDosInsercionesIdempotenciaActivas();
            assertFalse(primera.isDone());
            assertFalse(segunda.isDone());

            PedidoResponseDTO segundaRespuesta = segunda.get(15, TimeUnit.SECONDS);
            PedidoResponseDTO primeraRespuesta = primera.get(15, TimeUnit.SECONDS);

            assertNotEquals(primeraRespuesta.getId(), segundaRespuesta.getId());
            assertEquals(1, contarPedidos("Mesa concurrente A"));
            assertEquals(1, contarPedidos("Mesa concurrente B"));
        } finally {
            inicio.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            eliminarTriggerPausaIdempotencia();
        }
    }

    @Test
    void solicitudesSolapadasConLaMismaClavePermitenContinuarTrasElRollback() throws Exception {
        Producto producto = crearProducto();
        CrearPedidoRequestDTO solicitud = solicitud(producto, "Mesa rollback concurrente", 1, null);
        String clave = nuevaClave();
        CountDownLatch solicitudesListas = new CountDownLatch(2);
        CountDownLatch inicio = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        crearTriggersFalloUnaVezConEspera();

        try {
            Future<PedidoResponseDTO> primera = executor.submit(() ->
                    crearConcurrente(solicitudesListas, inicio, solicitud, clave));
            Future<PedidoResponseDTO> segunda = executor.submit(() ->
                    crearConcurrente(solicitudesListas, inicio, solicitud, clave));

            assertTrue(solicitudesListas.await(10, TimeUnit.SECONDS));
            inicio.countDown();
            esperarBloqueoSqlServerIdempotencia();

            int exitosas = 0;
            int fallidas = 0;
            for (Future<PedidoResponseDTO> resultado : List.of(primera, segunda)) {
                try {
                    assertNotNull(resultado.get(20, TimeUnit.SECONDS));
                    exitosas++;
                } catch (java.util.concurrent.ExecutionException ex) {
                    assertTrue(contieneErrorSql(ex, 51001), ex.toString());
                    fallidas++;
                }
            }

            assertEquals(1, fallidas, "La primera transacción debe fallar y revertirse");
            assertEquals(1, exitosas, "La solicitud en espera debe completar el pedido");
            assertEquals(1, contarPedidos("Mesa rollback concurrente"));
            assertEquals(EstadoIdempotencia.COMPLETADO,
                    idempotenciaRepository.findById(clave).orElseThrow().getEstado());
        } finally {
            inicio.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
            eliminarTriggersFalloUnaVezConEspera();
        }
    }

    @Test
    void migracionManualCreaYValidaRestriccionesEnSqlServer() throws IOException {
        Pedido pedidoExistente = new Pedido();
        pedidoExistente.setClienteOMesa("Pedido previo a migración");
        pedidoExistente.setEstado(EstadoPedido.PENDIENTE_PAGO);
        pedidoExistente.setTotal(BigDecimal.ONE);
        pedidoExistente.setFechaHora(LocalDateTime.now());
        pedidoExistente = pedidoRepository.saveAndFlush(pedidoExistente);

        jdbcTemplate.execute("DROP TABLE dbo.pedido_idempotencia");
        ejecutarScriptMigracionIdempotencia();

        assertEquals(1, contarRestricciones("sys.key_constraints", "PK_pedido_idempotencia"));
        assertEquals(1, contarRestricciones("sys.foreign_keys", "FK_pedido_idempotencia_pedidos"));
        assertEquals(1, contarRestricciones("sys.check_constraints", "CK_pedido_idempotencia_estado"));
        assertEquals("varchar", jdbcTemplate.queryForObject("""
                SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = 'pedido_idempotencia'
                  AND COLUMN_NAME = 'clave'
                """, String.class));
        assertEquals(128, jdbcTemplate.queryForObject("""
                SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = 'pedido_idempotencia'
                  AND COLUMN_NAME = 'clave'
                """, Integer.class));
        assertEquals("nvarchar", jdbcTemplate.queryForObject("""
                SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = 'pedido_idempotencia'
                  AND COLUMN_NAME = 'respuesta_json'
                """, String.class));
        assertEquals(-1, jdbcTemplate.queryForObject("""
                SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = 'pedido_idempotencia'
                  AND COLUMN_NAME = 'respuesta_json'
                """, Integer.class));
        assertEquals("varbinary", tipoColumna("hash_solicitud"));
        assertEquals(32, longitudColumna("hash_solicitud"));
        assertEquals("varchar", tipoColumna("estado"));
        assertEquals(20, longitudColumna("estado"));
        assertEquals("bigint", tipoColumna("pedido_id"));
        assertEquals("datetime2", tipoColumna("fecha_creacion"));
        assertEquals(6, precisionTemporal("fecha_creacion"));
        assertEquals(6, precisionTemporal("fecha_completado"));

        String clave = nuevaClave();
        byte[] hash = new byte[32];
        jdbcTemplate.update("""
                INSERT INTO dbo.pedido_idempotencia
                    (clave, hash_solicitud, estado, pedido_id, respuesta_json,
                     fecha_creacion, fecha_completado)
                VALUES (?, ?, 'COMPLETADO', ?, N'{}', SYSDATETIME(), SYSDATETIME())
                """, clave, hash, pedidoExistente.getId());

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                INSERT INTO dbo.pedido_idempotencia
                    (clave, hash_solicitud, estado, pedido_id, respuesta_json,
                     fecha_creacion, fecha_completado)
                VALUES (?, ?, 'COMPLETADO', -1, N'{}', SYSDATETIME(), SYSDATETIME())
                """, nuevaClave(), hash));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                INSERT INTO dbo.pedido_idempotencia
                    (clave, hash_solicitud, estado, pedido_id, respuesta_json,
                     fecha_creacion, fecha_completado)
                VALUES (?, ?, 'COMPLETADO', NULL, NULL, SYSDATETIME(), NULL)
                """, nuevaClave(), hash));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                INSERT INTO dbo.pedido_idempotencia
                    (clave, hash_solicitud, estado, pedido_id, respuesta_json,
                     fecha_creacion, fecha_completado)
                VALUES (?, ?, 'PROCESANDO', NULL, NULL, SYSDATETIME(), NULL)
                """, clave, hash));

        ejecutarScriptMigracionIdempotencia();
    }

    @Test
    void conservaRegistrosHistoricosConCodigoDeSeisCaracteres() {
        Pedido historico = new Pedido();
        historico.setCodigoCorto("ABC123");
        historico.setClienteOMesa("Pedido histórico");
        historico.setEstado(EstadoPedido.PENDIENTE_PAGO);
        historico.setTotal(BigDecimal.ONE);
        historico.setFechaHora(LocalDateTime.now());
        pedidoRepository.saveAndFlush(historico);

        Pedido guardado = pedidoRepository.findByCodigoCorto("ABC123").orElseThrow();

        assertEquals("ABC123", guardado.getCodigoCorto());
        assertNotNull(guardado.getId());
    }

    private PedidoResponseDTO crearConcurrente(
            CountDownLatch listas,
            CountDownLatch inicio,
            Producto producto,
            String mesa,
            String clave
    ) {
        listas.countDown();
        esperar(inicio);
        return pedidoService.crearPedido(solicitud(producto, mesa, 1, null), clave);
    }

    private PedidoResponseDTO crearConcurrente(
            CountDownLatch listas,
            CountDownLatch inicio,
            CrearPedidoRequestDTO solicitud,
            String clave
    ) {
        listas.countDown();
        esperar(inicio);
        return pedidoService.crearPedido(solicitud, clave);
    }

    private void crearTriggerPausaIdempotencia(int segundos) {
        jdbcTemplate.execute("""
                CREATE TRIGGER dbo.trg_test_pausa_idempotencia
                ON dbo.pedido_idempotencia
                AFTER INSERT
                AS
                BEGIN
                    WAITFOR DELAY '00:00:%02d';
                END
                """.formatted(segundos));
    }

    private void crearTriggersFalloUnaVezConEspera() {
        jdbcTemplate.execute("""
                CREATE SEQUENCE dbo.seq_test_idempotencia START WITH 1 INCREMENT BY 1
                """);
        jdbcTemplate.execute("""
                CREATE SEQUENCE dbo.seq_test_pedido_fallo START WITH 1 INCREMENT BY 1
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER dbo.trg_test_pausa_idempotencia_una_vez
                ON dbo.pedido_idempotencia
                AFTER INSERT
                AS
                BEGIN
                    DECLARE @intento bigint;
                    SELECT @intento = NEXT VALUE FOR dbo.seq_test_idempotencia;
                    IF @intento = 1
                        WAITFOR DELAY '00:00:05';
                END
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER dbo.trg_test_fallo_pedido_una_vez
                ON dbo.pedidos
                AFTER INSERT
                AS
                BEGIN
                    DECLARE @intento bigint;
                    SELECT @intento = NEXT VALUE FOR dbo.seq_test_pedido_fallo;
                    IF @intento = 1
                        THROW 51001, 'Fallo transaccional controlado de prueba', 1;
                END
                """);
    }

    private void eliminarTriggersFalloUnaVezConEspera() {
        jdbcTemplate.execute("""
                IF OBJECT_ID(N'dbo.trg_test_pausa_idempotencia_una_vez', N'TR') IS NOT NULL
                    DROP TRIGGER dbo.trg_test_pausa_idempotencia_una_vez
                """);
        jdbcTemplate.execute("""
                IF OBJECT_ID(N'dbo.trg_test_fallo_pedido_una_vez', N'TR') IS NOT NULL
                    DROP TRIGGER dbo.trg_test_fallo_pedido_una_vez
                """);
        jdbcTemplate.execute("""
                IF OBJECT_ID(N'dbo.seq_test_idempotencia', N'SO') IS NOT NULL
                    DROP SEQUENCE dbo.seq_test_idempotencia
                """);
        jdbcTemplate.execute("""
                IF OBJECT_ID(N'dbo.seq_test_pedido_fallo', N'SO') IS NOT NULL
                    DROP SEQUENCE dbo.seq_test_pedido_fallo
                """);
    }

    private void eliminarTriggerPausaIdempotencia() {
        jdbcTemplate.execute("""
                IF OBJECT_ID(N'dbo.trg_test_pausa_idempotencia', N'TR') IS NOT NULL
                    DROP TRIGGER dbo.trg_test_pausa_idempotencia
                """);
    }

    private void crearTriggerFalloPedido() {
        jdbcTemplate.execute("""
                CREATE TRIGGER dbo.trg_test_fallo_pedido
                ON dbo.pedidos
                AFTER INSERT
                AS
                BEGIN
                    THROW 51000, 'Fallo transaccional de prueba', 1;
                END
                """);
    }

    private void eliminarTriggerFalloPedido() {
        jdbcTemplate.execute("""
                IF OBJECT_ID(N'dbo.trg_test_fallo_pedido', N'TR') IS NOT NULL
                    DROP TRIGGER dbo.trg_test_fallo_pedido
                """);
    }

    private int contarRestricciones(String catalogo, String nombre) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + catalogo + " WHERE name = ?",
                Integer.class,
                nombre);
    }

    private void ejecutarScriptMigracionIdempotencia() throws IOException {
        String script;
        try (var input = new ClassPathResource(
                "db/migrations/20261117_01__create_pedido_idempotencia.sql").getInputStream()) {
            script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        for (String batch : script.split("(?im)^[\\t ]*GO[\\t ]*(?:\\r?\\n|$)")) {
            if (!batch.isBlank()) {
                jdbcTemplate.execute(batch);
            }
        }
    }

    private void esperarBloqueoSqlServerIdempotencia() throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < limite) {
            Integer bloqueos = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM sys.dm_exec_requests AS r
                    CROSS APPLY sys.dm_exec_sql_text(r.sql_handle) AS t
                    WHERE r.session_id <> @@SPID
                      AND r.blocking_session_id > 0
                      AND r.wait_type LIKE 'LCK%'
                      AND t.text LIKE '%pedido_idempotencia%'
                      AND EXISTS (
                          SELECT 1
                          FROM sys.dm_exec_requests AS bloqueador
                          WHERE bloqueador.session_id = r.blocking_session_id
                            AND bloqueador.wait_type = 'WAITFOR'
                      )
                    """, Integer.class);
            if (bloqueos != null && bloqueos > 0) {
                return;
            }
            Thread.sleep(25);
        }
        fail("SQL Server no mostró una solicitud de idempotencia bloqueada");
    }

    private void esperarDosInsercionesIdempotenciaActivas() throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < limite) {
            Integer activas = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM sys.dm_exec_requests AS r
                    CROSS APPLY sys.dm_exec_sql_text(r.sql_handle) AS t
                    WHERE r.session_id <> @@SPID
                      AND r.wait_type = 'WAITFOR'
                      AND t.text LIKE '%pedido_idempotencia%'
                    """, Integer.class);
            if (activas != null && activas >= 2) {
                return;
            }
            Thread.sleep(25);
        }
        fail("SQL Server no mostró dos inserciones de idempotencia activas a la vez");
    }

    private String tipoColumna(String columna) {
        return jdbcTemplate.queryForObject("""
                SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = 'pedido_idempotencia'
                  AND COLUMN_NAME = ?
                """, String.class, columna);
    }

    private int longitudColumna(String columna) {
        return jdbcTemplate.queryForObject("""
                SELECT CHARACTER_OCTET_LENGTH FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = 'pedido_idempotencia'
                  AND COLUMN_NAME = ?
                """, Integer.class, columna);
    }

    private int precisionTemporal(String columna) {
        return jdbcTemplate.queryForObject("""
                SELECT DATETIME_PRECISION FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = 'pedido_idempotencia'
                  AND COLUMN_NAME = ?
                """, Integer.class, columna);
    }

    private String nuevaClave() {
        return UUID.randomUUID().toString();
    }

    private boolean contieneErrorSql(Throwable error, int codigo) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof java.sql.SQLException sqlException
                    && sqlException.getErrorCode() == codigo) {
                return true;
            }
        }
        return false;
    }

    private Producto crearProducto() {
        Producto producto = new Producto();
        producto.setNombre("Producto " + System.nanoTime());
        producto.setPrecio(new BigDecimal("12.50"));
        producto.setDisponible(true);
        producto.setActivo(true);
        return productoRepository.saveAndFlush(producto);
    }

    private CrearPedidoRequestDTO solicitud(
            Producto producto,
            String mesa,
            int cantidad,
            String notas
    ) {
        return new CrearPedidoRequestDTO(
                mesa,
                List.of(new ItemPedidoDTO(producto.getId(), cantidad, notas)));
    }

    private int contarPedidos(String clienteOMesa) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pedidos WHERE cliente_o_mesa = ?",
                Integer.class,
                clienteOMesa);
    }

    private void esperar(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Tiempo de espera agotado");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Operación de prueba interrumpida", ex);
        }
    }
}
