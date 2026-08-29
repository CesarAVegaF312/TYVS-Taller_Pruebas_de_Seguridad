package edu.unisabana.tyvs.seguridad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MODULO 4 — SECRETOS EN EL CODIGO FUENTE
 *
 * Un secreto commiteado no se arregla borrandolo en el commit siguiente. El
 * historial de Git lo conserva, y si el repositorio es publico hay bots que
 * escanean GitHub en tiempo real: las claves de AWS filtradas se explotan en
 * minutos, no en dias. La unica respuesta correcta a "subi una credencial" es
 * ROTARLA, y ademas reescribir el historial.
 *
 * De ahi que este control tenga que ejecutarse ANTES del commit, no despues.
 * Esta prueba es la version minima y didactica de lo que hacen gitleaks o
 * truffleHog; en un proyecto real se pone ademas como hook de pre-commit,
 * porque para cuando el CI se queja el secreto ya viajo.
 *
 * SOBRE LOS FALSOS POSITIVOS: un detector de secretos siempre los tiene.
 * Busca formas, no significados, y "password = ..." aparece tanto en una
 * credencial real como en un ejemplo de documentacion. La respuesta no es
 * aflojar el patron hasta que calle, sino mantener una lista de excepciones
 * revisada, igual que con spotbugs-exclude.xml.
 */
@DisplayName("No hay secretos escritos en el codigo fuente")
class SecretosEnElCodigoTest {

    /**
     * Patrones deliberadamente simples: se entienden de un vistazo, que es
     * mas importante aqui que la exhaustividad. Un gitleaks de verdad trae
     * unas 150 reglas, muchas especificas de un proveedor (el prefijo AKIA de
     * AWS, el ghp_ de GitHub, el sk-live de Stripe).
     */
    private static final List<Pattern> PATRONES = List.of(
            // password = "algo" / clave: 'algo'  (con algo de contenido real)
            Pattern.compile("(?i)(password|passwd|clave|contrasena)\\s*[=:]\\s*[\"'][^\"'\\s]{6,}[\"']"),
            // api_key, apikey, secret, token seguidos de un valor largo
            Pattern.compile("(?i)(api[_-]?key|secret|token)\\s*[=:]\\s*[\"'][^\"'\\s]{12,}[\"']"),
            // Claves de acceso de AWS
            Pattern.compile("AKIA[0-9A-Z]{16}"),
            // Tokens personales de GitHub
            Pattern.compile("gh[pousr]_[A-Za-z0-9]{36,}"),
            // Bloques de clave privada
            Pattern.compile("-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----"),
            // Credenciales incrustadas en una URL: protocolo://usuario:clave@host
            Pattern.compile("[a-z][a-z0-9+.-]*://[^/\\s:@]+:[^/\\s:@]+@"));

    /**
     * Excepciones revisadas a mano. Cada una necesita justificacion, igual
     * que en spotbugs-exclude.xml.
     */
    private static final List<String> ARCHIVOS_EXCEPTUADOS = List.of(
            // Esta misma clase: contiene los patrones de busqueda, que por
            // definicion se parecen a lo que buscan.
            "SecretosEnElCodigoTest.java",
            // El modulo 2 inserta una contrasena FALSA en una tabla de prueba
            // para medir el radio de dano de una inyeccion SQL. No es una
            // credencial real y no abre nada.
            "InyeccionSqlTest.java");

    @Test
    @DisplayName("01 - Ningun archivo de codigo o configuracion contiene un secreto")
    void noHaySecretosEnElCodigo() throws IOException {
        List<String> hallazgos = new ArrayList<>();

        for (Path archivo : archivosDelProyecto()) {
            String contenido = new String(Files.readAllBytes(archivo), StandardCharsets.UTF_8);

            for (Pattern patron : PATRONES) {
                var m = patron.matcher(contenido);
                if (m.find()) {
                    hallazgos.add(archivo + "  ->  " + recortar(m.group()));
                }
            }
        }

        assertTrue(hallazgos.isEmpty(),
                "Posibles secretos en el codigo. Si alguno es un falso positivo, "
                        + "documentelo en ARCHIVOS_EXCEPTUADOS con su justificacion; "
                        + "si es real, ROTELO antes de borrarlo:\n  "
                        + String.join("\n  ", hallazgos));
    }

    @Test
    @DisplayName("02 - El detector funciona: reconoce un secreto de ejemplo")
    void elDetectorFunciona() {
        // Mismo principio que SastDetectaLaVulnerabilidadTest: un detector que
        // nunca ha detectado nada no es una garantia, es una suposicion.
        // Estas cadenas son inventadas y no abren ningun sistema.
        String[] ejemplos = {
                "String password = \"SuperClave123\";",
                "api_key: \"abcdef0123456789abcdef\"",
                "AKIAIOSFODNN7EXAMPLE",
                "jdbc:postgresql://usuario:secreta@servidor:5432/db"
        };

        for (String ejemplo : ejemplos) {
            boolean detectado = PATRONES.stream().anyMatch(p -> p.matcher(ejemplo).find());
            assertTrue(detectado, "El detector dejo pasar: " + ejemplo);
        }
    }

    /** Archivos de codigo y configuracion del proyecto, sin target/ ni binarios. */
    private static List<Path> archivosDelProyecto() throws IOException {
        Path raiz = Paths.get("").toAbsolutePath();

        try (Stream<Path> rutas = Files.walk(raiz)) {
            return rutas
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains(java.io.File.separator + "target"))
                    .filter(p -> !p.toString().contains(java.io.File.separator + ".git"))
                    .filter(SecretosEnElCodigoTest::esTexto)
                    .filter(p -> ARCHIVOS_EXCEPTUADOS.stream()
                            .noneMatch(e -> p.getFileName().toString().equals(e)))
                    .toList();
        }
    }

    private static boolean esTexto(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        return n.endsWith(".java") || n.endsWith(".xml") || n.endsWith(".properties")
                || n.endsWith(".yml") || n.endsWith(".yaml") || n.endsWith(".json")
                || n.endsWith(".md") || n.endsWith(".sql") || n.endsWith(".sh");
    }

    private static String recortar(String s) {
        return s.length() <= 60 ? s : s.substring(0, 57) + "...";
    }
}
