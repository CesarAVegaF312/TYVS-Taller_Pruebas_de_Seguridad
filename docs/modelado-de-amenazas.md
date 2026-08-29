# Módulo 1 — Modelado de amenazas

> El único módulo del taller que **no se automatiza**, y por eso el primero.
> Las pruebas de los módulos 2 a 4 verifican defensas. Este decide **cuáles
> hace falta construir**. Sin él, se acaba probando muy bien lo que a uno se le
> ocurrió, que casi nunca es lo que un atacante intentaría.

---

## Por qué esto va antes que las herramientas

Existe una trampa cómoda: instalar un escáner, ponerlo en el pipeline y
declarar el proyecto "seguro". El escáner encuentra lo que sabe buscar —
patrones conocidos en el código que usted escribió.

No encuentra:

- Que el endpoint de búsqueda devuelva el número de documento de cualquiera.
- Que cualquiera pueda inscribir votantes sin autenticarse.
- Que borrar una inscripción no deje rastro de quién la borró.

Ninguna de esas tres es un fallo de programación. Son **decisiones de diseño
que nadie tomó a propósito**, y ninguna herramienta las va a señalar, porque
el código las implementa perfectamente. Se encuentran razonando, y razonar es
lo que este módulo entrena.

---

## STRIDE en una página

STRIDE es una lista de seis preguntas. Su valor no está en la teoría sino en
que **obliga a recorrer las seis**, incluidas las que uno nunca se plantea.
La mayoría de los equipos piensa espontáneamente en la I (fuga de datos) y se
salta la R (repudio) por completo.

| Letra | Amenaza | La pregunta que hay que hacerse | Propiedad que rompe |
|---|---|---|---|
| **S** | *Spoofing* (suplantación) | ¿Puedo hacerme pasar por otro? | Autenticación |
| **T** | *Tampering* (manipulación) | ¿Puedo alterar datos que no me pertenecen? | Integridad |
| **R** | *Repudiation* (repudio) | ¿Puedo negar haber hecho algo? | Trazabilidad |
| **I** | *Information disclosure* | ¿Puedo ver datos que no me corresponden? | Confidencialidad |
| **D** | *Denial of service* | ¿Puedo dejarlo inservible para los demás? | Disponibilidad |
| **E** | *Elevation of privilege* | ¿Puedo hacer más de lo que me toca? | Autorización |

---

## El sistema bajo análisis

```text
   [Navegador]                    ┌──────────────────────────┐
        │                         │      Registraduría       │
        │  POST /register  ─────► │                          │
        │  GET  /buscar    ─────► │  RegistryController      │
        │  GET  /actuator/*─────► │  BusquedaController      │      ┌──────┐
        │                         │  Actuator                │ ───► │  BD  │
        └───────◄──── respuesta ──┤                          │      └──────┘
                                  └──────────────────────────┘
     ═══════════════════════════╪══════════════════════════════
        límite de confianza      │   (todo lo de la izquierda
                                     es controlado por el usuario)
```

**La línea doble es lo importante.** Es el *límite de confianza*: todo lo que
la cruza viene de fuera y puede ser hostil. Incluidos los datos que su propio
formulario acaba de validar en el navegador — un atacante no usa su
formulario, usa `curl`.

---

## Amenazas ya identificadas (y qué se hizo con cada una)

Esta tabla es el resultado del ejercicio, no el ejercicio. Sirve como modelo
del nivel de detalle esperado.

| # | Letra | Amenaza | Estado | Dónde se verifica |
|---|---|---|---|---|
| 1 | **T** | Inyección SQL en el parámetro `nombre` de `/buscar` | Mitigada: consulta parametrizada + lista blanca | `InyeccionSqlTest` (7 pruebas) |
| 2 | **I** | La búsqueda pública devuelve el documento y la edad | Mitigada: se devuelve solo el nombre | `SuperficieExpuestaIT` 08 |
| 3 | **I** | Actuator expone `/env` y `/heapdump` | Mitigada: lista explícita de endpoints | `SuperficieExpuestaIT` 01–03 |
| 4 | **I** | Los errores devuelven la traza de la excepción | Mitigada: `include-stacktrace=never` | `SuperficieExpuestaIT` 05–06 |
| 5 | **I** | `/health` revela el motor de base de datos | Mitigada: `show-details=never` | `SuperficieExpuestaIT` 04 |
| 6 | **D** | Un fragmento de búsqueda larguísimo satura la base | Mitigada parcialmente: límite de 60 caracteres | `InyeccionSqlTest` 07 |
| 7 | **I** | Credenciales escritas en el código | Mitigada: detector en la suite | `SecretosEnElCodigoTest` |
| 8 | **S** | Cualquiera puede inscribir votantes sin identificarse | **ACEPTADA** — ver abajo | — |
| 9 | **E** | No hay roles: quien puede leer puede escribir | **ACEPTADA** — ver abajo | — |
| 10 | **R** | No hay registro de auditoría de quién inscribió a quién | **ACEPTADA** — ver abajo | — |

### Las tres amenazas aceptadas, y por qué se dejan escritas

Las amenazas 8, 9 y 10 **no están mitigadas**, y eso es una decisión, no un
descuido. La aplicación es material docente y no tiene capa de autenticación
porque añadirla desviaría el taller hacia Spring Security.

Lo que importa metodológicamente es que **una amenaza aceptada se escribe**.
Un riesgo aceptado y documentado es una decisión de negocio; el mismo riesgo
sin documentar es una bomba de tiempo, porque dentro de un año nadie recordará
si se pensó y se descartó o si simplemente no se le ocurrió a nadie.

En un sistema real, las tres serían bloqueantes: un padrón electoral sin
autenticación, sin roles y sin auditoría no es desplegable.

---

## Lo que usted tiene que entregar

1. **Un diagrama de flujo de datos** de su propio sistema (o de esta
   Registraduría si trabaja sobre ella), con los límites de confianza
   marcados. A mano y fotografiado es perfectamente válido.

2. **Al menos 8 amenazas nuevas**, cubriendo las **seis** letras de STRIDE.
   Como mínimo una de R y una de D: son las que todo el mundo salta.

3. **Priorización.** Para cada amenaza, impacto (alto/medio/bajo) y facilidad
   de explotación. No todas valen lo mismo y el tiempo es finito.

4. **Una decisión por amenaza**, y solo hay tres opciones legítimas:
   - **Mitigar** → enlace a la prueba que lo verifica.
   - **Aceptar** → justificación escrita de por qué el riesgo es tolerable.
   - **Transferir** → a quién (proveedor, seguro, otro equipo).

   *"Pendiente"* no es una decisión. Si aparece en su entrega, es una amenaza
   sin dueño.

5. **Dos amenazas convertidas en prueba automatizada** dentro de la suite del
   módulo 2. Esa es la parte que cierra el ciclo: el modelado no sirve de nada
   si el resultado se queda en un documento que nadie vuelve a abrir.

---

## Un error que se repite mucho

Al listar amenazas, casi todo el mundo escribe *controles* disfrazados de
amenazas:

> ❌ "No se validan las entradas."

Eso es la ausencia de una defensa, no una amenaza. No dice quién ataca, qué
consigue, ni por qué importa. Comparado con:

> ✅ "Un usuario anónimo envía `' OR '1'='1` en el parámetro `nombre` de
> `/buscar` y obtiene el padrón electoral completo, incluidos los documentos
> de identidad de todas las personas inscritas."

La segunda versión dice **quién**, **cómo** y **qué obtiene**. Solo la segunda
se puede priorizar, y solo la segunda se puede convertir en una prueba.
