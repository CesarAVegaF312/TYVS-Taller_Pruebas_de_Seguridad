package edu.unisabana.tyvs.registry.delivery.rest;

import edu.unisabana.tyvs.registry.infrastructure.busqueda.BuscadorDeVotantes;
import edu.unisabana.tyvs.registry.infrastructure.persistence.RegistryRecord;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Busqueda publica de votantes inscritos.
 *
 * La aplicacion cablea aqui la implementacion SEGURA. La vulnerable existe en
 * el codigo pero no se expone por HTTP: se explota desde las pruebas del
 * modulo 2, que es donde debe explotarse.
 *
 * MINIMIZACION DE DATOS (y es una decision de seguridad, no de formato):
 * la busqueda devuelve el nombre y si la persona esta inscrita. NO devuelve
 * el documento ni la edad, aunque la consulta los traiga. Un endpoint publico
 * de busqueda que devuelve el documento completo es un directorio de datos
 * personales servido en bandeja, y el hecho de que la consulta ya tenga el
 * dato a mano no es razon para publicarlo.
 */
@RestController
@RequestMapping("/buscar")
public class BusquedaController {

    private final BuscadorDeVotantes buscador;

    public BusquedaController(BuscadorDeVotantes buscador) {
        this.buscador = buscador;
    }

    @GetMapping
    public List<String> buscar(@RequestParam("nombre") String nombre) throws Exception {
        List<RegistryRecord> encontrados = buscador.buscarPorNombre(nombre);

        return encontrados.stream()
                .map(RegistryRecord::getName)
                .collect(Collectors.toList());
    }
}
