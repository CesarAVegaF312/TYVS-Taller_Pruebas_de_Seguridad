package edu.unisabana.tyvs.registry.delivery.rest;

import edu.unisabana.tyvs.registry.application.usecase.RegistryPersistenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce excepciones a codigos HTTP.
 *
 * Sin esta clase, un genero invalido como "X" produce un IllegalArgumentException
 * dentro de Gender.valueOf() y Spring responde 500 Internal Server Error. Eso es
 * incorrecto: 5xx significa "el servidor fallo", cuando en realidad el cliente
 * envio datos malos. La respuesta correcta es 400 Bad Request.
 *
 * Este era el Defecto 05 de defectos.md.
 */
@RestControllerAdvice
public class RegistryExceptionHandler {

    /** Genero fuera del enum, o cualquier argumento invalido del cliente. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalidArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body("INVALID_INPUT");
    }

    /** JSON mal formado o con tipos que no encajan en el DTO. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<String> handleMalformedJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body("MALFORMED_JSON");
    }

    /** Fallo real de infraestructura: aqui si corresponde un 5xx. */
    @ExceptionHandler(RegistryPersistenceException.class)
    public ResponseEntity<String> handlePersistence(RegistryPersistenceException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("PERSISTENCE_ERROR");
    }
}
