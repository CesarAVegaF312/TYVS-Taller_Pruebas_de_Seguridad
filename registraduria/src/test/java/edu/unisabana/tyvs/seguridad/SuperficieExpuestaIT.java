package edu.unisabana.tyvs.seguridad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MODULO 2 — CASOS DE ABUSO: SUPERFICIE EXPUESTA Y FUGA DE INFORMACION
 *
 * Las dos familias de defecto que se comprueban aqui tienen algo en comun:
 * no rompen nada. La aplicacion funciona perfectamente con ellos. Por eso
 * sobreviven a cualquier suite funcional y llegan a produccion.
 *
 * 1. SUPERFICIE EXPUESTA. Cada endpoint accesible es una puerta. Actuator
 *    trae quince, y la mayoria de la gente no sabe cuales estan abiertas
 *    porque nunca las abrio a proposito: vinieron con la dependencia.
 *
 * 2. FUGA DE INFORMACION. Un mensaje de error demasiado sincero le ahorra
 *    horas de trabajo a un atacante: le dice que motor de base de datos hay
 *    detras, que version de que libreria, que rutas tiene el disco y a veces
 *    el SQL entero.
 *
 * Que estas comprobaciones vivan en la suite y no en una lista de chequeo
 * manual es el punto entero de "shift-left". Una lista de chequeo se revisa
 * una vez, antes de la primera salida a produccion. Una prueba se revisa en
 * cada commit, incluido el commit del viernes que expone /env "solo para
 * depurar un momento".
 */
