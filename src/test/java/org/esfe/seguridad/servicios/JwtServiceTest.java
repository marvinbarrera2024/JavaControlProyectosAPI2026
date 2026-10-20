package org.esfe.seguridad.servicios;

import io.jsonwebtoken.JwtException;
import org.esfe.seguridad.modelos.Rol;
import org.esfe.seguridad.modelos.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService();

    @BeforeEach
    void setUp() {
        String secret = Base64.getEncoder().encodeToString(
                "clave-de-prueba-de-32-bytes-minimo".getBytes(StandardCharsets.UTF_8));
        ReflectionTestUtils.setField(jwtService, "SECRET_KEY", secret);
    }

    @Test
    void getTokenIncluyeLoginYRolDelUsuario() {
        Usuario usuario = crearUsuario("ana", "ADMIN");

        String token = jwtService.getToken(usuario);

        assertEquals("ana", jwtService.getUsernameFromToken(token));
        List<?> roles = jwtService.getClaim(token, claims -> claims.get("roles", List.class));
        assertEquals(List.of("ADMIN"), roles);
    }

    @Test
    void isTokenValidSoloAceptaElMismoUsuario() {
        String token = jwtService.getToken(crearUsuario("ana", "USER"));

        assertTrue(jwtService.isTokenValid(token, crearUsuario("ana", "USER")));
        assertFalse(jwtService.isTokenValid(token, crearUsuario("otra", "USER")));
    }

    @Test
    void rechazaTokenManipulado() {
        String token = jwtService.getToken(crearUsuario("ana", "USER"));

        assertThrows(JwtException.class, () -> jwtService.getUsernameFromToken(token + "invalido"));
    }

    private Usuario crearUsuario(String login, String nombreRol) {
        return Usuario.builder()
                .login(login)
                .rol(Rol.builder().nombre(nombreRol).build())
                .build();
    }
}