package edu.unisabana.tyvs.registry.infrastructure.busqueda;

import edu.unisabana.tyvs.registry.infrastructure.persistence.RegistryRecord;

import java.util.List;

/**
 * Busca votantes inscritos por una parte de su nombre.
 *
 * Hay DOS implementaciones a proposito. Son funcionalmente identicas: ante una
 * entrada normal devuelven exactamente lo mismo, y cualquier prueba funcional
 * las da por buenas a las dos.
 *
 * La diferencia solo aparece ante una entrada hostil, y ese es justamente el
 * punto del taller: las pruebas funcionales usan las entradas que el
 * programador imagino. Un atacante usa las que no.
 */
public interface BuscadorDeVotantes {

    /**
     * @param fragmentoDeNombre texto a buscar dentro del nombre.
     * @return los registros cuyo nombre contiene el fragmento.
     */
    List<RegistryRecord> buscarPorNombre(String fragmentoDeNombre) throws Exception;
}
