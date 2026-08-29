package edu.unisabana.tyvs.seguridad;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * MODULO 3 — VERIFICAR AL VERIFICADOR
 *
 * Una pregunta que casi nadie hace: como sabe usted que su analizador
 * estatico funciona?
 *
 * El modo de fallo tipico de un SAST no es el falso positivo, que se ve y
 * molesta. Es el SILENCIO: la herramienta corre, tarda su minuto, escribe un
 * informe vacio, el semaforo se pone verde y nadie se entera de que estaba
 * mal configurada. Un informe vacio se ve exactamente igual tanto si el
 * codigo esta limpio como si el analizador no supo mirar.
 *
 * Las causas reales de ese silencio son mundanas: el plugin de findsecbugs no
 * se cargo, el umbral se subio hasta que dejo de reportar, alguien excluyo un
 * patron entero en vez de una clase, el analisis apunta a un directorio que
 * ya no existe.
 *
 * Esta prueba es el control: hay una vulnerabilidad conocida y puesta a
 * proposito en el codigo, y aqui se comprueba que la herramienta la encuentra.
 * Es el equivalente para el SAST de lo que las pruebas de mutacion son para
 * la suite unitaria: no medir cuanto se ejecuto, sino si se entera de algo.
 *
 * Lee target/spotbugs-sin-exclusiones.xml, que el pom genera en la fase
 * process-classes precisamente para esto.
 */
@DisplayName("El analisis estatico encuentra la vulnerabilidad sembrada")
class SastDetectaLaVulnerabilidadTest {

    private static final Path INFORME =
            Paths.get("target", "spotbugs-sin-exclusiones.xml");

    /** La clase que contiene la inyeccion SQL deliberada. */
    private static final String CLASE_VULNERABLE = "BuscadorVulnerable";

    private static String informe;

    @BeforeAll
    static void leerInforme() throws IOException {
        // Si alguien ejecuta solo `mvn surefire:test` el informe no existe
        // todavia. Se omite en vez de fallar: el flujo normal es `mvn verify`.
        assumeTrue(Files.exists(INFORME),
                "No hay informe de SpotBugs. Ejecute `mvn verify` (o al menos "
                        + "`mvn process-classes`) antes que esta prueba.");

        informe = new String(Files.readAllBytes(INFORME), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("01 - findsecbugs esta realmente cargado")
    void findsecbugsEstaCargado() {
        // Si el plugin no se resolvio, SpotBugs corre igual y no dice nada:
        // simplemente pierde las ~130 reglas de seguridad. El build sigue
        // verde. Este es el fallo silencioso mas comun de todos.
        assertTrue(informe.contains("com.h3xstream.findsecbugs"),
                "El plugin findsecbugs no aparece en el informe: se estan "
                        + "perdiendo todas las reglas de seguridad");
    }

    @Test
    @DisplayName("02 - Reporta la inyeccion SQL de la clase vulnerable")
    void reportaLaInyeccionSql() {
        assertTrue(informe.contains("SQL_INJECTION_JDBC"),
                "findsecbugs no reporto SQL_INJECTION_JDBC pese a que "
                        + CLASE_VULNERABLE + " concatena la entrada del usuario");

        // El detector propio de SpotBugs (sin findsecbugs) tambien lo ve.
        // Que coincidan dos detectores independientes es buena senal.
        assertTrue(informe.contains("SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE"),
                "El detector nucleo de SpotBugs tampoco lo reporto");
    }

    @Test
    @DisplayName("03 - El hallazgo apunta a la clase vulnerable, no a otra")
    void elHallazgoApuntaADondeDebe() {
        // Que el patron aparezca en algun sitio no basta: hay que comprobar
        // que senala el archivo correcto. Un analizador que reporta la clase
        // equivocada manda al equipo a mirar donde no es.
        assertTrue(informe.contains(CLASE_VULNERABLE + ".java"),
                "El informe no menciona " + CLASE_VULNERABLE + ".java");
    }

    @Test
    @DisplayName("04 - La version segura no aparece marcada por inyeccion")
    void laVersionSeguraNoAparece() {
        // El otro lado del control: si la herramienta marcara TAMBIEN el
        // codigo correcto, seria ruido y el equipo aprenderia a ignorarla.
        // Un analizador solo sirve si distingue.
        String bloqueDeLaClaseSegura = extraerBloqueDe("BuscadorSeguro.java");

        assertTrue(bloqueDeLaClaseSegura.isEmpty()
                        || !bloqueDeLaClaseSegura.contains("SQL_INJECTION"),
                "SpotBugs marco por inyeccion SQL la implementacion "
                        + "parametrizada. Eso seria un falso positivo: " + bloqueDeLaClaseSegura);
    }

    /** Devuelve el fragmento del informe alrededor de un archivo dado. */
    private static String extraerBloqueDe(String archivo) {
        int i = informe.indexOf(archivo);
        if (i < 0) {
            return "";
        }
        int desde = Math.max(0, i - 600);
        int hasta = Math.min(informe.length(), i + 600);
        return informe.substring(desde, hasta);
    }
}
