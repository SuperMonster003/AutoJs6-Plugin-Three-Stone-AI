<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/app/src/main/res/mipmap/ic_launcher_on_device_ai.png?raw=true" alt="on-device-ai-ic-launcher" border="0" width="128" />
  </p>

  <p>Plugin de IA local. Genera texto en streaming en el dispositivo con LiteRT-LM, sin red</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-On-Device-AI?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-On-Device-AI?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-On-Device-AI?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Idiomas

******

El README.md actual admite los siguientes idiomas:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-fr.md)
- Español [es] # actual
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ar.md)

******

### Introducción

******

On-Device AI es el plugin oficial de generación de texto con IA local para AutoJs6. Ejecuta en la CPU los modelos LiteRT-LM importados por el usuario, acepta un historial de mensajes de texto plano y devuelve texto plano o texto JSON restringido por un schema mediante una sesión de streaming controlada. Toda la inferencia ocurre localmente: sin acceso a la red y sin subir datos.

******

### Funciones

******

- Importar un paquete de modelo `.litertlm` con el selector del sistema Android y guardar una copia verificada en el almacenamiento privado de la aplicación.
- Comprobar el almacenamiento privado antes de abrir el selector, mostrar el presupuesto de importación actual y la ocupación estimada de la copia privada, y volver a comprobar el archivo seleccionado antes de copiarlo.
- Crear solicitudes de generación local con historial system, user y assistant en texto sin formato.
- Transferir `temperature`, `topK`, `topP` y `maxTokens` desde `ai.ask`, `ai.chat` y `ai.stream` de AutoJs6 hasta LiteRT-LM.
- Restringir la salida de forma nativa con JSON Schema de LiteRT-LM mediante `structuredJson` y `responseSchema` de AutoJs6; los valores completos siguen siendo texto JSON para `JSON.parse`.
- Informar los recuentos exactos de tokens de entrada, salida y totales de LiteRT-LM, junto con la duración de generación del proveedor, mediante `ai.chat().usage` y los eventos usage de streaming.
- Mantener el contexto de varios turnos en una única Conversation nativa de LiteRT-LM mediante `ai.session` de AutoJs6, enviando solo el nuevo prompt de usuario en los turnos posteriores.
- Reutilizar el Engine inicializado según el SHA-256 del modelo para evitar arranques en frío repetidos en solicitudes consecutivas al mismo modelo.
- Inicializar opcionalmente cada modelo importado una vez, conservar su estado Disponible/Incompatible y repetir la comprobación desde el gestor de modelos.
- Entregar chunks de texto en orden con contrapresión por credits y publicar un solo estado terminal completado, fallido o cancelado.
- Enumerar, seleccionar y renombrar modelos importados, eliminar modelos no seleccionados y recuperar desde el gestor los archivos de modelos sin referencia.
- Funcionar completamente en el dispositivo con un backend CPU, sin descargar modelos ni llamar a un servicio de inferencia remoto.

******

### Formatos de modelo y datos

******

La versión 1 declara únicamente el siguiente alcance:

```text
model package: .litertlm
input: text/plain message history plus application/json response schema
output: streamed text/plain or application/json text chunks
runtime: LiteRT-LM 0.15.0
```

******

### Interfaz del plugin

******

El host descubre y llama al plugin con las siguientes identidades:

```text
service action: org.autojs.plugin.ON_DEVICE_AI
plugin id: on-device-ai
protocol provider id: autojs6.on-device-ai
engine: on-device-ai
variant: default
protocol: V1.2
required host build: 5276
```

El plugin declara ejecución ON_DEVICE y modo credential NONE. Declara las capacidades `streaming`, `usage`, `persistent-session` y `structured-json`, acepta mensajes `text/plain` y schemas de respuesta `application/json`, y emite texto `text/plain` o `application/json`.

Se requiere la build 5276 o posterior del host. Las versiones incluyen variantes APK arm64-v8a, x86_64, universal.

******

### Estado de integración con el host

******

> En AutoJs6 (compilación 5276 y posteriores), `ai.ask`, `ai.chat` y `ai.stream` admiten la ruta de plugin local. `ai.session({ plugin: true })` crea una Conversation persistente de varios turnos cuyas llamadas posteriores a `ask`, `chat` y `stream` solo envían el nuevo prompt de usuario. `ai.ask(messages, { plugin: true })` conserva en orden los mensajes de texto sin formato con roles `system`, `user` y `assistant`, y el último mensaje debe tener el rol `user`. `ai.chat` devuelve recuentos exactos de tokens en `usage` y la duración medida en `usage.raw.durationMillis`; `ai.stream` emite el mismo usage acumulado antes de completarse. Pase `plugin: true` para seleccionar este plugin, y el ID de modelo puede omitirse cuando solo hay un modelo importado; `ai.models({ plugin: true })` enumera los modelos importados. Si el plugin no está instalado, no está habilitado en el Centro de plugins o no tiene modelo, los scripts reciben un error claro. También se admite el selector explícito `plugin: { component, providerId, modelId }`. `responseSchema` activa implícitamente la salida estructurada; `structuredJson: true` sin schema usa uno predeterminado con raíz de objeto. `ai.ask` y `ai.chat().text` siguen devolviendo texto JSON, los deltas de streaming son texto JSON parcial y una sesión persistente conserva un único schema fijo en todos sus turnos.

