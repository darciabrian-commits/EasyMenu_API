package org.esfe.easymenu.servicios.implementaciones;

import org.esfe.easymenu.dtos.ProductoRequestDTO;
import org.esfe.easymenu.excepcion.RecursoNoEncontradoException;
import org.esfe.easymenu.excepcion.ReglaNegocioException;
import org.esfe.easymenu.modelos.Producto;
import org.esfe.easymenu.repositorios.ProductoRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductoServiceImplTest {

    @Mock
    private ProductoRepository repositorio;

    @InjectMocks
    private ProductoServiceImpl servicio;

    // Datos ficticios: estas pruebas no modifican la base de datos.
    private Producto productoEjemplo() {
        Producto producto = new Producto();
        producto.setId(7L);
        producto.setNombre("Pupusa QA");
        producto.setDescripcion("Prueba");
        producto.setPrecio(new BigDecimal("2.50"));
        producto.setCategoria("PLATILLO");
        producto.setActivo(true);
        producto.setDisponible(true);
        return producto;
    }

    private ProductoRequestDTO datosProducto(String nombre) {
        return new ProductoRequestDTO(
                nombre,
                "Producto de prueba",
                new BigDecimal("2.50"),
                "PLATILLO"
        );
    }

    // PRUEBA 1 - ORIGINAL DE ANA
    @Test
    void crearConNombreDuplicadoLanzaError() {
        var dto = new ProductoRequestDTO(
                "Pupusa QA",
                "Prueba",
                new BigDecimal("2.50"),
                "PLATILLO"
        );

        when(repositorio.existsByNombreIgnoreCase("Pupusa QA"))
                .thenReturn(true);

        assertThrows(
                ReglaNegocioException.class,
                () -> servicio.crear(dto)
        );

        verify(repositorio, never()).save(any());
    }

    // PRUEBA 2 - CREAR PRODUCTO NUEVO
    @Test
    void crearProductoNuevoGuardaYDevuelveDatos() {
        var dto = datosProducto("Tamal QA");

        when(repositorio.save(any(Producto.class)))
                .thenAnswer(invocacion -> {
                    Producto guardado = invocacion.getArgument(0);
                    guardado.setId(8L);
                    return guardado;
                });

        var resultado = servicio.crear(dto);

        assertEquals(Long.valueOf(8L), resultado.id());
        assertEquals("Tamal QA", resultado.nombre());
        assertEquals(new BigDecimal("2.50"), resultado.precio());
        assertTrue(resultado.activo());
        assertTrue(resultado.disponible());

        verify(repositorio).save(any(Producto.class));
    }

    // PRUEBA 3 - CONSULTAR TODOS LOS PRODUCTOS
    @Test
    void obtenerTodosDevuelveProductosActivos() {
        when(repositorio.findByActivoTrue())
                .thenReturn(List.of(productoEjemplo()));

        var resultado = servicio.obtenerTodos();

        assertEquals(1, resultado.size());
        assertEquals("Pupusa QA", resultado.get(0).nombre());

        verify(repositorio).findByActivoTrue();
    }

    // PRUEBA 4 - CONSULTAR LISTA VACIA
    @Test
    void obtenerTodosSinProductosDevuelveListaVacia() {
        when(repositorio.findByActivoTrue())
                .thenReturn(List.of());

        var resultado = servicio.obtenerTodos();

        assertTrue(resultado.isEmpty());
    }

    // PRUEBA 5 - CONSULTAR PRODUCTOS DISPONIBLES
    @Test
    void obtenerDisponiblesDevuelveProductosDisponibles() {
        when(repositorio.findByActivoTrueAndDisponibleTrue())
                .thenReturn(List.of(productoEjemplo()));

        var resultado = servicio.obtenerDisponibles();

        assertEquals(1, resultado.size());
        assertTrue(resultado.get(0).disponible());

        verify(repositorio).findByActivoTrueAndDisponibleTrue();
    }

    // PRUEBA 6 - SIN PRODUCTOS DISPONIBLES
    @Test
    void obtenerDisponiblesSinResultadosDevuelveListaVacia() {
        when(repositorio.findByActivoTrueAndDisponibleTrue())
                .thenReturn(List.of());

        assertTrue(servicio.obtenerDisponibles().isEmpty());
    }

    // PRUEBA 7 - FILTRAR POR CATEGORIA
    @Test
    void obtenerPorCategoriaDevuelveProductos() {
        when(repositorio.findByActivoTrueAndCategoriaIgnoreCase("PLATILLO"))
                .thenReturn(List.of(productoEjemplo()));

        var resultado = servicio.obtenerPorCategoria("PLATILLO");

        assertEquals(1, resultado.size());
        assertEquals("PLATILLO", resultado.get(0).categoria());
    }

    // PRUEBA 8 - CATEGORIA SIN RESULTADOS
    @Test
    void obtenerPorCategoriaSinCoincidenciasDevuelveListaVacia() {
        when(repositorio.findByActivoTrueAndCategoriaIgnoreCase("BEBIDA"))
                .thenReturn(List.of());

        assertTrue(servicio.obtenerPorCategoria("BEBIDA").isEmpty());
    }

    // PRUEBA 9 - BUSCAR PRODUCTO EXISTENTE
    @Test
    void obtenerPorIdActivoDevuelveProducto() {
        when(repositorio.findById(7L))
                .thenReturn(Optional.of(productoEjemplo()));

        var resultado = servicio.obtenerPorId(7L);

        assertEquals(Long.valueOf(7L), resultado.id());
        assertEquals("Pupusa QA", resultado.nombre());
    }

    // PRUEBA 10 - BUSCAR PRODUCTO INEXISTENTE
    @Test
    void obtenerPorIdInexistenteLanzaError() {
        when(repositorio.findById(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                RecursoNoEncontradoException.class,
                () -> servicio.obtenerPorId(99L)
        );
    }

    // PRUEBA 11 - BUSCAR PRODUCTO INACTIVO
    @Test
    void obtenerPorIdInactivoLanzaError() {
        Producto inactivo = productoEjemplo();
        inactivo.setActivo(false);

        when(repositorio.findById(7L))
                .thenReturn(Optional.of(inactivo));

        assertThrows(
                RecursoNoEncontradoException.class,
                () -> servicio.obtenerPorId(7L)
        );
    }

    // PRUEBA 12 - ACTUALIZAR PRODUCTO
    @Test
    void actualizarProductoModificaSusDatos() {
        Producto producto = productoEjemplo();
        var dto = datosProducto("Pupusa Especial QA");

        when(repositorio.findById(7L))
                .thenReturn(Optional.of(producto));

        when(repositorio.save(producto))
                .thenReturn(producto);

        var resultado = servicio.actualizar(7L, dto);

        assertEquals("Pupusa Especial QA", resultado.nombre());
        assertEquals("Producto de prueba", resultado.descripcion());
        assertEquals("Pupusa Especial QA", producto.getNombre());

        verify(repositorio).existsByNombreIgnoreCaseAndIdNot(
                "Pupusa Especial QA",
                7L
        );

        verify(repositorio).save(producto);
    }

    // PRUEBA 13 - ACTUALIZAR CON NOMBRE DUPLICADO
    @Test
    void actualizarConNombreDuplicadoLanzaError() {
        Producto producto = productoEjemplo();
        var dto = datosProducto("Tamal QA");

        when(repositorio.findById(7L))
                .thenReturn(Optional.of(producto));

        when(repositorio.existsByNombreIgnoreCaseAndIdNot("Tamal QA", 7L))
                .thenReturn(true);

        assertThrows(
                ReglaNegocioException.class,
                () -> servicio.actualizar(7L, dto)
        );

        verify(repositorio, never()).save(any());
    }

    // PRUEBA 14 - ACTUALIZAR PRODUCTO INEXISTENTE
    @Test
    void actualizarProductoInexistenteLanzaError() {
        when(repositorio.findById(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                RecursoNoEncontradoException.class,
                () -> servicio.actualizar(
                        99L,
                        datosProducto("Tamal QA")
                )
        );

        verify(repositorio, never()).save(any());
    }

    // PRUEBA 15 - ELIMINAR PRODUCTO
    @Test
    void eliminarProductoLoMarcaInactivo() {
        Producto producto = productoEjemplo();

        when(repositorio.findById(7L))
                .thenReturn(Optional.of(producto));

        servicio.eliminar(7L);

        assertFalse(producto.getActivo());

        verify(repositorio).save(producto);
    }

    // PRUEBA 16 - ELIMINAR PRODUCTO INEXISTENTE
    @Test
    void eliminarProductoInexistenteLanzaError() {
        when(repositorio.findById(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                RecursoNoEncontradoException.class,
                () -> servicio.eliminar(99L)
        );

        verify(repositorio, never()).save(any());
    }

    // PRUEBA 17 - MARCAR PRODUCTO AGOTADO
    @Test
    void cambiarDisponibilidadAFalsoMarcaAgotado() {
        Producto producto = productoEjemplo();

        when(repositorio.findById(7L))
                .thenReturn(Optional.of(producto));

        when(repositorio.save(producto))
                .thenReturn(producto);

        var resultado = servicio.cambiarEstadoDisponibilidad(
                7L,
                false
        );

        assertFalse(resultado.disponible());

        verify(repositorio).save(producto);
    }

    // PRUEBA 18 - MARCAR PRODUCTO DISPONIBLE
    @Test
    void cambiarDisponibilidadAVerdaderoMarcaDisponible() {
        Producto producto = productoEjemplo();
        producto.setDisponible(false);

        when(repositorio.findById(7L))
                .thenReturn(Optional.of(producto));

        when(repositorio.save(producto))
                .thenReturn(producto);

        var resultado = servicio.cambiarEstadoDisponibilidad(
                7L,
                true
        );

        assertTrue(resultado.disponible());

        verify(repositorio).save(producto);
    }
}