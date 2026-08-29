# Registro de Defectos de Seguridad (plantilla)

Documente aqui los defectos que encuentre. Debe incluir al menos **dos**, y
al menos **uno tiene que haberlo encontrado usted razonando**, no una
herramienta. Encontrar lo que el escaner ya senala no demuestra nada.

---

## Formato 1: Lista detallada

### Defecto XX — <titulo breve>

- **Letra STRIDE**: S | T | R | I | D | E
- **Como se detecto**: modelado de amenazas | caso de abuso | SAST | SCA | revision manual
- **Precondiciones**: que necesita el atacante antes de empezar (nada / una
  cuenta / estar en la red interna). Cambia por completo la severidad.
- **Pasos para reproducir**:
  1. ...
  2. ...
- **Que obtiene el atacante**: sea concreto. "Acceso indebido" no dice nada;
  "el padron completo con los numeros de documento" si.
- **Impacto**: Alto | Medio | Bajo
- **Facilidad de explotacion**: Trivial | Requiere conocimiento | Requiere acceso previo
- **Decision**: Mitigar | Aceptar | Transferir  ("Pendiente" no es una decision)
- **Mitigacion propuesta**: ...
- **Prueba que lo verifica**: ruta al archivo y nombre del metodo
- **Estado**: Abierto | En progreso | Resuelto

---

## Formato 2: Tabla de seguimiento

| ID | Titulo | STRIDE | Como se detecto | Impacto | Decision | Estado |
|----|--------|--------|-----------------|---------|----------|--------|
| SEC-01 | ... | ... | ... | ... | ... | Abierto |

---

## Sobre como se redacta un defecto de seguridad

Un defecto de seguridad no es "falta validacion". Eso es la ausencia de una
defensa: no dice quien ataca, que consigue, ni por que importa.

> ❌ "El endpoint de busqueda no valida la entrada."
>
> ✅ "Un usuario anonimo envia `' OR '1'='1` en el parametro `nombre` de
> `/buscar` y obtiene el padron electoral completo, incluidos los numeros de
> documento de todas las personas inscritas. No requiere autenticacion ni
> conocimiento previo del sistema. Impacto alto, explotacion trivial."

Solo la segunda version se puede priorizar, y solo la segunda se puede
convertir en una prueba automatizada.