******

### Seguridad y privacidad

******

El plugin no solicita permisos de red ni almacenamiento. Lee el modelo solo mediante un URI concedido por el selector del sistema, calcula SHA-256 mientras lo copia al directorio privado `files/models`, ejecuta fsync y lo activa mediante reemplazo atómico del pointer en el mismo directorio. Los servicios también verifican el nombre del paquete AutoJs6, la propiedad del UID y las firmas coincidentes.

******

### Límites operativos

******

- La importación de un modelo tiene un límite estricto de 8 GiB y debe dejar al menos 256 MiB libres.
- Un coordinador de importación única con alcance de aplicación mantiene el trabajo durante la recreación de Activity. Un pending journal sincronizado con fsync permite la recuperación en arranque frío y limpia los archivos temporales stale `.incoming`, `.current` y `.pending`. La recuperación solo elimina un destination creado por el intento actual y nunca publicado mediante current metadata; se conservan las generaciones publicadas, current e históricas con nombre de hash.
- Para evitar condiciones de carrera entre procesos con el proceso aislado `:provider`, las importaciones no eliminan automáticamente las generaciones anteriores con nombre de hash SHA-256. El gestor puede eliminar modelos no seleccionados del catálogo y recuperar archivos con nombre de hash que ya no estén referenciados.
- Solo una sesión de generación puede estar activa en el proceso. Los descriptores se duplican antes del trabajo asíncrono y se cierran según las cuotas del protocolo.
- El provider conserva como máximo un Engine inicializado. Las solicitudes consecutivas al mismo modelo lo reutilizan; se libera de inmediato al cambiar de modelo, tras cinco minutos de inactividad o, de forma segura, después de la sesión activa cuando Android informa de presión de memoria explícita.
- La comprobación de un modelo solo demuestra que `Engine.initialize()` funciona en el dispositivo y entorno incluidos actuales; no evalúa la calidad de la salida y puede repetirse tras cambios de dispositivo o entorno.
- El provider anuncia un máximo de contexto de 256 KiB y un máximo de salida de 64 KiB. Las solicitudes y modelos pueden imponer límites menores.
- El schema de respuesta debe ser un objeto JSON de no más de 64 KiB. Las palabras clave admitidas son las implementadas por el runtime LiteRT-LM/LLGuidance incluido; la salida completa se analiza y valida estrictamente, por lo que debe reservarse suficiente `maxTokens` para todo el valor JSON.
- `maxTokens` admite enteros de 1 a 2.147.483.647. `temperature` debe ser finito y no negativo, `topK` un entero positivo y `topP` finito entre 0 y 1. Si se omiten los tres controles de muestreo se conservan los valores del modelo o motor; una sustitución parcial completa los controles omitidos con la base de LiteRT-LM `topK: 1`, `topP: 0.95` y `temperature: 1`.
- El streaming usa credits finitos y chunks limitados para evitar buffers ilimitados o callbacks sin contrapresión.
- Los tokens de usage proceden directamente de los contadores de caché KV y decode de Conversation en LiteRT-LM, sin estimaciones por caracteres. `durationMillis` mide solo la generación del plugin y excluye descubrimiento, enlace, listado de modelos y despacho del host.
- Una `ai.session` persistente permite un turno activo y conserva su Conversation nativa tras completarse normalmente; debe recrearse después de una cancelación, timeout, error de generación o cierre explícito.
- La cancelación, el cierre de sesión y el timeout detienen la publicación y finalizan la solicitud con un solo estado terminal.

******

### Capacidades no declaradas

******

- No se declaran reasoning ni tools.
- No se aceptan mensajes con rol tool, schemas de herramientas, tool calls ni tool results.
- No hay descubrimiento de modelos por red, descarga, inferencia cloud ni flujo credential.
- No se declara backend GPU o NPU. La extensión `.litertlm` por sí sola no garantiza que el runtime LiteRT-LM actual pueda cargar el modelo.

******

### Hoja de ruta

******

La hoja de ruta se organiza en funciones entregables para el usuario, cada una verificable por separado

- [Ver ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/ROADMAP.md)

******

