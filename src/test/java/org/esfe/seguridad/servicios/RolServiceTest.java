package org.esfe.seguridad.servicios;

import org.esfe.seguridad.modelos.Rol;
import org.esfe.seguridad.repositorios.RolRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RolServiceTest {

    @Mock
    private RolRepository rolRepository;

    @InjectMocks
    private RolService rolService;

    @Test
    void obtenerRolesDevuelveTodosLosRoles() {
        List<Rol> roles = List.of(
                Rol.builder().id(1).nombre("ADMIN").build(),
                Rol.builder().id(2).nombre("USER").build());
        when(rolRepository.findAll()).thenReturn(roles);

        List<Rol> resultado = rolService.obtenerRoles();

        assertEquals(roles, resultado);
        verify(rolRepository).findAll();
    }

    @Test
    void obtenerPorIdDevuelveElRolExistente() {
        Rol rol = Rol.builder().id(3).nombre("ADMIN").build();
        when(rolRepository.findById(3)).thenReturn(Optional.of(rol));

        Rol resultado = rolService.obtenerPorId(3);

        assertEquals(rol, resultado);
        verify(rolRepository).findById(3);
    }

    @Test
    void obtenerPorIdLanzaExcepcionCuandoElRolNoExiste() {
        when(rolRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> rolService.obtenerPorId(99));
        verify(rolRepository).findById(99);
    }
}