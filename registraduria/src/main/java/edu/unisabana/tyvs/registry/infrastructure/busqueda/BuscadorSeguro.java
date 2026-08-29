package edu.unisabana.tyvs.registry.infrastructure.busqueda;

import edu.unisabana.tyvs.registry.infrastructure.persistence.RegistryRecord;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * La misma busqueda, escrita bien. Dos defensas, en este orden:
 *
 * 1. CONSULTA PARAMETRIZADA (la que de verdad resuelve el problema)
 *
 *    El SQL se envia al motor con un hueco (?) y el valor va aparte. El motor
 *    compila la consulta ANTES de ver el dato, asi que el dato ya no puede
 *    cambiar la estructura de la consulta. Da igual lo que escriba el usuario:
 *    "' OR '1'='1" se busca como el texto literal " ' OR '1'='1 ", que no
 *    existe en ningun nombre.
 *
 *    Esto no es "escapar comillas". Escapar a mano es una carrera que siempre
 *    se pierde: hay codificaciones, comentarios y comillas Unicode que se le
 *    escapan a cualquiera. Parametrizar elimina la categoria entera de fallo.
 *
 * 2. VALIDACION DE ENTRADA (defensa en profundidad)
 *
 *    Aun con parametros, un fragmento vacio devolveria el padron completo, y
 *    uno larguisimo es un vector de denegacion de servicio. La validacion no
 *    sustituye a la parametrizacion: la acompana. Si manana alguien reescribe
 *    la consulta y se equivoca, esta capa reduce el dano.
 *
 * Detalle facil de pasar por alto: los comodines de LIKE. Un usuario que
 * escriba "%" veria el padron entero sin necesidad de inyectar nada, porque %
 * es un comodin PARA LIKE aunque sea un dato inofensivo para SQL. Por eso se
 * escapan _ y % explicitamente con ESCAPE.
 */
public class BuscadorSeguro implements BuscadorDeVotantes {

    /** Un fragmento util es corto. Mas alla de esto solo hay ruido o abuso. */
    public static final int LONGITUD_MAXIMA = 60;

    /** Minimo para que la busqueda discrimine algo y no devuelva medio padron. */
    public static final int LONGITUD_MINIMA = 2;

    private final String jdbcUrl;

    public BuscadorSeguro(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    @Override
    public List<RegistryRecord> buscarPorNombre(String fragmentoDeNombre) throws Exception {
        String fragmento = validar(fragmentoDeNombre);

        final String sql = "SELECT id, name, age, is_alive FROM registry"
                + " WHERE name LIKE ? ESCAPE '\\'";

        List<RegistryRecord> encontrados = new ArrayList<>();
        try (Connection con = DriverManager.getConnection(jdbcUrl);
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, "%" + escaparComodines(fragmento) + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    encontrados.add(leer(rs));
                }
            }
        }
        return encontrados;
    }

    /**
     * Lista blanca, no lista negra.
     *
     * Prohibir "lo malo" (comillas, DROP, --) no funciona: la lista de cosas
     * malas es infinita y siempre falta una. Permitir "lo bueno" si funciona,
     * porque lo bueno si es enumerable: un nombre lleva letras, espacios,
     * tildes, apostrofos de apellidos como O'Brien y poco mas.
     */
    private static String validar(String entrada) {
        if (entrada == null) {
            throw new IllegalArgumentException("El fragmento de busqueda es obligatorio.");
        }
        String limpio = entrada.trim();
        if (limpio.length() < LONGITUD_MINIMA) {
            throw new IllegalArgumentException(
                    "El fragmento debe tener al menos " + LONGITUD_MINIMA + " caracteres.");
        }
        if (limpio.length() > LONGITUD_MAXIMA) {
            throw new IllegalArgumentException(
                    "El fragmento no puede superar los " + LONGITUD_MAXIMA + " caracteres.");
        }
        if (!limpio.matches("[\\p{L}\\p{M} .'-]+")) {
            throw new IllegalArgumentException(
                    "El fragmento solo puede contener letras, espacios, puntos, apostrofos y guiones.");
        }
        return limpio;
    }

    /**
     * Neutraliza los comodines de LIKE para que se busquen como texto literal.
     * La barra invertida va primera: si no, se escaparia a si misma despues.
     */
    private static String escaparComodines(String valor) {
        return valor.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static RegistryRecord leer(ResultSet rs) throws SQLException {
        return new RegistryRecord(
                rs.getInt("id"), rs.getString("name"), rs.getInt("age"), rs.getBoolean("is_alive"));
    }
}
