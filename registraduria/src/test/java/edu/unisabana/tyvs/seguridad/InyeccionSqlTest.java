package edu.unisabana.tyvs.seguridad;

import edu.unisabana.tyvs.registry.infrastructure.busqueda.BuscadorSeguro;
import edu.unisabana.tyvs.registry.infrastructure.busqueda.BuscadorVulnerable;
import edu.unisabana.tyvs.registry.infrastructure.persistence.RegistryRecord;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MODULO 2 — CASOS DE ABUSO: INYECCION SQL
 *
 * Un caso de uso describe lo que el usuario quiere hacer.
 * Un caso de abuso describe lo que el atacante quiere hacer.
 *
 * La diferencia entre los dos no es el tono: es que producen conjuntos de
 * entradas completamente distintos. El caso de uso "buscar un votante por
 * nombre" genera entradas como "Ana" o "Rodriguez". El caso de abuso "leer
 * datos que no me corresponden usando la busqueda" genera "' OR '1'='1".
 *
 * Ninguna suite funcional, por completa que sea, produce la segunda familia
 * de entradas por su cuenta. Hay que pensarlas a proposito. Eso es lo que
 * este modulo entrena.
 *
 * LA PRUEBA 01 ES LA MAS IMPORTANTE DEL ARCHIVO: demuestra que las dos
 * implementaciones son indistinguibles desde el punto de vista funcional. Si
 * la unica evidencia que tuviera fuera su suite de pruebas normal, no podria
 * saber cual de las dos esta en produccion.
 */
@DisplayName("Casos de abuso: inyeccion SQL en la busqueda de votantes")
class InyeccionSqlTest {

    /** Base en memoria propia de esta clase, para no interferir con otras pruebas. */
    private static final String JDBC_URL = "jdbc:h2:mem:seguridad_sqli;DB_CLOSE_DELAY=-1";

    private BuscadorVulnerable vulnerable;
    private BuscadorSeguro seguro;

    @BeforeEach
    void prepararPadron() throws Exception {
        try (Connection con = DriverManager.getConnection(JDBC_URL, "", "");
                Statement st = con.createStatement()) {

            st.execute("DROP TABLE IF EXISTS registry");
            st.execute("DROP TABLE IF EXISTS credenciales");

            st.execute("CREATE TABLE registry("
                    + " id INT PRIMARY KEY, name VARCHAR(100) NOT NULL,"
                    + " age INT NOT NULL, is_alive BOOLEAN NOT NULL)");
            st.execute("INSERT INTO registry VALUES"
                    + " (1, 'Ana Martinez', 30, TRUE),"
                    + " (2, 'Ana Sofia Rojas', 41, TRUE),"
                    + " (3, 'Luis Rodriguez', 52, TRUE),"
                    + " (4, 'Pedro Gomez', 67, FALSE)");

            // Una segunda tabla que la busqueda de votantes NO deberia poder
            // tocar jamas. Sirve para medir el radio de dano de la inyeccion.
            st.execute("CREATE TABLE credenciales("
                    + " usuario VARCHAR(50) PRIMARY KEY, clave VARCHAR(100) NOT NULL)");
            st.execute("INSERT INTO credenciales VALUES"
                    + " ('admin', 'clave-de-produccion-2026')");
        }

        vulnerable = new BuscadorVulnerable(JDBC_URL);
        seguro = new BuscadorSeguro(JDBC_URL);
    }

    @Test
    @DisplayName("01 - Con entradas normales las dos implementaciones son indistinguibles")
    void lasDosSeComportanIgualAnteEntradasNormales() throws Exception {
        // Esto es lo que probaria una suite funcional: que la busqueda busca.
        assertEquals(2, vulnerable.buscarPorNombre("Ana").size());
        assertEquals(2, seguro.buscarPorNombre("Ana").size());

        assertEquals(1, vulnerable.buscarPorNombre("Rodriguez").size());
        assertEquals(1, seguro.buscarPorNombre("Rodriguez").size());

        assertEquals(0, vulnerable.buscarPorNombre("Zulema").size());
        assertEquals(0, seguro.buscarPorNombre("Zulema").size());

        // Conclusion incomoda: una suite funcional al 100% de cobertura no
        // distingue codigo seguro de codigo vulnerable. La cobertura mide
        // lineas ejecutadas, no entradas imaginadas.
    }

