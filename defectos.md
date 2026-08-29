# Registro de Defectos de Seguridad — ejemplo del profesor

Ejemplo del nivel de detalle esperado. Los tres defectos son reales y están
en el repositorio; los dos primeros ya tienen prueba que los verifica.

---

### Defecto SEC-01 — Inyección SQL en la búsqueda de votantes

- **Letra STRIDE**: T (manipulación) e I (fuga de información)
- **Cómo se detectó**: modelado de amenazas, confirmado después por SAST
- **Precondiciones**: ninguna. Basta una petición HTTP anónima.
- **Pasos para reproducir**:
  1. `BuscadorVulnerable` concatena el parámetro dentro de la sentencia SQL.
  2. Enviar como fragmento de nombre: `' OR '1'='1`
  3. El `WHERE` se convierte en una tautología.
- **Qué obtiene el atacante**: el padrón electoral completo. Con un `UNION`
  además puede leer **otras tablas** de la misma base: la prueba 03 de
  `InyeccionSqlTest` extrae una contraseña de la tabla `credenciales`.
  El radio de daño no es el endpoint, es todo lo que alcance el usuario de
  base de datos de la aplicación.
- **Impacto**: Alto
- **Facilidad de explotación**: Trivial
- **Decisión**: Mitigar
- **Mitigación**: consulta parametrizada (`PreparedStatement`) más validación
  por lista blanca. Implementado en `BuscadorSeguro`, que es la clase que la
  aplicación cablea.
- **Prueba que lo verifica**: `InyeccionSqlTest` (7 pruebas) y
  `SuperficieExpuestaIT#laBusquedaRechazaLaInyeccionPorHttp`
- **Estado**: Resuelto

> **Detalle que casi se escapa.** La primera versión de la mitigación
> parametrizaba la consulta pero **no escapaba los comodines de `LIKE`**. Un
> usuario que escribiera `%` seguía viendo el padrón entero, sin inyectar
> nada: `%` es un dato perfectamente válido para SQL y aun así es un comodín
> para `LIKE`. Es el defecto que se olvida justo después de arreglar la
> inyección, porque uno da el problema por cerrado.

---

### Defecto SEC-02 — La búsqueda pública devolvía el número de documento

- **Letra STRIDE**: I (fuga de información)
- **Cómo se detectó**: modelado de amenazas. **Ninguna herramienta lo detecta**,
  y ahí está lo interesante: el código funcionaba perfectamente. Devolver el
  documento no es un error de programación, es una decisión de diseño que
  nadie tomó a propósito — la consulta ya traía el dato y se serializó entero.
- **Precondiciones**: ninguna.
- **Pasos para reproducir**:
  1. `GET /buscar?nombre=Rodriguez`
  2. La respuesta incluía `id`, `age` y `name` de cada coincidencia.
- **Qué obtiene el atacante**: iterando apellidos comunes, un directorio
  completo de datos personales: nombre, documento y edad de todo el padrón.
- **Impacto**: Alto
- **Facilidad de explotación**: Trivial
- **Decisión**: Mitigar
- **Mitigación**: minimización de datos. `BusquedaController` proyecta solo el
  nombre. Que el dato esté a mano no es razón para publicarlo.
- **Prueba que lo verifica**: `SuperficieExpuestaIT#laBusquedaMinimizaLosDatos`
- **Estado**: Resuelto

---

### Defecto SEC-03 — No hay autenticación ni registro de auditoría

- **Letra STRIDE**: S (suplantación), E (elevación) y R (repudio)
- **Cómo se detectó**: modelado de amenazas
- **Precondiciones**: ninguna.
- **Qué obtiene el atacante**: cualquiera puede inscribir votantes, y no queda
  rastro de quién lo hizo. En un padrón electoral real esto invalida el
  proceso entero: sin trazabilidad no se puede auditar una elección.
- **Impacto**: Alto
- **Facilidad de explotación**: Trivial
- **Decisión**: **Aceptar** (riesgo asumido, no descuido)
- **Justificación**: la aplicación es material docente, corre en local y no
  contiene datos reales. Añadir Spring Security desviaría el taller de su
  objetivo. **En un sistema real esto sería bloqueante**: un padrón sin
  autenticación, sin roles y sin auditoría no es desplegable.
- **Prueba que lo verifica**: ninguna, por definición — un riesgo aceptado no
  se prueba, se documenta.
- **Estado**: Aceptado

> Este defecto está aquí precisamente porque **no** se arregló. Un riesgo
> aceptado y escrito es una decisión de negocio; el mismo riesgo sin escribir
> es una bomba de tiempo, porque en un año nadie recordará si se pensó y se
> descartó o si simplemente no se le ocurrió a nadie.

---

## Tabla de seguimiento

| ID | Título | STRIDE | Cómo se detectó | Impacto | Decisión | Estado |
|----|--------|--------|-----------------|---------|----------|--------|
| SEC-01 | Inyección SQL en la búsqueda | T, I | Modelado + SAST | Alto | Mitigar | Resuelto |
| SEC-02 | La búsqueda devolvía el documento | I | Modelado | Alto | Mitigar | Resuelto |
| SEC-03 | Sin autenticación ni auditoría | S, E, R | Modelado | Alto | Aceptar | Aceptado |

---

## Lo que dice esta tabla si se lee entera

De los tres defectos, **el escáner solo habría encontrado el primero**. Los
otros dos salieron de sentarse a razonar sobre el sistema, que es el módulo 1.

Es el argumento del taller en una línea: las herramientas encuentran errores
de implementación; los fallos de diseño hay que buscarlos pensando.