// classes = RegistryApplication.class es OBLIGATORIO aqui, y el motivo no es
// evidente: @SpringBootTest busca la clase @SpringBootApplication subiendo por
// el paquete de la prueba. Esta prueba vive en edu.unisabana.tyvs.seguridad y
// la aplicacion en edu.unisabana.tyvs.registry, que es una rama hermana, no un
// ancestro. Sin esta linea el arranque falla con "Unable to find a
// @SpringBootConfiguration".
@SpringBootTest(classes = edu.unisabana.tyvs.registry.RegistryApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "registry.jdbc-url=jdbc:h2:mem:seg_superficie;DB_CLOSE_DELAY=-1"
})
@DisplayName("Casos de abuso: superficie expuesta y fuga de informacion")
class SuperficieExpuestaIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("01 - Actuator no expone /env: revelaria variables de entorno y secretos")
    void actuatorNoExponeEnv() throws Exception {
        // /env lista TODA la configuracion del proceso, incluidas las
        // variables de entorno. Ahi es donde suelen vivir las contrasenas de
        // base de datos y las claves de API.
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("02 - Actuator no expone /heapdump: seria volcar la memoria del proceso")
    void actuatorNoExponeHeapdump() throws Exception {
        // Un heapdump contiene todo lo que hay en memoria en ese instante:
        // tokens de sesion, datos personales, credenciales ya descifradas.
        // Es la fuga mas completa que puede provocar una sola peticion GET.
        mockMvc.perform(get("/actuator/heapdump"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("03 - Actuator no expone /configprops ni /beans ni /mappings")
    void actuatorNoExponeElResto() throws Exception {
        // Estos tres no filtran secretos directamente, pero dibujan el mapa
        // interno de la aplicacion: que endpoints hay, que componentes, que
        // librerias. Es reconocimiento gratis.
        for (String endpoint : new String[] { "configprops", "beans", "mappings", "threaddump" }) {
            mockMvc.perform(get("/actuator/" + endpoint))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("04 - /health responde, pero sin detallar que hay detras")
    void healthNoRevelaDetalles() throws Exception {
        // Se quiere que el balanceador pueda preguntar "estas vivo?".
        // No se quiere que un anonimo sepa que base de datos hay detras ni
        // cuanto espacio libre queda en disco.
        MvcResult r = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andReturn();

        String cuerpo = r.getResponse().getContentAsString();

        assertTrue(cuerpo.contains("UP"), "El healthcheck deberia seguir siendo util");
        assertFalse(cuerpo.contains("\"components\""),
                "El healthcheck esta detallando los componentes internos: " + cuerpo);
        assertFalse(cuerpo.toLowerCase().contains("h2"),
                "El healthcheck revela el motor de base de datos: " + cuerpo);
        assertFalse(cuerpo.toLowerCase().contains("diskspace"),
                "El healthcheck revela el estado del disco: " + cuerpo);
    }

    @Test
    @DisplayName("05 - Un error de servidor no devuelve la traza de la excepcion")
    void elErrorNoDevuelveLaTraza() throws Exception {
        // Se envia un genero que no existe en el enum. Sin control, Spring
        // responderia 500 con el stack trace completo dentro.
        String json = "{\"name\":\"Ana\",\"id\":900001,\"age\":30,"
                + "\"gender\":\"ESTO_NO_EXISTE\",\"alive\":true}";

        MvcResult r = mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andReturn();

        String cuerpo = r.getResponse().getContentAsString();

        // Se comprueba lo que NO debe estar, que es lo dificil de recordar.
        assertFalse(cuerpo.contains("java.lang."),
                "La respuesta nombra clases internas de Java: " + cuerpo);
        assertFalse(cuerpo.contains("edu.unisabana"),
                "La respuesta revela los paquetes de la aplicacion: " + cuerpo);
        assertFalse(cuerpo.contains("\tat "),
                "La respuesta contiene una traza de pila: " + cuerpo);
        assertFalse(cuerpo.toLowerCase().contains("sql"),
                "La respuesta menciona SQL: " + cuerpo);
    }

    @Test
    @DisplayName("06 - Un JSON mal formado tampoco filtra el detalle del parser")
    void elJsonMalFormadoNoFiltraDetalle() throws Exception {
        MvcResult r = mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ esto no es json "))
                .andReturn();

        String cuerpo = r.getResponse().getContentAsString();

        assertFalse(cuerpo.contains("com.fasterxml"),
                "La respuesta revela que se usa Jackson y su version: " + cuerpo);
        assertFalse(cuerpo.contains("\tat "), "La respuesta contiene una traza: " + cuerpo);
    }

    @Test
    @DisplayName("07 - La busqueda rechaza la inyeccion tambien a traves de HTTP")
    void laBusquedaRechazaLaInyeccionPorHttp() throws Exception {
        // La prueba unitaria ya comprobo el buscador. Esta comprueba que lo
        // que esta CABLEADO en la aplicacion es el buscador seguro. Son dos
        // afirmaciones distintas: se puede tener la clase correcta escrita y
        // la incorrecta conectada.
        mockMvc.perform(get("/buscar").param("nombre", "' OR '1'='1"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/buscar").param("nombre", "%"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("08 - La busqueda no devuelve documento ni edad (minimizacion de datos)")
    void laBusquedaMinimizaLosDatos() throws Exception {
        mockMvc.perform(post("/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ana Buscable\",\"id\":900777,\"age\":33,"
                        + "\"gender\":\"FEMALE\",\"alive\":true}"))
                .andExpect(status().isOk());

        MvcResult r = mockMvc.perform(get("/buscar").param("nombre", "Buscable"))
                .andExpect(status().isOk())
                .andReturn();

        String cuerpo = r.getResponse().getContentAsString();

        assertTrue(cuerpo.contains("Ana Buscable"), "Deberia encontrarla: " + cuerpo);

        // Un endpoint publico de busqueda que devuelve el documento completo
        // es un directorio de datos personales. Que la consulta ya tenga el
        // dato a mano no es razon para publicarlo.
        assertFalse(cuerpo.contains("900777"),
                "La busqueda publica esta devolviendo el numero de documento: " + cuerpo);
        assertFalse(cuerpo.contains("33"),
                "La busqueda publica esta devolviendo la edad: " + cuerpo);
    }
}