### Historial de versiones

******

# v1.1.0

###### 2026/08/20

* `Función` Plugin renombrado a On-Device AI, posicionado como el plugin oficial de IA local de AutoJs6
* `Función` Compatible con el selector abreviado `plugin: true` de `ai.ask`/`ai.chat`/`ai.stream` y la enumeración de modelos `ai.models` de AutoJs6
* `Función` Transferencia de `temperature`, `topK`, `topP` y `maxTokens` mediante el protocolo On-Device AI 1.1 a los controles de muestreo y tokens de salida de LiteRT-LM
* `Función` Informe de los recuentos exactos de tokens de entrada, salida y totales de LiteRT-LM, junto con la duración de generación medida por el proveedor, mediante `ai.chat().usage` y eventos usage de streaming
* `Función` Sesiones persistentes del protocolo On-Device AI 1.2 y reutilización de Conversation de varios turnos con `ai.session` de AutoJs6 sin reenviar el historial anterior
* `Función` Decodificación nativa restringida por JSON Schema de LiteRT-LM mediante `structuredJson` y `responseSchema` de AutoJs6, compatible con llamadas únicas, streaming y sesiones persistentes, con validación estricta del JSON completo
* `Mejora` Descripción del plugin, instrucciones y README en 10 idiomas actualizados conforme a la formalización de la ruta de plugin local `ai.*`
* `Mejora` ROADMAP reescrito como hoja de ruta de funciones con elementos verificables individualmente

# v1.0.0

###### 2026/08/08

* `Función` Provider en el dispositivo para el protocolo On-Device AI V1 con ID y motor `on-device-ai`, provider ID `autojs6.on-device-ai` y variante `default`
* `Función` Generación de texto sin formato con LiteRT-LM y CPU, historial system, user y assistant y streaming controlado por credits
* `Función` Importación SAF de `.litertlm` al almacenamiento privado con límite de 8 GiB, reserva de espacio, SHA-256, fsync y activación atómica
* `Función` Una sesión activa, I/O limitada, cuotas de descriptores, cancelación, timeout, un estado terminal y verificación del llamador AutoJs6 con la misma firma
* `Función` Omisión explícita de las capacidades reasoning, tools, structured JSON, usage, red y credential
* `Función` APK arm64-v8a, x86_64 y universal con README, changelog, interfaz Android e instrucciones del plugin en 10 idiomas
* `Función` Pantalla de gestión de modelos para consultar el catálogo completo y el espacio usado en el almacenamiento privado, con selección atómica del modelo actual sin copiar archivos de modelo
* `Mejora` Conservación de generaciones anteriores con nombre de hash SHA-256 tras una importación de reemplazo para evitar carreras entre procesos con `:provider`, por lo que los archivos conservados siguen ocupando almacenamiento privado
* `Mejora` Se añadió un coordinador de importación única con alcance de aplicación y un pending journal sincronizado con fsync para mantener el trabajo al recrear Activity, recuperar en arranque frío, limpiar temporales stale y limitar el borrado a destinos creados por el intento actual y nunca publicados, mientras se conservan las generaciones publicadas, current e históricas con nombre de hash
* `Dependencia` Se añadió LiteRT-LM 0.15.0 para generación de texto con CPU en el dispositivo

##### Más versiones

* [CHANGELOG-es.md](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/app/src/main/assets/doc/CHANGELOG-es.md)

******

### Compilación

******

```powershell
.\gradlew.bat :app:assembleDebug
```

Compilación de versión:

```powershell
.\gradlew.bat :app:assembleRelease
```

Los parámetros proceden de `version.properties`. El SDK mínimo actual es 24, el SDK objetivo es 36 y se requiere JDK 21 o posterior.

La ABI del protocolo se suministra mediante AAR locales del repositorio en `libs`:

```text
common-plugin-api.aar
protocol-wire-api.aar
ai-common-api.aar
on-device-ai-api.aar
```

El runtime usa LiteRT-LM 0.15.0 desde Maven. Las builds de versión conservan las clases de LiteRT-LM y producen dos APK por ABI y un APK universal.

******

### Licencia

******

El código fuente del proyecto se distribuye bajo MPL-2.0. LiteRT-LM y otros componentes de terceros mantienen sus licencias respectivas.

******

### Estructura de recursos

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/assets/doc/CHANGELOG-*.md
app/src/main/res/values-*/strings.xml
```

`.python/generate_markdown.py` genera README y changelogs integrados en 10 idiomas desde fuentes JSON. Las cadenas Android se mantienen en sus propios directorios de recursos.

******

### Enlaces

******

- Documentación de AutoJs6: https://docs.autojs6.com
- Proyecto LiteRT-LM: https://github.com/google-ai-edge/LiteRT-LM
