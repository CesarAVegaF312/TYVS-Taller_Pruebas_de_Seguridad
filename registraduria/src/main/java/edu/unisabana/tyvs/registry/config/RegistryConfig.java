package edu.unisabana.tyvs.registry.config;

import edu.unisabana.tyvs.registry.application.port.out.RegistryRepositoryPort;
import edu.unisabana.tyvs.registry.application.usecase.Registry;
import edu.unisabana.tyvs.registry.infrastructure.busqueda.BuscadorDeVotantes;
import edu.unisabana.tyvs.registry.infrastructure.busqueda.BuscadorSeguro;
import edu.unisabana.tyvs.registry.infrastructure.persistence.RegistryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cableado de la aplicacion (composition root).
 *
 * El dominio y el caso de uso no conocen Spring: es esta clase la que decide
 * que implementacion concreta del puerto se inyecta. Por eso Registry se puede
 * probar unitariamente con un mock sin levantar el contexto.
 *
 * La URL JDBC se lee de una propiedad para que cada prueba de integracion pueda
 * usar su propia base de datos y no contaminar a las demas.
 */
@Configuration
public class RegistryConfig {

    @Bean
    public RegistryRepositoryPort registryRepositoryPort(
            @Value("${registry.jdbc-url:jdbc:h2:mem:regdb;DB_CLOSE_DELAY=-1}") String jdbcUrl)
            throws Exception {
        RegistryRepository repo = new RegistryRepository(jdbcUrl);
        repo.initSchema();
        return repo;
    }

    @Bean
    public Registry registry(RegistryRepositoryPort port) {
        return new Registry(port);
    }

    /**
     * Se cablea SIEMPRE la implementacion segura.
     *
     * BuscadorVulnerable no tiene @Bean ni se referencia aqui: existe para que
     * las pruebas del modulo 2 lo exploten, y no hay ninguna propiedad de
     * configuracion que permita activarlo. Es deliberado. Un interruptor del
     * tipo "modo inseguro" acaba encendido en produccion algun viernes.
     */
    @Bean
    public BuscadorDeVotantes buscadorDeVotantes(
            @Value("${registry.jdbc-url:jdbc:h2:mem:regdb;DB_CLOSE_DELAY=-1}") String jdbcUrl) {
        return new BuscadorSeguro(jdbcUrl);
    }
}
