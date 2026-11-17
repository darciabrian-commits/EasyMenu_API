package org.esfe.easymenu.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.esfe.easymenu.dtos.ErrorResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

public class PublicPedidoRateLimitFilter extends OncePerRequestFilter {

    static final int MAX_REQUESTS_PER_WINDOW = 60;
    private static final long WINDOW_MILLIS = 60_000;
    private static final int MAX_CLIENTS = 10_000;
    private static final Pattern CONSULTA_PUBLICA =
            Pattern.compile("^/api/pedidos/codigo/[^/]+$");
    private static final Pattern CANCELACION_PUBLICA =
            Pattern.compile("^/api/pedidos/codigo/[^/]+/cancelar$");

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong requestCount = new AtomicLong();

    public PublicPedidoRateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !("GET".equals(request.getMethod()) && CONSULTA_PUBLICA.matcher(path).matches())
                && !("PATCH".equals(request.getMethod())
                        && CANCELACION_PUBLICA.matcher(path).matches());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!permitirSolicitud(request.getRemoteAddr(), System.currentTimeMillis())) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(
                    response.getOutputStream(),
                    new ErrorResponseDTO(
                            HttpStatus.TOO_MANY_REQUESTS.value(),
                            "Demasiadas solicitudes. Intente más tarde.",
                            LocalDateTime.now()
                    )
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean permitirSolicitud(String clientAddress, long now) {
        String key = clientAddress == null || clientAddress.isBlank()
                ? "unknown"
                : clientAddress;

        if (!windows.containsKey(key) && windows.size() >= MAX_CLIENTS) {
            eliminarVentanasExpiradas(now);
            if (windows.size() >= MAX_CLIENTS) {
                return false;
            }
        }

        AtomicBoolean permitido = new AtomicBoolean();
        windows.compute(key, (address, current) -> {
            Window active = current == null || now - current.startedAtMillis() >= WINDOW_MILLIS
                    ? new Window(now, 0)
                    : current;
            if (active.requestCount() >= MAX_REQUESTS_PER_WINDOW) {
                permitido.set(false);
                return active;
            }
            permitido.set(true);
            return new Window(active.startedAtMillis(), active.requestCount() + 1);
        });

        if ((requestCount.incrementAndGet() & 255) == 0) {
            eliminarVentanasExpiradas(now);
        }
        return permitido.get();
    }

    private void eliminarVentanasExpiradas(long now) {
        windows.entrySet().removeIf(entry ->
                now - entry.getValue().startedAtMillis() >= WINDOW_MILLIS);
    }

    private record Window(long startedAtMillis, int requestCount) {
    }
}
