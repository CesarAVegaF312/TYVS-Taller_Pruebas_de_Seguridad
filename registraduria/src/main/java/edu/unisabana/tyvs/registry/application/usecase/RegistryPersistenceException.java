package edu.unisabana.tyvs.registry.application.usecase;

/**
 * Fallo de infraestructura al persistir un registro.
 *
 * Existe para que el caso de uso no filtre SQLException hacia arriba: la capa
 * de entrega decide como traducirla a HTTP sin tener que conocer JDBC.
 */
public class RegistryPersistenceException extends RuntimeException {

    public RegistryPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
