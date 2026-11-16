package org.esfe.easymenu.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.core.userdetails.User;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String TEST_SECRET_BASE64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(JwtUtil.class);

    @Test
    void noArrancaSiNoHaySecretoConfigurado() {
        contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void noArrancaConSecretoVacio() {
        contextRunner
                .withPropertyValues("jwt.secret=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void noArrancaConBase64Invalido() {
        contextRunner
                .withPropertyValues("jwt.secret=%%%not-base64%%%")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void noArrancaConClaveBase64InferiorA32Bytes() {
        String shortKey = Base64.getEncoder().encodeToString(
                "short-key".getBytes(StandardCharsets.UTF_8)
        );
        contextRunner
                .withPropertyValues("jwt.secret=" + shortKey)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void arrancaConBase64ValidoQueDecodificaA32Bytes() {
        contextRunner
                .withPropertyValues("jwt.secret=" + TEST_SECRET_BASE64)
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(JwtUtil.class));
    }

    @Test
    void firmaConHs256YValidaElTokenGenerado() {
        JwtUtil jwtUtil = new JwtUtil(TEST_SECRET_BASE64);
        var user = User.withUsername("cajero@easymenu.com").password("hash").roles("CAJERO").build();
        String token = jwtUtil.generarToken(user);
        var parsedToken = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(Base64.getDecoder().decode(TEST_SECRET_BASE64)))
                .build()
                .parseClaimsJws(token);

        assertThat(parsedToken.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(jwtUtil.obtenerUsuarioDelToken(token)).isEqualTo("cajero@easymenu.com");
        assertThat(jwtUtil.validarToken(token, user)).isTrue();
        assertThat(parsedToken.getBody().getExpiration()).isAfter(new Date());
    }

    @Test
    void validaJwtAnteriorCuandoBase64ConservaLosMismosBytesDeClave() {
        String previousSecret = "test-only-easymenu-jwt-key-7f4a91c6d3e2b805";
        byte[] previousKeyBytes = previousSecret.getBytes(StandardCharsets.UTF_8);
        String migratedSecret = Base64.getEncoder().encodeToString(previousKeyBytes);
        JwtUtil jwtUtil = new JwtUtil(migratedSecret);
        var user = User.withUsername("cajero@easymenu.com").password("hash").roles("CAJERO").build();
        Date now = new Date();
        String previousToken = Jwts.builder()
                .setSubject(user.getUsername())
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + 60_000))
                .signWith(Keys.hmacShaKeyFor(previousKeyBytes), SignatureAlgorithm.HS256)
                .compact();

        assertThat(jwtUtil.validarToken(previousToken, user)).isTrue();
    }
}
