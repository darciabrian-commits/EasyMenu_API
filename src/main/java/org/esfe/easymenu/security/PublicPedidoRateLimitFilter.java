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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Enumeration;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class PublicPedidoRateLimitFilter extends OncePerRequestFilter {

    static final int MAX_REQUESTS_PER_WINDOW = 60;
    static final int MAX_CREATE_REQUESTS_PER_WINDOW = 30;
    private static final long DEFAULT_WINDOW_MILLIS = 60_000;
    private static final int MAX_CLIENTS = 10_000;
    private static final int MAX_FORWARDED_ADDRESSES = 10;
    private static final int MAX_FORWARDED_HEADER_LENGTH = 512;
    private static final Pattern CONSULTA_PUBLICA =
            Pattern.compile("^/api/pedidos/codigo/[^/]+$");
    private static final Pattern CANCELACION_PUBLICA =
            Pattern.compile("^/api/pedidos/codigo/[^/]+/cancelar$");
    private static final Pattern IPV4 = Pattern.compile("[0-9.]+");
    private static final Pattern IPV6 = Pattern.compile("[0-9a-fA-F:.]+");

    private final ObjectMapper objectMapper;
    private final int createLimit;
    private final int publicLimit;
    private final long windowMillis;
    private final List<Cidr> trustedProxies;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong requestCount = new AtomicLong();

    public PublicPedidoRateLimitFilter(ObjectMapper objectMapper) {
        this(
                objectMapper,
                MAX_CREATE_REQUESTS_PER_WINDOW,
                MAX_REQUESTS_PER_WINDOW,
                DEFAULT_WINDOW_MILLIS,
                List.of()
        );
    }

    PublicPedidoRateLimitFilter(
            ObjectMapper objectMapper,
            int createLimit,
            int publicLimit,
            long windowMillis,
            List<String> trustedProxyCidrs
    ) {
        if (createLimit < 1 || publicLimit < 1 || windowMillis < 1) {
            throw new IllegalArgumentException("Los límites y la ventana deben ser mayores que cero");
        }
        this.objectMapper = objectMapper;
        this.createLimit = createLimit;
        this.publicLimit = publicLimit;
        this.windowMillis = windowMillis;
        this.trustedProxies = trustedProxyCidrs.stream()
                .filter(cidr -> !cidr.isBlank())
                .map(Cidr::parse)
                .toList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !("POST".equals(request.getMethod()) && "/api/pedidos".equals(path))
                && !("GET".equals(request.getMethod())
                        && CONSULTA_PUBLICA.matcher(path).matches())
                && !("PATCH".equals(request.getMethod())
                        && CANCELACION_PUBLICA.matcher(path).matches());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String clientAddress = resolveClientAddress(request);
        if (clientAddress == null) {
            writeError(
                    response,
                    HttpStatus.BAD_REQUEST,
                    "No se pudo determinar una dirección de cliente válida desde el proxy confiable."
            );
            return;
        }

        boolean createRequest = "POST".equals(request.getMethod());
        int limit = createRequest ? createLimit : publicLimit;
        String bucket = createRequest ? "crear" : "consulta";
        LimitDecision decision = permitRequest(
                clientAddress + ":" + bucket,
                limit,
                System.currentTimeMillis()
        );
        if (!decision.allowed()) {
            response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
            writeError(
                    response,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiadas solicitudes. Intente más tarde."
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private LimitDecision permitRequest(String key, int limit, long now) {
        if (!windows.containsKey(key) && windows.size() >= MAX_CLIENTS * 2) {
            removeExpiredWindows(now);
            if (windows.size() >= MAX_CLIENTS * 2) {
                return new LimitDecision(false, retryAfterSeconds(windowMillis));
            }
        }

        AtomicReference<LimitDecision> decisionRef = new AtomicReference<>();
        windows.compute(key, (address, current) -> {
            Window active = current == null || now - current.startedAtMillis() >= windowMillis
                    ? new Window(now, 0)
                    : current;
            if (active.requestCount() >= limit) {
                long remaining = windowMillis - (now - active.startedAtMillis());
                decisionRef.set(new LimitDecision(false, retryAfterSeconds(remaining)));
                return active;
            }
            decisionRef.set(new LimitDecision(true, 0));
            return new Window(active.startedAtMillis(), active.requestCount() + 1);
        });

        if ((requestCount.incrementAndGet() & 255) == 0) {
            removeExpiredWindows(now);
        }
        return decisionRef.get();
    }

    private long retryAfterSeconds(long remainingMillis) {
        return Math.max(1, (remainingMillis + 999) / 1000);
    }

    private void removeExpiredWindows(long now) {
        windows.entrySet().removeIf(entry ->
                now - entry.getValue().startedAtMillis() >= windowMillis);
    }

    private String resolveClientAddress(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        byte[] remoteBytes = parseAddress(remoteAddress);
        if (remoteBytes == null) {
            return null;
        }
        if (!isTrustedProxy(remoteBytes)) {
            return !request.getHeaders("X-Forwarded-For").hasMoreElements()
                    ? addressText(remoteBytes)
                    : null;
        }

        String forwardedFor = forwardedForHeader(request);
        if (forwardedFor == null || forwardedFor.length() > MAX_FORWARDED_HEADER_LENGTH) {
            return null;
        }
        String[] forwardedAddresses = forwardedFor.split(",", -1);
        if (forwardedAddresses.length > MAX_FORWARDED_ADDRESSES) {
            return null;
        }

        for (int index = forwardedAddresses.length - 1; index >= 0; index--) {
            byte[] address = parseAddress(forwardedAddresses[index].trim());
            if (address == null) {
                return null;
            }
            if (!isTrustedProxy(address)) {
                return addressText(address);
            }
        }
        return null;
    }

    private String forwardedForHeader(HttpServletRequest request) {
        Enumeration<String> values = request.getHeaders("X-Forwarded-For");
        StringJoiner joined = new StringJoiner(",");
        while (values.hasMoreElements()) {
            joined.add(values.nextElement());
            if (joined.length() > MAX_FORWARDED_HEADER_LENGTH) {
                return null;
            }
        }
        return joined.length() == 0 ? null : joined.toString();
    }

    private boolean isTrustedProxy(byte[] address) {
        return trustedProxies.stream().anyMatch(proxy -> proxy.contains(address));
    }

    private byte[] parseAddress(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String address = value.trim();
        if (IPV4.matcher(address).matches()) {
            String[] octets = address.split("\\.", -1);
            if (octets.length != 4) {
                return null;
            }
            byte[] parsed = new byte[4];
            try {
                for (int i = 0; i < octets.length; i++) {
                    int octet = Integer.parseInt(octets[i]);
                    if (octet > 255) {
                        return null;
                    }
                    parsed[i] = (byte) octet;
                }
                return parsed;
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        if (!IPV6.matcher(address).matches() || !address.contains(":")) {
            return null;
        }
        try {
            byte[] parsed = InetAddress.getByName(address).getAddress();
            return parsed.length == 16 ? parsed : null;
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private String addressText(byte[] address) {
        try {
            return InetAddress.getByAddress(address).getHostAddress();
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Dirección IP no válida", exception);
        }
    }

    private void writeError(
            HttpServletResponse response,
            HttpStatus status,
            String message
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getOutputStream(),
                new ErrorResponseDTO(status.value(), message, LocalDateTime.now())
        );
    }

    private record LimitDecision(boolean allowed, long retryAfterSeconds) {
    }

    private record Window(long startedAtMillis, int requestCount) {
    }

    private record Cidr(byte[] network, int prefixLength) {

        static Cidr parse(String value) {
            String[] parts = value.trim().split("/", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("CIDR de proxy confiable no válido: " + value);
            }
            byte[] network = parseIp(parts[0]);
            int prefix;
            try {
                prefix = Integer.parseInt(parts[1]);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("CIDR de proxy confiable no válido: " + value, exception);
            }
            if (prefix < 0 || prefix > network.length * 8) {
                throw new IllegalArgumentException("CIDR de proxy confiable no válido: " + value);
            }
            return new Cidr(network, prefix);
        }

        boolean contains(byte[] address) {
            if (network.length != address.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (network[i] != address[i]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xff << (8 - remainingBits);
            return (network[fullBytes] & mask) == (address[fullBytes] & mask);
        }

        private static byte[] parseIp(String value) {
            String address = value.trim();
            if (IPV4.matcher(address).matches()) {
                String[] octets = address.split("\\.", -1);
                if (octets.length != 4) {
                    throw new IllegalArgumentException("IP de proxy confiable no válida: " + value);
                }
                byte[] parsed = new byte[4];
                try {
                    for (int i = 0; i < octets.length; i++) {
                        int octet = Integer.parseInt(octets[i]);
                        if (octet > 255) {
                            throw new IllegalArgumentException("IP de proxy confiable no válida: " + value);
                        }
                        parsed[i] = (byte) octet;
                    }
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("IP de proxy confiable no válida: " + value, exception);
                }
                return parsed;
            }
            if (!IPV6.matcher(address).matches() || !address.contains(":")) {
                throw new IllegalArgumentException("IP de proxy confiable no válida: " + value);
            }
            try {
                byte[] parsed = InetAddress.getByName(address).getAddress();
                if (parsed.length != 16) {
                    throw new IllegalArgumentException("IP de proxy confiable no válida: " + value);
                }
                return parsed;
            } catch (UnknownHostException exception) {
                throw new IllegalArgumentException("IP de proxy confiable no válida: " + value, exception);
            }
        }
    }
}