    @Test
    @DisplayName("02 - EXPLOIT: una tautologia devuelve el padron completo")
    void laTautologiaDevuelveTodoElPadron() throws Exception {
        // El clasico. Cierra la comilla, agrega una condicion siempre cierta.
        String ataque = "' OR '1'='1";

        // Mire el SQL que se construye. El dato del usuario dejo de ser un
        // dato: ahora es parte de la logica de la consulta.
        System.out.println("SQL ejecutado: " + vulnerable.sqlGeneradoPara(ataque));

        List<RegistryRecord> fuga = vulnerable.buscarPorNombre(ataque);

        // Se pidio "buscar un nombre" y se obtuvo la tabla entera.
        // Esto no es un fallo de busqueda: es un fallo de control de acceso.
        assertEquals(4, fuga.size(), "La inyeccion deberia devolver los 4 registros");
    }

    @Test
    @DisplayName("03 - EXPLOIT: un UNION lee una tabla que la busqueda no deberia tocar")
    void elUnionExfiltraOtraTabla() throws Exception {
        // Peor que devolver de mas: devolver de OTRO SITIO. El radio de dano
        // de una inyeccion no es el endpoint, es todo lo que alcance el
        // usuario de base de datos de la aplicacion.
        String ataque = "x%' UNION SELECT 1, clave, 0, false FROM credenciales --";

        List<RegistryRecord> fuga = vulnerable.buscarPorNombre(ataque);

        boolean seFiltroLaClave = fuga.stream()
                .anyMatch(r -> "clave-de-produccion-2026".equals(r.getName()));

        assertTrue(seFiltroLaClave,
                "La busqueda de votantes acaba de devolver una contrasena: " + fuga);
    }

    @Test
    @DisplayName("04 - EXPLOIT: un comodin de LIKE basta, sin inyectar nada")
    void elComodinSolteroYaEsUnaFuga() throws Exception {
        // Ni siquiera hace falta romper la sintaxis. "%" es un dato
        // perfectamente valido para SQL y aun asi vacia la tabla, porque es
        // un comodin PARA LIKE. Es el defecto que casi siempre se olvida al
        // migrar a consultas parametrizadas: se arregla la inyeccion y se
        // deja el comodin.
        assertEquals(4, vulnerable.buscarPorNombre("%").size());
    }

    @Test
    @DisplayName("05 - La version segura rechaza los tres ataques")
    void laVersionSeguraLosRechaza() {
        // No "devuelve menos": rechaza la entrada antes de tocar la base.
        // Fallar rapido y ruidosamente es preferible a fallar en silencio.
        assertThrows(IllegalArgumentException.class,
                () -> seguro.buscarPorNombre("' OR '1'='1"));
        assertThrows(IllegalArgumentException.class,
                () -> seguro.buscarPorNombre("x%' UNION SELECT 1, clave, 0, false FROM credenciales --"));
        assertThrows(IllegalArgumentException.class,
                () -> seguro.buscarPorNombre("%"));
    }

    @Test
    @DisplayName("06 - La version segura trata las comillas como texto, no como codigo")
    void laVersionSeguraTrataLasComillasComoTexto() throws Exception {
        // Un apellido irlandes lleva apostrofo de verdad. Una defensa que
        // simplemente prohibiera la comilla romperia a estas personas, que es
        // como se llega a la regla absurda de "su apellido no es valido".
        try (Connection con = DriverManager.getConnection(JDBC_URL, "", "");
                Statement st = con.createStatement()) {
            st.execute("INSERT INTO registry VALUES (5, 'Sean O''Brien', 34, TRUE)");
        }

        List<RegistryRecord> resultado = seguro.buscarPorNombre("O'Brien");

        assertEquals(1, resultado.size(), "Un apostrofo legitimo debe poder buscarse");
        assertEquals("Sean O'Brien", resultado.get(0).getName());

        // La diferencia entre "prohibir la comilla" y "parametrizar" es
        // exactamente esta persona.
    }

    @Test
    @DisplayName("07 - La version segura pone limites de longitud en las dos direcciones")
    void laVersionSeguraLimitaLaLongitud() {
        assertThrows(IllegalArgumentException.class, () -> seguro.buscarPorNombre("A"));
        assertThrows(IllegalArgumentException.class, () -> seguro.buscarPorNombre("   "));
        assertThrows(IllegalArgumentException.class, () -> seguro.buscarPorNombre(null));
        assertThrows(IllegalArgumentException.class,
                () -> seguro.buscarPorNombre("A".repeat(BuscadorSeguro.LONGITUD_MAXIMA + 1)));
    }
}
