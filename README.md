# Taller de Pruebas de Seguridad (*shift-left*)

Los talleres anteriores preguntan **"¿hace el sistema lo que debe?"**. Este
pregunta otra cosa: **"¿qué MÁS puede hacer?"**.

No es un matiz. Son dos conjuntos de entradas distintos. Probar el caso de uso
"buscar un votante por nombre" produce entradas como `Ana` o `Rodríguez`.
Probar el caso de abuso "leer datos que no me corresponden usando la búsqueda"
produce `' OR '1'='1`. **Ninguna suite funcional, por completa que sea, genera
la segunda familia por su cuenta.** Hay que pensarlas a propósito.

*Shift-left* significa mover esa pregunta al principio: al diseño y al commit,
en vez de a una auditoría externa tres semanas antes de salir a producción,
cuando arreglar cualquier cosa ya es carísimo.

---

## 🎯 Objetivos

- **Modelar amenazas** con STRIDE y convertir el resultado en pruebas.
- Escribir **casos de abuso** automatizados: inyección SQL, superficie
  expuesta, fuga de información en los errores.
- Configurar **SAST** (SpotBugs + findsecbugs) como puerta de calidad que
  rompe el build, no como informe que nadie lee.
- **Verificar al verificador**: comprobar que el analizador detecta un defecto
  sembrado a propósito, en vez de confiar en un informe vacío.
- Justificar por escrito cada supresión, y entender por qué una supresión sin
  justificación es peor que el hallazgo.
- Entender el papel del **SCA** (dependencias vulnerables) y de la detección
  de **secretos**, y por qué cada uno va en un momento distinto del pipeline.

---

## 📑 Índice

