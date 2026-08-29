package edu.unisabana.tyvs.registry.infrastructure.busqueda;

import edu.unisabana.tyvs.registry.infrastructure.persistence.RegistryRecord;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 * IMPLEMENTACION VULNERABLE A PROPOSITO — NO COPIAR A UN PROYECTO REAL
 * ============================================================================
 *
 * Existe por la misma razon que defectuosa.html en el taller de UX: para que
 * usted vea el fallo ocurrir, no para que se lo cuenten.
 *
 * El defecto esta en una sola linea: el fragmento que envia el usuario se
 * CONCATENA dentro del SQL. El motor no recibe "una consulta y unos datos";
 * recibe un texto y lo interpreta entero como codigo. Todo lo que el usuario
 * escriba es codigo SQL.
 *
 * Por que ninguna prueba funcional lo detecta:
 *
 *   buscarPorNombre("Ana")  -> devuelve a Ana.  Prueba verde.
 *   buscarPorNombre("Luis") -> devuelve a Luis. Prueba verde.
 *
 * Se comporta perfectamente con TODAS las entradas que a alguien se le ocurre
 * escribir pensando en el caso de uso. El problema aparece con:
 *
 *   buscarPorNombre("x' OR '1'='1")
 *
 * que convierte el WHERE en una tautologia y devuelve el padron completo.
 * Es decir: un fallo de control de acceso disfrazado de busqueda.
 *
 * La leccion no es "usa PreparedStatement" (eso ya lo sabe). Es que la
 * seguridad necesita un tipo de prueba distinto: la funcional pregunta
 * "hace lo que debe?" y la de abuso pregunta "que MAS puede hacer?".
 *
 * Se conserva en el codigo, y no en un comentario, porque el modulo 2 la
 * explota de verdad y el modulo 3 comprueba que SpotBugs la marca.
 * Esta excluida de spotbugs-exclude.xml con esa justificacion escrita.
 */
public class BuscadorVulnerable implements BuscadorDeVotantes {

    private final String jdbcUrl;

    public BuscadorVulnerable(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    @Override
    public List<RegistryRecord> buscarPorNombre(String fragmentoDeNombre) throws Exception {
        // AQUI ESTA EL DEFECTO. Una sola concatenacion.
        String sql = "SELECT id, name, age, is_alive FROM registry"
                + " WHERE name LIKE '%" + fragmentoDeNombre + "%'";

        List<RegistryRecord> encontrados = new ArrayList<>();
        try (Connection con = DriverManager.getConnection(jdbcUrl);
                Statement st = con.createStatement();
                ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                encontrados.add(leer(rs));
            }
        }
        return encontrados;
    }

    /** Expone el SQL que se ejecutaria, para que las pruebas puedan mostrarlo. */
    public String sqlGeneradoPara(String fragmentoDeNombre) {
        return "SELECT id, name, age, is_alive FROM registry"
                + " WHERE name LIKE '%" + fragmentoDeNombre + "%'";
    }

    private static RegistryRecord leer(ResultSet rs) throws SQLException {
        return new RegistryRecord(
                rs.getInt("id"), rs.getString("name"), rs.getInt("age"), rs.getBoolean("is_alive"));
    }
}
