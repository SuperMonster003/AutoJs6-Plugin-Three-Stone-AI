<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="three-stone-ai-ic-launcher" border="0" width="128" />
  </p>

  <p>Plugin de IA unificado. LiteRT-LM sigue local; los destinos en línea siempre se eligen explícitamente</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Idiomas

******

El README.md actual admite los siguientes idiomas:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-fr.md)
- Español [es] # actual
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ar.md)

******

### Introducción

******

3-Stone AI es el plugin oficial de generación de texto con IA para AutoJs6. Ejecuta los modelos LiteRT-LM importados por el usuario en un backend CPU seleccionado explícitamente o en una GPU compatible, acepta un historial de mensajes de texto plano y devuelve texto plano o texto JSON restringido por un schema mediante una sesión de streaming controlada. Las llamadas actuales del host AI Provider V1 usan esos objetivos locales sin acceso a la red ni subida de datos. Los ajustes del plugin gestionan perfiles de OpenAI, Anthropic, Gemini, DeepSeek, OpenRouter y OpenAI Compatible personalizados, credenciales cifradas, el destino en línea predeterminado, las redes medidas y pruebas de conexión explícitas. El chat del iniciador puede vincular explícitamente cada conversación a un destino local o en línea configurado; las APIs V1 del host siguen siendo solo locales.

******

### Funciones

******