- [Sistema bajo prueba](#sistema-bajo-prueba)
- [Puesta en marcha](#puesta-en-marcha)
- [Módulos del taller](#módulos-del-taller)
- [Para entregar](#para-entregar-con-este-taller)
- [Rúbrica](#rúbrica)

---

## Sistema bajo prueba

La misma `registraduria` de los talleres anteriores, con una funcionalidad
nueva: **búsqueda pública de votantes inscritos** por parte del nombre.

Esa búsqueda está implementada **dos veces**, a propósito:

| Clase | Qué hace | Para qué existe |
|---|---|---|
| [`BuscadorSeguro`](registraduria/src/main/java/edu/unisabana/tyvs/registry/infrastructure/busqueda/BuscadorSeguro.java) | Consulta parametrizada + validación por lista blanca | Es la que la aplicación cablea de verdad |
| [`BuscadorVulnerable`](registraduria/src/main/java/edu/unisabana/tyvs/registry/infrastructure/busqueda/BuscadorVulnerable.java) | Concatena la entrada dentro del SQL | Material didáctico: el módulo 2 la explota |

> ⚠️ `BuscadorVulnerable` **no está expuesta por HTTP** y no hay ninguna
> propiedad de configuración que la active. Es deliberado: un interruptor
> tipo "modo inseguro" acaba encendido en producción algún viernes.

```text
.
├─ registraduria/
│   ├─ src/main/java/.../busqueda/     # las dos implementaciones
│   ├─ src/test/java/.../seguridad/    # módulos 2, 3 y 4
│   ├─ spotbugs-exclude.xml            # supresiones CON justificación
│   └─ pom.xml
├─ docs/
│   └─ modelado-de-amenazas.md         # módulo 1
├─ defectos.md
└─ defectos_template.md
```

---

## Puesta en marcha

```bash
cd registraduria
mvn clean verify
```

Eso ejecuta todo: las pruebas de abuso, el análisis estático y la puerta de
calidad. Debe terminar en `BUILD SUCCESS` con **21 pruebas** en verde.

> ⏱️ El build tarda unos 3 minutos porque **SpotBugs corre dos veces**. No es
> un descuido: la primera pasada genera un informe *sin* exclusiones que el
> módulo 3 necesita para comprobar que la herramienta detecta algo, y la
> segunda es la puerta de calidad *con* exclusiones. Está explicado en el
> `pom.xml`.

Para ver el informe de seguridad en detalle:

```bash
mvn spotbugs:gui        # navegador gráfico de hallazgos
cat target/spotbugsXml.xml
```

---

## Módulos del taller

### Módulo 1 — Modelado de amenazas ([`docs/modelado-de-amenazas.md`](docs/modelado-de-amenazas.md))

El único que no se automatiza, y por eso va primero. Las herramientas
verifican defensas; este módulo decide **cuáles hace falta construir**.

Un escáner encuentra lo que sabe buscar. No encuentra que el endpoint de
búsqueda devuelva el documento de cualquiera, ni que no haya registro de quién
inscribió a quién. Eso no son fallos de programación: son decisiones de diseño
que nadie tomó a propósito, y solo se encuentran razonando.

Se usa **STRIDE**, y su valor está en que obliga a recorrer las seis letras —
incluida la **R** de repudio, que casi todo el mundo se salta.

### Módulo 2 — Casos de abuso automatizados

Tres archivos, tres familias de defecto.

**[`InyeccionSqlTest`](registraduria/src/test/java/edu/unisabana/tyvs/seguridad/InyeccionSqlTest.java)** (7 pruebas). La primera es la más incómoda del taller:

```java
assertEquals(2, vulnerable.buscarPorNombre("Ana").size());
assertEquals(2, seguro.buscarPorNombre("Ana").size());
```

Las dos implementaciones son **indistinguibles** ante entradas normales. Si la
única evidencia que tuviera fuera su suite funcional, no podría saber cuál de
las dos está en producción. Una cobertura del 100% no distingue código seguro
de código vulnerable: mide líneas ejecutadas, no entradas imaginadas.

Después vienen los tres exploits, que se ejecutan de verdad:

| Entrada | Qué consigue |
|---|---|
| `' OR '1'='1` | Devuelve el padrón completo. Un fallo de control de acceso disfrazado de búsqueda. |
| `x%' UNION SELECT 1, clave, 0, false FROM credenciales --` | Devuelve una **contraseña de otra tabla**. El radio de daño no es el endpoint: es todo lo que alcance el usuario de base de datos. |
| `%` | Vacía la tabla entera **sin inyectar nada**: `%` es un comodín válido para `LIKE`. Es el defecto que se olvida al migrar a consultas parametrizadas. |

Y la prueba 06, que suele pasar desapercibida: `BuscadorSeguro` acepta
`O'Brien`. La diferencia entre *prohibir la comilla* y *parametrizar* es
exactamente esa persona, a la que si no un sistema le dice que su apellido no
es válido.

**[`SuperficieExpuestaIT`](registraduria/src/test/java/edu/unisabana/tyvs/seguridad/SuperficieExpuestaIT.java)** (8 pruebas). Actuator trae quince endpoints y la mayoría de la gente no sabe cuáles tiene abiertos, porque nunca los abrió: vinieron con la dependencia. Se comprueba que `/env`, `/heapdump`, `/beans`, `/configprops` y `/threaddump` responden 404, que `/health` no dice qué motor de base de datos hay detrás, y que un error no devuelve la traza de la excepción.

Que esto viva en la suite y no en una lista de chequeo es el punto entero de
*shift-left*: una lista se revisa una vez; una prueba se revisa en el commit
del viernes que expone `/env` "solo para depurar un momento".

**[`SecretosEnElCodigoTest`](registraduria/src/test/java/edu/unisabana/tyvs/seguridad/SecretosEnElCodigoTest.java)** (2 pruebas). Versión mínima de `gitleaks`. Un secreto commiteado no se arregla borrándolo después: el historial lo conserva y hay bots escaneando GitHub en tiempo real. La única respuesta correcta a "subí una credencial" es **rotarla**.

### Módulo 3 — SAST, y verificar al verificador

SpotBugs analiza el bytecode; **findsecbugs** le añade unas 130 reglas de
seguridad. Se engancha a `verify` con el goal `check`, no con `spotbugs`:
`check` **rompe el build**. Un informe que nadie mira no cambia el código.

Ahora la pregunta que casi nadie hace: **¿cómo sabe usted que su analizador
funciona?**

El modo de fallo típico de un SAST no es el falso positivo, que se ve y
molesta. Es el **silencio**: corre, tarda su minuto, escribe un informe vacío
y el semáforo se pone verde. Un informe vacío se ve exactamente igual tanto si
el código está limpio como si la herramienta estaba mal configurada — el
plugin que no se cargó, el umbral que alguien subió, el patrón excluido entero
en vez de la clase.

[`SastDetectaLaVulnerabilidadTest`](registraduria/src/test/java/edu/unisabana/tyvs/seguridad/SastDetectaLaVulnerabilidadTest.java) es el control: comprueba que findsecbugs está realmente cargado, que reporta `SQL_INJECTION_JDBC`, que señala el archivo correcto, y que **no** marca la implementación segura (un analizador solo sirve si distingue).

> Es, para el SAST, lo que las pruebas de mutación son para la suite unitaria:
> no medir cuánto se ejecutó, sino si se entera de algo.

**El archivo de supresiones es parte del material.** Lea
[`spotbugs-exclude.xml`](registraduria/spotbugs-exclude.xml): tiene tres
entradas y cada una lleva su justificación escrita. Dos detalles que importan:

- Se excluye la **clase** `BuscadorVulnerable`, no el **patrón**
  `SQL_INJECTION_JDBC`. Si alguien escribe otra inyección en cualquier otra
  clase, el build se pone rojo. Excluir el patrón habría desactivado la
  detección en todo el proyecto para siempre — es el error más común al
  escribir estos archivos.
- Dos hallazgos (`DMI_EMPTY_DB_PASSWORD`, `HARD_CODE_PASSWORD`) **no** están
  suprimidos: se arreglaron. Fue más rápido arreglarlos que justificarlos, y
  eso suele ser la señal.

### Módulo 4 — SCA y secretos

**SCA** (*Software Composition Analysis*) es analizar las dependencias, no el
código propio. Importa porque la mayor parte de lo que despliega usted no lo
escribió: entre transitivas, un Spring Boot mínimo arrastra más de cien
librerías.

Va en un **perfil aparte** y no en el build por defecto, por una razón
concreta:

```bash
mvn -Psca verify -Dnvd.api.key=SU_CLAVE
```

`dependency-check` descarga la base del NVD (cientos de megas) y desde 2023
exige clave de API para no sufrir límites de peticiones severos. Meterlo en el
build normal significaría que un `mvn verify` recién clonado tarda veinte
minutos o falla por red. En un proyecto real esto vive en un **job nocturno**,
exactamente por lo mismo.

> 💡 El umbral está en `failBuildOnCVSS=7` (severidad alta). Por debajo se
> registra pero no bloquea. Poner 0 suena responsable y produce el efecto
> contrario: el equipo aprende a ignorar el rojo.

---

## Cada técnica va en un momento distinto

Esto es lo que hay que llevarse del taller, más que cualquier herramienta
concreta:

| Técnica | Cuándo se ejecuta | Qué encuentra | Qué NO encuentra |
|---|---|---|---|
| Modelado de amenazas | Diseño, antes de escribir código | Fallos de diseño, autorización ausente, decisiones no tomadas | Errores de implementación |
| Detección de secretos | *Pre-commit* | Credenciales antes de que viajen | Todo lo demás |
| SAST | En cada *build* | Patrones peligrosos en el código propio | Fallos de lógica de negocio, autorización |
| Casos de abuso | En cada *build* | Lo que su modelo de amenazas predijo | Lo que no se le ocurrió |
| SCA | *Job* nocturno | CVE conocidos en dependencias | Vulnerabilidades sin publicar |
| DAST / pentest | Antes de publicar | Comportamiento del sistema completo, en ejecución | Lo que no está desplegado |

Ninguna sustituye a otra, y ninguna es suficiente sola. **Todas se pueden
falsificar subiendo el umbral hasta que el informe salga vacío** — que es
exactamente por lo que el módulo 3 existe.

---

## PARA ENTREGAR CON ESTE TALLER

### 1) Repositorio

- El proyecto completo, con `mvn clean verify` en verde tras un clon limpio.
- Historial de *commits* legible.

### 2) Modelado de amenazas (módulo 1)

- Diagrama de flujo de datos con los **límites de confianza** marcados.
- **8 amenazas nuevas** como mínimo, cubriendo las **seis** letras de STRIDE
  (al menos una de **R** y una de **D**).
- Priorización por impacto y facilidad de explotación.
- Una decisión por amenaza: **mitigar / aceptar / transferir**. *"Pendiente"*
  no es una decisión.

### 3) Casos de abuso (módulo 2)

- **Dos amenazas de su modelo convertidas en pruebas automatizadas**, dentro
  de la suite. Es lo que cierra el ciclo entre el documento y el código.
- Análisis escrito de la prueba 01 de `InyeccionSqlTest`: ¿por qué una
  cobertura del 100% no habría detectado la vulnerabilidad?

### 4) SAST (módulo 3)

- Introduzca **una vulnerabilidad nueva** en el código (no en
  `BuscadorVulnerable`), compruebe que el build se rompe, pegue la salida y
  arréglela. Sin esa evidencia no consta que la puerta funcione.
- **Una supresión propia** en `spotbugs-exclude.xml`, con su justificación
  escrita, y la explicación de por qué se acotó como se acotó.

### 5) SCA (módulo 4)

- Ejecute el perfil `sca` y pegue el resumen.
- Elija **una** vulnerabilidad reportada e investíguela: ¿afecta realmente a
  este proyecto? ¿se usa el código vulnerable? Un CVE en una librería que
  nunca se invoca es ruido, y distinguir eso es el trabajo real.

### 6) Gestión de defectos

- `defectos.md` con **2 defectos de seguridad** como mínimo, uno de ellos
  encontrado por usted y no por una herramienta.

### 7) Reflexión final (en el Wiki)

Una de estas dos, a elección:

- ¿Cuál de las seis técnicas de la tabla añadiría **primero** a un proyecto
  que hoy no tiene ninguna, y por qué esa?
- Su equipo tiene el SAST en verde desde hace seis meses. ¿Qué haría para
  averiguar si eso significa "no hay vulnerabilidades" o "la herramienta dejó
  de mirar"?

---

## Rúbrica

| **Criterios de evaluación** | **Indicadores** | **Excelente (5 pts)** | **Bueno (4 pts)** | **Necesita mejorar (3.5 pts)** | **Deficiente (2.5 pts)** | **No cumple (0 pts)** |
|---|---|---|---|---|---|---|
| **Estructura y ejecución** | `mvn clean verify` corre sin pasos manuales. | Todo verde tras un clon limpio. | Corre con ajustes menores. | Requiere pasos no documentados. | Falla en varias pruebas. | No ejecuta. |
| **Modelado de amenazas** **(vale por 2)** | Cobertura de STRIDE y calidad de las amenazas. | 8+ amenazas, las seis letras, redactadas como quién/cómo/qué obtiene, con decisión por cada una. | 6–7 amenazas, cuatro o cinco letras. | Menos de 6, o confunde amenazas con controles ausentes. | Lista genérica sin relación con el sistema. | No entrega modelado. |
| **Casos de abuso** **(vale por 2)** | Amenazas convertidas en pruebas. | 2+ amenazas propias automatizadas, con aserciones que fallarían si la defensa se quita. | 2 pruebas correctas pero poco específicas. | 1 prueba, o no trazable a una amenaza. | Pruebas que no verifican nada. | No automatiza ninguna. |
| **SAST y puerta de calidad** **(vale por 2)** | Configuración e interpretación. | Vulnerabilidad introducida, build roto con evidencia, arreglada; supresión propia bien acotada y justificada. | Puerta funcionando, evidencia parcial. | Ejecuta el análisis sin romper el build. | Sube el umbral hasta que el informe sale vacío. | No configura SAST. |
| **Verificación del verificador** | Entiende el fallo silencioso. | Explica con evidencia por qué un informe vacío no es una garantía. | Lo menciona sin evidencia propia. | Repite el concepto sin aplicarlo. | No lo aborda. | — |
| **SCA** | Análisis de dependencias. | Perfil ejecutado y un CVE investigado a fondo (¿se usa el código vulnerable?). | Ejecutado, análisis superficial. | Solo pega el informe. | Menciona sin ejecutar. | No ejecuta SCA. |
| **Gestión de defectos** | Registro y trazabilidad. | 2+ defectos documentados, uno hallado sin herramienta. | Defectos con detalle parcial. | Registro superficial. | Mención sin evidencia. | No entrega `defectos.md`. |
| **Documentación y reflexión** | Wiki con análisis. | Reflexión crítica que conecta técnica y momento del pipeline. | Clara pero sin profundidad. | Incompleta. | Mínima. | Sin documentación. |

> **Cómo suma**: 11 criterios × 5 pts = **55 puntos**. Son 8 filas, pero
> *Modelado de amenazas*, *Casos de abuso* y *SAST* valen por dos cada una —
> son el núcleo del taller.

| Rango de puntaje | Desempeño |
| ---------------- | --------- |
| 50 – 55 | Excelente dominio técnico y metodológico. |
| 39 – 49 | Buen trabajo con documentación o análisis parcial. |
| 33 – 38 | Cumple con lo básico pero sin profundidad. |
| < 33 | No cumple con los criterios mínimos del taller. |

---

## Créditos y uso académico

Material del curso **Testing y Validación de Software**, Maestría en
Ingeniería de Software, **Universidad de La Sabana**.

Las vulnerabilidades de este repositorio son **deliberadas y están contenidas**
en un entorno local sin datos reales. Existen para aprender a detectarlas.
Aplicar estas técnicas contra sistemas de terceros sin autorización escrita es
ilegal en Colombia (Ley 1273 de 2009) y en la mayoría de las jurisdicciones.
