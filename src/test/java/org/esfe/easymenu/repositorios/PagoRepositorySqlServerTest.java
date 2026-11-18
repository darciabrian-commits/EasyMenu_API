package org.esfe.easymenu.repositorios;

import org.esfe.easymenu.modelos.EstadoPago;
import org.esfe.easymenu.modelos.EstadoPedido;
import org.esfe.easymenu.modelos.MetodoPago;
import org.esfe.easymenu.modelos.Pago;
import org.esfe.easymenu.modelos.Pedido;
import org.esfe.easymenu.modelos.Producto;
import org.esfe.easymenu.dtos.PagoDTO;
import org.esfe.easymenu.repositorios.ProductoRepository;
import org.esfe.easymenu.servicios.implementaciones.PagoServiceImpl;
import org.esfe.easymenu.servicios.implementaciones.PedidoServiceImpl;
import org.esfe.easymenu.dtos.ProcesarPagoDTO;
import org.esfe.easymenu.excepcion.RecursoNoEncontradoException;
import org.esfe.easymenu.excepcion.ReglaNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
@Import({PagoServiceImpl.class, PedidoServiceImpl.class, JacksonAutoConfiguration.class})
class PagoRepositorySqlServerTest {

    @Container
    private static final MSSQLServerContainer<?> SQL_SERVER =
            new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
                    .acceptLicense();

    @Autowired
    private PagoRepository pagoRepository;

    @SpyBean
    private PedidoRepository pedidoRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PagoServiceImpl pagoService;

    @Autowired
    private PedidoServiceImpl pedidoService;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
    void ejecutaConsultasAgregadasEnSqlServer() {
        LocalDate diaCobro = LocalDate.of(2026, 11, 16);
        LocalDateTime inicioCobro = diaCobro.atStartOfDay();
        LocalDateTime finCobro = diaCobro.plusDays(1).atStartOfDay();
        LocalDateTime inicioReembolso = finCobro;
        LocalDateTime finReembolso = diaCobro.plusDays(2).atStartOfDay();

        Pago pago = crearPago(new BigDecimal("1234.56"), EstadoPago.REEMBOLSADO);
        pago.setFechaHora(inicioCobro);
        pago.setFechaReembolso(inicioReembolso);
        pagoRepository.saveAndFlush(pago);

        var cobros = pagoRepository.resumirCobrosEntre(inicioCobro, finCobro);
        var reembolsos = pagoRepository.resumirReembolsosEntre(inicioReembolso, finReembolso);

        assertEquals(1L, cobros.getCantidadCobros());
        assertEquals(new BigDecimal("1234.56"), cobros.getTotalCobradoEfectivo());
        assertEquals(1L, reembolsos.getCantidadReembolsos());
        assertEquals(new BigDecimal("1234.56"), reembolsos.getTotalReembolsos());
    }

    @Test
    void obtieneElRegistroRealDePagoPorPedido() {
        Pago pago = crearPago(new BigDecimal("25.00"), EstadoPago.COMPLETADO);
        pago.setMetodoPago(MetodoPago.TARJETA);
        pago.setMontoRecibido(new BigDecimal("25.00"));
        pagoRepository.saveAndFlush(pago);

        PagoDTO response = pagoService.obtenerPorPedidoId(pago.getPedido().getId());

        assertEquals(pago.getId(), response.getId());
        assertEquals(pago.getPedido().getId(), response.getPedidoId());
        assertEquals(EstadoPago.COMPLETADO, response.getEstado());
        assertEquals(MetodoPago.TARJETA, response.getMetodoPago());
    }

