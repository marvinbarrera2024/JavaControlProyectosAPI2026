package org.esfe.seguridad.servicios;

import org.esfe.seguridad.dtos.UsuarioLogin;
import org.esfe.seguridad.dtos.UsuarioRegistrar;
import org.esfe.seguridad.dtos.UsuarioToken;
import org.esfe.seguridad.modelos.Rol;
import org.esfe.seguridad.modelos.Usuario;
import org.esfe.seguridad.repositorios.RolRepository;
import org.esfe.seguridad.repositorios.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository userRepository;

    @Mock
        private RolRepository rolRepository;

        private RolService rolService;

        private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    private UsuarioService usuarioService;

        @BeforeEach
        void setUp() {
                rolService = new RolService();
                ReflectionTestUtils.setField(rolService, "rolRepository", rolRepository);

                jwtService = new JwtService();
                String secret = Base64.getEncoder().encodeToString(
                                "clave-de-prueba-de-32-bytes-minimo".getBytes(StandardCharsets.UTF_8));
                ReflectionTestUtils.setField(jwtService, "SECRET_KEY", secret);

                usuarioService = new UsuarioService();
                ReflectionTestUtils.setField(usuarioService, "userRepository", userRepository);
                ReflectionTestUtils.setField(usuarioService, "rolService", rolService);
                ReflectionTestUtils.setField(usuarioService, "jwtService", jwtService);
                ReflectionTestUtils.setField(usuarioService, "passwordEncoder", passwordEncoder);
                ReflectionTestUtils.setField(usuarioService, "authenticationManager", authenticationManager);
        }

    @Test
    void loginAutenticaAlUsuarioYDevuelveToken() {
        UsuarioLogin login = UsuarioLogin.builder()
                .login("ana")
                .clave("clave")
                .build();
        Usuario usuario = Usuario.builder()
                .login("ana")
                .rol(Rol.builder().nombre("USER").build())
                .build();
        when(userRepository.findByLogin("ana")).thenReturn(Optional.of(usuario));

        UsuarioToken resultado = usuarioService.login(login);

        assertTrue(jwtService.isTokenValid(resultado.getToken(), usuario));
        ArgumentCaptor<Authentication> authenticationCaptor = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(authenticationCaptor.capture());
        UsernamePasswordAuthenticationToken authentication =
                (UsernamePasswordAuthenticationToken) authenticationCaptor.getValue();
        assertEquals("ana", authentication.getPrincipal());
        assertEquals("clave", authentication.getCredentials());
        verify(userRepository).findByLogin("ana");
    }

    @Test
    void loginNoBuscaUsuarioCuandoLaAutenticacionFalla() {
        UsuarioLogin login = UsuarioLogin.builder()
                .login("ana")
                .clave("incorrecta")
                .build();
        doThrow(new BadCredentialsException("Credenciales invalidas"))
                .when(authenticationManager).authenticate(any(Authentication.class));

        assertThrows(BadCredentialsException.class, () -> usuarioService.login(login));

        verify(userRepository, never()).findByLogin(any(String.class));
    }

    @Test
    void registroCodificaClaveAsignaRolGuardaUsuarioYDevuelveToken() {
        UsuarioRegistrar registro = UsuarioRegistrar.builder()
                .nombre("Ana")
                .apellido("Lopez")
                .telefono("5551234")
                .login("ana")
                .clave("clave-plana")
                .rolId(2)
                .build();
        Rol rol = Rol.builder().id(2).nombre("USER").build();
        when(passwordEncoder.encode("clave-plana")).thenReturn("clave-cifrada");
        when(rolRepository.findById(2)).thenReturn(Optional.of(rol));

        UsuarioToken resultado = usuarioService.registro(registro);

        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(userRepository).save(usuarioCaptor.capture());
        Usuario usuarioGuardado = usuarioCaptor.getValue();
        assertEquals("Ana", usuarioGuardado.getNombre());
        assertEquals("Lopez", usuarioGuardado.getApellido());
        assertEquals("5551234", usuarioGuardado.getTelefono());
        assertEquals("ana", usuarioGuardado.getLogin());
        assertEquals("clave-cifrada", usuarioGuardado.getClave());
        assertEquals(rol, usuarioGuardado.getRol());
        assertTrue(jwtService.isTokenValid(resultado.getToken(), usuarioGuardado));
        verify(passwordEncoder).encode("clave-plana");
        verify(rolRepository).findById(2);
    }
}