- Importar un paquete de modelo `.litertlm` con el selector del sistema Android y guardar una copia verificada en el almacenamiento privado de la aplicación.
- Descargar un modelo LiteRT Community fijado y sin autenticación a una ubicación SAF elegida por el usuario, con progreso, cancelación, limpieza y verificación exacta de tamaño y SHA-256.
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
- Seleccionar explícitamente `cpu`, `gpu` o `npu` desde AutoJs6; CPU es el valor predeterminado, GPU se expone solo tras una prueba de carga OpenCL y NPU se informa como no disponible porque su runtime EAP no está incluido.
- Gestionar perfiles en línea integrados y personalizados, credenciales de Android Keystore, el destino en línea predeterminado, redes medidas y pruebas de conexión explícitas y limitadas desde los ajustes.
- Vincular cada conversación del iniciador a una instantánea de destino local o en línea; al cambiar una conversación con mensajes se recomienda una nueva, y continuar exige confirmación explícita y registra el cambio.
- Guardar una instantánea del destino, proveedor, modelo y ubicación reales en cada respuesta del asistente; la regeneración reutiliza exactamente el destino registrado, falla explícitamente si cambia o deja de estar disponible y nunca recurre silenciosamente al destino actual de la conversación.

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
service action: org.autojs.plugin.AI_PROVIDER
plugin id: three-stone-ai
protocol provider id: autojs6.three-stone-ai
engine: three-stone-ai
variant: default
protocol: V1.2-V1.3
required host build: 5276
```

La superficie pública de AI Provider V1 declara ejecución ON_DEVICE y modo credential NONE. Declara las capacidades `streaming`, `usage`, `persistent-session` y `structured-json`, acepta mensajes `text/plain` y schemas de respuesta `application/json`, y emite texto `text/plain` o `application/json`. El protocolo 1.3 añade perfiles backend explícitos y disponibilidad por dispositivo sin retorno silencioso a CPU. Los objetivos REMOTE internos del plugin no se anuncian mediante V1.

Se requiere la build 5276 o posterior del host. Las versiones incluyen variantes APK arm64-v8a, x86_64, universal.

******

### Estado de integración con el host

******

> En AutoJs6 (compilación 5276 y posteriores), `ai.ask`, `ai.chat` y `ai.stream` admiten la ruta de plugin local. `ai.session({ plugin: true })` crea una Conversation persistente de varios turnos cuyas llamadas posteriores a `ask`, `chat` y `stream` solo envían el nuevo prompt de usuario. `ai.ask(messages, { plugin: true })` conserva en orden los mensajes de texto sin formato con roles `system`, `user` y `assistant`, y el último mensaje debe tener el rol `user`. `ai.chat` devuelve recuentos exactos de tokens en `usage` y la duración medida en `usage.raw.durationMillis`; `ai.stream` emite el mismo usage acumulado antes de completarse. Pase `plugin: true` para seleccionar este plugin, y el ID de modelo puede omitirse cuando solo hay un modelo importado; `ai.models({ plugin: true })` enumera los modelos y sus `backendProfiles`. La generación acepta `backend: 'cpu' | 'gpu' | 'npu'`; un perfil no disponible falla explícitamente y nunca vuelve a CPU. Si el plugin no está instalado, no está habilitado en el Centro de plugins o no tiene modelo, los scripts reciben un error claro. También se admite el selector explícito `plugin: { component, providerId, modelId }`. `responseSchema` activa implícitamente la salida estructurada; `structuredJson: true` sin schema usa uno predeterminado con raíz de objeto. `ai.ask` y `ai.chat().text` siguen devolviendo texto JSON, los deltas de streaming son texto JSON parcial y una sesión persistente conserva un único schema y backend fijos en todos sus turnos.

******

### Seguridad y privacidad

******

El plugin solicita `INTERNET` para descargas de modelos recomendados iniciadas por el usuario y solicitudes a un objetivo online configurado por el usuario; la generación local V1 actual no usa la red. No solicita ningún permiso general de almacenamiento. Las descargas usan revisiones HTTPS inmutables, tamaño y SHA-256 fijados, y solo escriben en la ubicación SAF elegida; deben superar cabecera LiteRT-LM, tamaño, resumen, flush y fsync. La importación sigue leyendo solo un URI del selector, escribe una copia verificada en `files/models` y la activa atómicamente. Los servicios también verifican el paquete AutoJs6, el UID y las firmas.

******

### Límites operativos

******

- La importación de un modelo tiene un límite estricto de 8 GiB y debe dejar al menos 256 MiB libres.
- Solo se ejecuta una descarga en el proceso. Recrear la Activity conserva progreso y cancelación; al cancelar o fallar se elimina o trunca el destino. Terminar el proceso aún puede dejar un documento externo parcial que debe borrarse manualmente.
- Un coordinador de importación única con alcance de aplicación mantiene el trabajo durante la recreación de Activity. Un pending journal sincronizado con fsync permite la recuperación en arranque frío y limpia los archivos temporales stale `.incoming`, `.current` y `.pending`. La recuperación solo elimina un destination creado por el intento actual y nunca publicado mediante current metadata; se conservan las generaciones publicadas, current e históricas con nombre de hash.
- Para evitar condiciones de carrera entre procesos con el proceso aislado `:provider`, las importaciones no eliminan automáticamente las generaciones anteriores con nombre de hash SHA-256. El gestor puede eliminar modelos no seleccionados del catálogo y recuperar archivos con nombre de hash que ya no estén referenciados.
- Solo una sesión de generación puede estar activa en el proceso. Los descriptores se duplican antes del trabajo asíncrono y se cierran según las cuotas del protocolo.
- El provider conserva como máximo un Engine inicializado, indexado por SHA-256 de modelo y perfil backend. Las solicitudes con la misma pareja lo reutilizan; cambiar cualquier clave, cinco minutos de inactividad o la presión de memoria explícita lo liberan de forma segura.
- La comprobación de un modelo solo demuestra que `Engine.initialize()` funciona en el dispositivo y entorno incluidos actuales; no evalúa la calidad de la salida y puede repetirse tras cambios de dispositivo o entorno.
- El provider anuncia un máximo de contexto de 256 KiB y un máximo de salida de 64 KiB. Las solicitudes y modelos pueden imponer límites menores.
- El schema de respuesta debe ser un objeto JSON de no más de 64 KiB. Las palabras clave admitidas son las implementadas por el runtime LiteRT-LM/LLGuidance incluido; la salida completa se analiza y valida estrictamente, por lo que debe reservarse suficiente `maxTokens` para todo el valor JSON.
- `maxTokens` admite enteros de 1 a 2.147.483.647. Si se omite, el recuento de tokens de salida queda a cargo del modelo o motor; se mantiene el límite de seguridad de salida de 64 KiB del proveedor. `temperature` debe ser finito y no negativo, `topK` un entero positivo y `topP` finito entre 0 y 1. Si se omiten los tres controles de muestreo se conservan los valores del modelo o motor; una sustitución parcial completa los controles omitidos con la base de LiteRT-LM `topK: 1`, `topP: 0.95` y `temperature: 1`.
- El streaming usa credits finitos y chunks limitados para evitar buffers ilimitados o callbacks sin contrapresión.
- Los tokens de usage proceden directamente de los contadores de caché KV y decode de Conversation en LiteRT-LM, sin estimaciones por caracteres. `durationMillis` mide solo la generación del plugin y excluye descubrimiento, enlace, listado de modelos y despacho del host.
- Una `ai.session` persistente permite un turno activo y conserva su Conversation nativa tras completarse normalmente; debe recrearse después de una cancelación, timeout, error de generación o cierre explícito.
- La cancelación, el cierre de sesión y el timeout detienen la publicación y finalizan la solicitud con un solo estado terminal.

******

### Capacidades no declaradas

******

- No se declaran reasoning ni tools.
- No se aceptan mensajes con rol tool, schemas de herramientas, tool calls ni tool results.
- No hay descubrimiento de modelos por red ni descargas desde URL arbitrarias. El chat del iniciador solo expone modelos locales importados y perfiles en línea configurados explícitamente; el host AI Provider V1 sigue siendo solo local y solo puede descargarse el catálogo integrado fijado.
- No se declara inferencia NPU: el perfil es visible como `unavailable` con `npu-runtime-not-packaged`. GPU solo se declara si `libOpenCL.so` puede cargarse y la extensión `.litertlm` aún no garantiza que el modelo se inicialice.

******

### Hoja de ruta

******

La hoja de ruta se organiza en funciones entregables para el usuario, cada una verificable por separado

- [Ver ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/ROADMAP.md)

******

### Historial de versiones

******

# v1.1.0

###### 2026/08/24

* `Función` Identidad de marca y de ejecución del plugin oficial de IA local de AutoJs6 consolidada como 3-Stone AI
* `Función` La integración entre procesos usa las identidades neutrales `ai-provider-api`, `org.autojs.plugin.ai.provider.api`, `org.autojs.plugin.AI_PROVIDER` e `IAiProvider`/`IAiSession`/`IAiCallback` sin conservar alias de las identidades reemplazadas
* `Función` Compatible con el selector abreviado `plugin: true` de `ai.ask`/`ai.chat`/`ai.stream` y la enumeración de modelos `ai.models` de AutoJs6
* `Función` Transferencia de `temperature`, `topK`, `topP` y `maxTokens` mediante el protocolo AI Provider 1.1 a los controles de muestreo y tokens de salida de LiteRT-LM
* `Función` Informe de los recuentos exactos de tokens de entrada, salida y totales de LiteRT-LM, junto con la duración de generación medida por el proveedor, mediante `ai.chat().usage` y eventos usage de streaming
* `Función` Sesiones persistentes del protocolo AI Provider 1.2 y reutilización de Conversation de varios turnos con `ai.session` de AutoJs6 sin reenviar el historial anterior
* `Función` Decodificación nativa restringida por JSON Schema de LiteRT-LM mediante `structuredJson` y `responseSchema` de AutoJs6, compatible con llamadas únicas, streaming y sesiones persistentes, con validación estricta del JSON completo
* `Función` Perfiles backend explícitos `cpu`, `gpu` y `npu` mediante el protocolo 1.3 y las opciones de generación de AutoJs6, con informe de compatibilidad del dispositivo, aislamiento de caché por modelo/perfil y sin fallback desde perfiles no disponibles; GPU solo se declara tras una prueba de carga de OpenCL y NPU permanece no disponible porque su runtime EAP no está empaquetado
* `Función` Descarga directa de modelos LiteRT Community fijados a una ubicación SAF elegida, con progreso, cancelación precisa, limpieza, verificación de cabecera LiteRT-LM, tamaño y SHA-256, e importación directa posterior
* `Función` Se añadió un espacio de conversación iniciable con Markdown en streaming, historial persistente, aviso al sustituir una rama tras editar mensajes anteriores, búsqueda con varios resultados y entrada adaptada al teclado
* `Función` Se añadieron ajustes de aplicación para color del tema, modo oscuro, idioma, información de la aplicación y del desarrollador e historial de versiones, con Seguir AutoJs6 como valor predeterminado cuando sea posible
* `Función` Se añadieron ajustes de conversación para tamaño de fuente, comportamiento de Enter, output tokens ilimitados o personalizados y muestreo `temperature`, `topK` y `topP` predeterminado por el modelo o personalizado
* `Función` Se renderiza contenido `$\text{...}$` en línea durante el streaming, con comandos matemáticos comunes y estilos de superíndice y subíndice
* `Función` Se añadió un almacén de credenciales gestionado por el plugin con Android Keystore, AES-256-GCM, texto cifrado autenticado vinculado al profile, archivos privados atómicos entre procesos, consultas limitadas al estado configured y borrado inmediato del texto sin cifrar
* `Función` Se añadió un repositorio estricto y sin secretos de perfiles en línea para endpoints OpenAI Compatible solo por HTTPS, con UUID canónicos, metadatos atómicos entre procesos y reemplazo o eliminación obligatorios de la credencial al cambiar el provider o el origin
* `Función` Se añadió el backend interno del plugin para ejecución HTTPS OpenAI Compatible con perfiles de baseUrl, credencial y modelo personalizados, streaming SSE acotado y fallback JSON, cancelación precisa, usage del provider, historial persistente de turnos completados, mapping de JSON Schema y errores fijos sin datos sensibles; el enrutamiento del host AI Provider V1 sigue limitado a objetivos locales
* `Función` Se añadieron preajustes de OpenAI, Anthropic, Gemini, DeepSeek y OpenRouter alineados con el catálogo del host; la capa unificada de ejecución en línea reutiliza el protocolo compatible con OpenAI y adapta por separado la autenticación, las solicitudes, los terminales SSE, el uso y JSON Schema nativos de Anthropic Messages y Gemini GenerateContent, sin fallback entre protocolos ni entre local y en línea
* `Función` Se añadió la UI de servicios en línea en 10 idiomas para añadir, editar y eliminar perfiles, sustituir y borrar claves API sin mostrarlas, elegir el destino predeterminado, exigir permiso para redes medidas antes de leer credenciales y ejecutar pruebas explícitas cancelables de hasta 120 segundos; los ajustes comparten el documento atómico entre procesos y AI Provider V1 sigue siendo solo local
* `Función` Se añadió un selector unificado de destinos locales y en la nube al chat del iniciador: cada conversación conserva una instantánea de destino, las conversaciones con mensajes recomiendan iniciar una nueva al cambiar y continuar con el contexto exige confirmación explícita y registra el cambio
* `Función` Se añadió a cada respuesta del asistente una instantánea del destino, proveedor, modelo y ubicación reales; la regeneración reutiliza exactamente el destino registrado, falla explícitamente si cambia o deja de estar disponible y nunca recurre silenciosamente al destino actual de la conversación
* `Corrección` Se eliminaron los límites implícitos de 256 tokens y 4 KiB de los ejemplos ejecutables: omitir `maxTokens` usa ahora el valor predeterminado del modelo o motor y el ejemplo Binder directo usa los 64 KiB completos permitidos por el proveedor
* `Corrección` Se corrigió el ejemplo Binder de bajo nivel de las instrucciones localizadas en 10 idiomas, que aún invocaba el constructor `AiGenerationOptions` de 14 argumentos del protocolo 1.1 y fallaba con la API del protocolo 1.3
* `Corrección` Se corrigió que el gestor de modelos conservara los colores de texto del tema claro en el modo oscuro del sistema, lo que hacía ilegibles el texto, las casillas y las filas de modelos sobre el fondo oscuro
* `Corrección` Se mantuvo el editor visible sobre el teclado, se eligió el texto del botón Enviar según el contraste con el color del tema y se unificaron los controles de búsqueda anterior, siguiente y cerrar
* `Corrección` Se corrigió el bloqueo al cerrar una session desde un callback listener de generación, donde la espera de inactividad se esperaba a sí misma indefinidamente; el cierre sigue esperando los callbacks ya activos en otros hilos
* `Corrección` Se corrigió el rechazo del almacenamiento privado de perfiles en línea y credenciales cuando Android canonicaliza la raíz confiable `/data/user/0` como `/data/data`; se siguen rechazando los enlaces de hijos directos y las fugas de contención
* `Mejora` Descripción del plugin, instrucciones y README en 10 idiomas actualizados conforme a la formalización de la ruta de plugin local `ai.*`
* `Mejora` ROADMAP reescrito como hoja de ruta de funciones con elementos verificables individualmente
* `Mejora` Se normalizó la puntuación ASCII en la aplicación y en el texto localizado generado, con una prueba de regresión para el texto empaquetado y generado
* `Mejora` Se introdujo una capa compartida `AiBackend`/`AiTarget`/`AiBackendSession` para que el chat del iniciador y el proveedor Binder usen la misma ruta `LiteRtLocalBackend` de catálogo, capacidades, creación de sesiones, streaming y cancelación
* `Mejora` Se combinaron los targets locales `local:*` y en línea `profile:*` en un catálogo y despachador únicos a nivel de Application; la lista de modelos V1 sigue siendo solo local y los targets en línea se marcan unavailable hasta implementar su transporte HTTPS

# v1.0.0

###### 2026/08/08

* `Función` Provider en el dispositivo para el protocolo AI Provider V1 con ID y motor `three-stone-ai`, provider ID `autojs6.three-stone-ai` y variante `default`
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

* [CHANGELOG-es.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/assets/doc/CHANGELOG-es.md)

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
ai-provider-api.aar
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