    @Test
    void pedidosSinPagoYPedidosInexistentesDevuelvenNoEncontradoSegunElContratoActual() {
        Long pedidoExistenteSinPago =
                crearPedido(EstadoPedido.PENDIENTE_PAGO, LocalDateTime.now()).getId();

        RecursoNoEncontradoException sinPago = assertThrows(
                RecursoNoEncontradoException.class,
                () -> pagoService.obtenerPorPedidoId(pedidoExistenteSinPago));
        RecursoNoEncontradoException pedidoInexistente = assertThrows(
                RecursoNoEncontradoException.class,
                () -> pagoService.obtenerPorPedidoId(-1L));

        assertEquals(
                "No existe comprobante de pago para el pedido ID: " + pedidoExistenteSinPago,
                sinPago.getMessage());
        assertEquals(
                "No existe comprobante de pago para el pedido ID: -1",
                pedidoInexistente.getMessage());
    }

    @Test
    void consultaPorPedidoIdentificaUnPagoReembolsado() {
        Pago pago = crearPago(new BigDecimal("25.00"), EstadoPago.REEMBOLSADO);
        pago.setMotivoReembolso("Reembolso de prueba");
        pago.setFechaReembolso(LocalDateTime.now());
        pagoRepository.saveAndFlush(pago);

        PagoDTO response = pagoService.obtenerPorPedidoId(pago.getPedido().getId());

        assertEquals(EstadoPago.REEMBOLSADO, response.getEstado());
        assertEquals("Reembolso de prueba", response.getMotivoReembolso());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cobroPrimeroHaceQueAmbosTiposDeCancelacionEsperenYRechacen(boolean porCodigo)
            throws Exception {
        Pedido pedido = crearPedido(EstadoPedido.PENDIENTE_PAGO, LocalDateTime.now());
        Long pedidoId = pedido.getId();
        String codigo = pedido.getCodigoCorto();

        ejecutarConPrimeraOperacionRetenida(
                () -> pagoService.cobrarPedido(dto(pedidoId)),
                () -> {
                    if (porCodigo) {
                        assertThrows(RecursoNoEncontradoException.class,
                                () -> pedidoService.cancelarPedidoPorCodigo(codigo));
                    } else {
                        assertThrows(ReglaNegocioException.class,
                                () -> pedidoService.cancelarPedido(pedidoId));
                    }
                });

        assertEquals(EstadoPedido.RECIBIDO,
                pedidoRepository.findById(pedidoId).orElseThrow().getEstado());
        assertEquals(EstadoPago.COMPLETADO,
                pagoRepository.findByPedidoId(pedidoId).orElseThrow().getEstado());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancelacionPrimeroHaceQueElCobroEsperadoSeaRechazado(boolean porCodigo)
            throws Exception {
        Pedido pedido = crearPedido(EstadoPedido.PENDIENTE_PAGO, LocalDateTime.now());
        Long pedidoId = pedido.getId();
        String codigo = pedido.getCodigoCorto();

        ejecutarConPrimeraOperacionRetenida(
                () -> {
                    if (porCodigo) {
                        pedidoService.cancelarPedidoPorCodigo(codigo);
                    } else {
                        pedidoService.cancelarPedido(pedidoId);
                    }
                },
                () -> assertThrows(ReglaNegocioException.class,
                        () -> pagoService.cobrarPedido(dto(pedidoId))));

        assertEquals(EstadoPedido.CANCELADO,
                pedidoRepository.findById(pedidoId).orElseThrow().getEstado());
        assertTrue(pagoRepository.findByPedidoId(pedidoId).isEmpty());
    }

    @Test
    void cobroPrimeroImpideQueLaTareaExpireUnPedidoYaPagado() throws Exception {
        Long pedidoId = crearPedido(
                EstadoPedido.PENDIENTE_PAGO,
                LocalDateTime.now().minusMinutes(16)).getId();

        ejecutarConPrimeraOperacionRetenida(
                () -> pagoService.cobrarPedido(dto(pedidoId)),
                () -> pedidoService.expirarPedidosPendientes(15));

        assertEquals(EstadoPedido.RECIBIDO,
                pedidoRepository.findById(pedidoId).orElseThrow().getEstado());
        assertEquals(EstadoPago.COMPLETADO,
                pagoRepository.findByPedidoId(pedidoId).orElseThrow().getEstado());
    }

    @Test
    void expiracionPrimeroImpideQueElCobroCambieUnPedidoExpirado() throws Exception {
        Long pedidoId = crearPedido(
                EstadoPedido.PENDIENTE_PAGO,
                LocalDateTime.now().minusMinutes(16)).getId();

        ejecutarConPrimeraOperacionRetenida(
                () -> pedidoService.expirarPedidosPendientes(15),
                () -> assertThrows(ReglaNegocioException.class,
                        () -> pagoService.cobrarPedido(dto(pedidoId))));

        assertEquals(EstadoPedido.EXPIRADO,
                pedidoRepository.findById(pedidoId).orElseThrow().getEstado());
        assertTrue(pagoRepository.findByPedidoId(pedidoId).isEmpty());
    }

    @Test
    void dosReembolsosConcurrentesDelMismoPagoSoloPermitenElPrimero() throws Exception {
        Pago pago = crearPago(new BigDecimal("25.00"), EstadoPago.COMPLETADO);
        Long pagoId = pago.getId();
        Long pedidoId = pago.getPedido().getId();
        AtomicReference<LocalDateTime> fechaPrimerReembolso = new AtomicReference<>();

        ejecutarConPrimeraOperacionRetenida(
                () -> {
                    pagoService.reembolsarPago(pagoId, "Primer motivo");
                    entityManager.flush();
                    Pago primerPago = pagoRepository.findById(pagoId).orElseThrow();
                    entityManager.refresh(primerPago);
                    fechaPrimerReembolso.set(primerPago.getFechaReembolso());
                },
                () -> assertThrows(ReglaNegocioException.class,
                        () -> pagoService.reembolsarPago(pagoId, "Segundo motivo")));

        Pago pagoFinal = pagoRepository.findById(pagoId).orElseThrow();
        Pedido pedidoFinal = pedidoRepository.findById(pedidoId).orElseThrow();
        assertEquals(EstadoPago.REEMBOLSADO, pagoFinal.getEstado());
        assertEquals(EstadoPedido.REEMBOLSADO, pedidoFinal.getEstado());
        assertEquals("Primer motivo", pagoFinal.getMotivoReembolso());
        assertEquals(fechaPrimerReembolso.get(), pagoFinal.getFechaReembolso());
    }

    @Test
    void reembolsoPrimeroImpideQueUnCambioDeEstadoPosteriorLoSobrescriba() throws Exception {
        Pago pago = crearPago(new BigDecimal("25.00"), EstadoPago.COMPLETADO);
        Long pagoId = pago.getId();
        Long pedidoId = pago.getPedido().getId();

        ejecutarConPrimeraOperacionRetenida(
                () -> pagoService.reembolsarPago(pagoId, "Motivo reembolso primero"),
                () -> assertThrows(ReglaNegocioException.class,
                        () -> pedidoService.cambiarEstado(
                                pedidoId, EstadoPedido.EN_PREPARACION)));

        assertEquals(EstadoPago.REEMBOLSADO,
                pagoRepository.findById(pagoId).orElseThrow().getEstado());
        assertEquals(EstadoPedido.REEMBOLSADO,
                pedidoRepository.findById(pedidoId).orElseThrow().getEstado());
    }

    @Test
    void cambioDeEstadoPrimeroSeSerializaAntesDelReembolso() throws Exception {
        Pago pago = crearPago(new BigDecimal("25.00"), EstadoPago.COMPLETADO);
        Long pagoId = pago.getId();
        Long pedidoId = pago.getPedido().getId();

        ejecutarConPrimeraOperacionRetenida(
                () -> pedidoService.cambiarEstado(pedidoId, EstadoPedido.EN_PREPARACION),
                () -> pagoService.reembolsarPago(pagoId, "Motivo reembolso posterior"));

        assertEquals(EstadoPago.REEMBOLSADO,
                pagoRepository.findById(pagoId).orElseThrow().getEstado());
        assertEquals(EstadoPedido.REEMBOLSADO,
                pedidoRepository.findById(pedidoId).orElseThrow().getEstado());
    }

    @Test
    void transicionesConcurrentesSeValidanContraElEstadoConfirmado() throws Exception {
        Pedido pedido = crearPedido(EstadoPedido.RECIBIDO, LocalDateTime.now());
        Long pedidoId = pedido.getId();

        ejecutarConPrimeraOperacionRetenida(
                () -> pedidoService.cambiarEstado(
                        pedidoId, EstadoPedido.EN_PREPARACION),
                () -> pedidoService.cambiarEstado(pedidoId, EstadoPedido.LISTO));

        assertEquals(EstadoPedido.LISTO,
                pedidoRepository.findById(pedidoId).orElseThrow().getEstado());
    }

    @Test
    void dosTransicionesIgualesConcurrentesNoRepitenUnPasoYaAplicado() throws Exception {
        Pedido pedido = crearPedido(EstadoPedido.RECIBIDO, LocalDateTime.now());
        Long pedidoId = pedido.getId();

        ejecutarConPrimeraOperacionRetenida(
                () -> pedidoService.cambiarEstado(
                        pedidoId, EstadoPedido.EN_PREPARACION),
                () -> assertThrows(ReglaNegocioException.class,
                        () -> pedidoService.cambiarEstado(
                                pedidoId, EstadoPedido.EN_PREPARACION)));

        assertEquals(EstadoPedido.EN_PREPARACION,
                pedidoRepository.findById(pedidoId).orElseThrow().getEstado());
    }

    @Test
    void cambioDeEstadoNoPermiteLlegarARecibidoSinCobro() {
        Pedido pedido = crearPedido(EstadoPedido.PENDIENTE_PAGO, LocalDateTime.now());

        assertThrows(ReglaNegocioException.class,
                () -> pedidoService.cambiarEstado(pedido.getId(), EstadoPedido.RECIBIDO));

        assertEquals(EstadoPedido.PENDIENTE_PAGO,
                pedidoRepository.findById(pedido.getId()).orElseThrow().getEstado());
        assertTrue(pagoRepository.findByPedidoId(pedido.getId()).isEmpty());
    }

    @Test
    void colisionSqlServerHaceRollbackDeIntentoYReintentaEnOtraTransaccion() {
        String colision = "ABCDEFGHJK";
        String codigoAlternativo = "ZZZZZZZZZZ";
        Producto producto = new Producto();
        producto.setNombre("Producto para reintento");
        producto.setPrecio(new BigDecimal("12.50"));
        producto.setDisponible(true);
        producto.setActivo(true);
        producto = productoRepository.saveAndFlush(producto);
        Producto productoGuardado = producto;

        AtomicInteger consultas = new AtomicInteger();
        doAnswer(invocation -> {
            String codigoConsultado = invocation.getArgument(0);
            int consulta = consultas.getAndIncrement();
            if (consulta == 0 && colision.equals(codigoConsultado)) {
                TransactionTemplate otraTransaccion = new TransactionTemplate(transactionManager);
                otraTransaccion.setPropagationBehavior(
                        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                otraTransaccion.executeWithoutResult(status -> {
                    Pedido competidor = new Pedido();
                    competidor.setCodigoCorto(colision);
                    competidor.setClienteOMesa("Pedido competidor");
                    competidor.setEstado(EstadoPedido.PENDIENTE_PAGO);
                    competidor.setTotal(BigDecimal.ONE);
                    competidor.setFechaHora(LocalDateTime.now());
                    entityManager.persist(competidor);
                    entityManager.flush();
                });
                return java.util.Optional.empty();
            }
            return consultarPedido(codigoConsultado);
        }).when(pedidoRepository).findByCodigoCorto(any(String.class));

        try (var generador = mockStatic(Pedido.class)) {
            generador.when(Pedido::generarCodigoCorto).thenReturn(colision, codigoAlternativo);

            var response = pedidoService.crearPedido(
                    new org.esfe.easymenu.dtos.CrearPedidoRequestDTO(
                            "Mesa de reintento",
                            java.util.List.of(new org.esfe.easymenu.dtos.ItemPedidoDTO(
                                    productoGuardado.getId(), 1, null))));

            assertEquals(codigoAlternativo, response.getCodigoCorto());
        }

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pedidos WHERE codigo_corto = ?", Integer.class, colision));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pedidos WHERE codigo_corto = ?", Integer.class, codigoAlternativo));
    }

    private Optional<Pedido> consultarPedido(String codigo) {
        TransactionTemplate lectura = new TransactionTemplate(transactionManager);
        lectura.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return lectura.execute(status -> entityManager.createQuery(
                        "SELECT p FROM Pedido p WHERE p.codigoCorto = :codigo",
                        Pedido.class)
                .setParameter("codigo", codigo)
                .getResultStream()
                .findFirst());
    }

    private void ejecutarConPrimeraOperacionRetenida(Runnable primera, Runnable segunda)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch primeraLista = new CountDownLatch(1);
        CountDownLatch liberarPrimera = new CountDownLatch(1);
        CountDownLatch segundaIniciada = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        try {
            Future<?> primeraFutura = executor.submit(() ->
                    transaction.executeWithoutResult(status -> {
                        primera.run();
                        primeraLista.countDown();
                        esperar(liberarPrimera);
                    }));

            assertTrue(primeraLista.await(10, TimeUnit.SECONDS),
                    "La primera operación no alcanzó el estado retenido");

            Future<?> segundaFutura = executor.submit(() -> {
                segundaIniciada.countDown();
                segunda.run();
            });
            assertTrue(segundaIniciada.await(10, TimeUnit.SECONDS));
            esperarBloqueoSqlServerPedidosOPagos();
            assertFalse(segundaFutura.isDone(),
                    "La segunda operación debe esperar el bloqueo de la primera");

            liberarPrimera.countDown();
            primeraFutura.get(15, TimeUnit.SECONDS);
            segundaFutura.get(15, TimeUnit.SECONDS);
        } finally {
            liberarPrimera.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private void esperarBloqueoSqlServerPedidosOPagos() throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < limite) {
            Integer bloqueos = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM sys.dm_exec_requests AS r
                    CROSS APPLY sys.dm_exec_sql_text(r.sql_handle) AS t
                    WHERE r.session_id <> @@SPID
                      AND r.blocking_session_id > 0
                      AND r.wait_type LIKE 'LCK%'
                      AND (t.text LIKE '%pedidos%' OR t.text LIKE '%pagos%')
                    """, Integer.class);
            if (bloqueos != null && bloqueos > 0) {
                return;
            }
            Thread.sleep(25);
        }
        fail("SQL Server no mostró una operación de pago o pedido bloqueada");
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

    private Pedido crearPedido(EstadoPedido estado, LocalDateTime fechaHora) {
        Pedido pedido = new Pedido();
        pedido.setClienteOMesa("Pedido de concurrencia");
        pedido.setEstado(estado);
        pedido.setTotal(new BigDecimal("25.00"));
        pedido.setFechaHora(fechaHora);
        return pedidoRepository.saveAndFlush(pedido);
    }

    private ProcesarPagoDTO dto(Long pedidoId) {
        ProcesarPagoDTO dto = new ProcesarPagoDTO();
        dto.setPedidoId(pedidoId);
        dto.setMetodoPago(MetodoPago.TARJETA);
        return dto;
    }

    private Pago crearPago(BigDecimal monto, EstadoPago estado) {
        Pedido pedido = new Pedido();
        pedido.setClienteOMesa("Mesa de prueba SQL Server");
        pedido.setEstado(EstadoPedido.RECIBIDO);
        pedido.setTotal(monto);
        pedido = pedidoRepository.saveAndFlush(pedido);

        Pago pago = new Pago();
        pago.setPedido(pedido);
        pago.setMontoTotal(monto);
        pago.setMetodoPago(MetodoPago.EFECTIVO);
        pago.setEstado(estado);
        return pagoRepository.saveAndFlush(pago);
    }
}
