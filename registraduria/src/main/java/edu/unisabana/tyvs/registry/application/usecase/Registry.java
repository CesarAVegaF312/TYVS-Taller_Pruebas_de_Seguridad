package edu.unisabana.tyvs.registry.application.usecase;

import edu.unisabana.tyvs.registry.application.port.out.RegistryRepositoryPort;
import edu.unisabana.tyvs.registry.domain.model.Person;
import edu.unisabana.tyvs.registry.domain.model.RegisterResult;

/**
 * Caso de uso: registrar un votante.
 *
 * Depende del PUERTO (RegistryRepositoryPort), no de una implementacion
 * concreta. Eso es lo que permite probarlo de dos formas distintas:
 *
 * - con un mock del puerto -> prueba UNITARIA (rapida, sin base de datos)
 * - con RegistryRepository sobre H2 -> prueba de INTEGRACION (real, mas lenta)
 */
public class Registry {

    /** Edad minima para votar. */
    public static final int MIN_AGE = 18;

    private final RegistryRepositoryPort repo;

    public Registry(RegistryRepositoryPort repo) {
        this.repo = repo;
    }

    public RegisterResult registerVoter(Person p) {
        if (p == null)
            return RegisterResult.INVALID;
        if (p.getId() <= 0)
            return RegisterResult.INVALID;
        if (!p.isAlive())
            return RegisterResult.DEAD;
        if (p.getAge() < MIN_AGE)
            return RegisterResult.UNDERAGE;

        try {
            if (repo.existsById(p.getId()))
                return RegisterResult.DUPLICATED;
            repo.save(p.getId(), p.getName(), p.getAge(), p.isAlive());
            return RegisterResult.VALID;
        } catch (Exception e) {
            // Traducimos el fallo de infraestructura a una excepcion de aplicacion.
            // El detalle tecnico va en la causa, no en el mensaje que ve el cliente.
            throw new RegistryPersistenceException("No se pudo registrar al votante", e);
        }
    }
}
