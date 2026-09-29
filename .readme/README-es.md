<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source srcset="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" media="(prefers-color-scheme: dark)" />
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-three-stone-ai-ic-launcher" border="0" width="128" />
    </picture>
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

3-Stone AI es el plugin oficial de generación de texto con IA para AutoJs6. Ejecuta los modelos LiteRT-LM importados por el usuario en un backend CPU seleccionado explícitamente o en una GPU compatible, y también llama a perfiles OpenAI, Anthropic, Gemini, DeepSeek, OpenRouter y OpenAI Compatible configurados por el usuario. AI Provider V2 expone directamente esos destinos locales y en línea en un catálogo unificado; cada solicitud selecciona un destino explícito y los destinos locales funcionan sin acceso a la red ni subida de datos. Los ajustes del plugin gestionan credenciales cifradas, el destino en línea predeterminado, las redes medidas y pruebas de conexión explícitas.

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
- Mantener los fallos de generación local y en la nube dentro del destino seleccionado: el chat del iniciador añade una causa acotada sin datos sensibles, indica que no hubo fallback entre límites y ofrece un cambio manual explícito de destino sin descartar la salida parcial.
- Llamadas nativas a herramientas para destinos en línea compatibles con OpenAI, Anthropic Messages y Gemini GenerateContent, con argumentos en streaming, llamadas paralelas y continuación tras resultados.
- Entrada JPEG/PNG y resultados de herramientas con imágenes mediante AI Provider 2.1 negociado, con ajustes por modelo.
- Mantener al día los modelos predefinidos en línea con el catálogo público del proyecto en GitHub. La actualización automática está activada por defecto y comprueba una vez cada 24 horas al abrir los ajustes de IA en línea; se puede desactivar o actualizar manualmente. Respeta el ajuste de redes medidas, conserva la lista almacenada o integrada sin conexión o ante errores y no cambia los perfiles guardados ni los ID personalizados.
- Agrupar los modelos predefinidos por proveedor y ampliar las opciones de OpenRouter con Qwen, Kimi, GLM, Grok, Meta y MiniMax, conservando los ID exactos.

******

### Formatos de modelo y datos

******

Formatos de modelos y entradas admitidos:

```text
model package: .litertlm
input: text/plain history + application/json schema; negotiated 2.1: image/jpeg and image/png descriptors
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
protocol: V2 (2.0 / 2.1)
required host build: 5276
```

AI Provider V2 expone un único catálogo paginado de destinos `local:*` y `profile:*`. Cada destino declara por separado provider, modelo, localidad, configuración y disponibilidad, capacidades, límites, controles y orígenes HTTPS. Un catálogo solo local declara ON_DEVICE/NONE; la presencia de perfiles en línea declara HYBRID/PLUGIN_MANAGED y la unión exacta de sus orígenes HTTPS. El perfil backend local sigue siendo un control opcional del destino y nunca hay retorno silencioso desde perfiles o destinos no disponibles.

Se requiere la build 5276 o posterior del host y Android 7.0 o posterior. Las compilaciones generan APK armeabi-v7a, arm64-v8a, x86, x86_64, universal. En x86 y armeabi-v7a, usa el APK individual correspondiente para la aplicación, la IA en línea y la integración con el host. La inferencia local LiteRT-LM requiere arm64-v8a o x86_64 y su biblioteca nativa incluida en el APK. El APK universal solo contiene bibliotecas de 64 bits y no se puede instalar en dispositivos exclusivamente de 32 bits.

******

### Estado de integración con el host

******

> En AutoJs6 (compilación 5276 y posteriores), `ai.catalog()` devuelve cada modelo local importado y profile en línea configurado como un único catálogo de destinos, con ID exacto, proveedor, modelo, localidad, configuración, disponibilidad, capacidades, controles, límites, origen y profiles backend locales. Pase un `target` exacto a `ai.ask`, `ai.chat`, `ai.stream` o `ai.session`; solo `target` selecciona el plugin oficial 3-Stone AI, mientras que `plugin: true` usa su destino predeterminado declarado. Los destinos locales pueden ofrecer `cpu`, `gpu` y `npu` no disponible; los destinos en línea no tienen profile de ejecución local. Un backend o destino no disponible falla sin fallback y las rutas local/en línea nunca cambian automáticamente. Las respuestas completas y de streaming exponen target, plugin, profile, razonamiento, motivo de finalización, usage completo y duración medida por el proveedor. Los errores estables distinguen proveedor ausente o deshabilitado, destino desconocido, no configurado, no disponible o con capacidad incompatible y backend no disponible. `responseSchema` activa la salida estructurada; `structuredJson: true` sin schema usa una raíz object predeterminada y las sesiones persistentes fijan el mismo target, schema y backend opcional para todos los turnos.

******

### Seguridad y privacidad

******

El plugin solicita `INTERNET` para descargas de modelos recomendados iniciadas por el usuario, solicitudes a un destino en línea configurado por el usuario y actualizaciones del catálogo público de modelos predefinidos desde GitHub en los ajustes de IA en línea. Estas actualizaciones no requieren una clave API ni realizan inferencia, y no se inician al activar el plugin ni durante la generación local. Las credenciales permanecen en el almacenamiento privado cifrado del plugin y nunca cruzan Binder ni el catálogo de destinos. No solicita ningún permiso general de almacenamiento. Las descargas de archivos de modelos usan revisiones HTTPS inmutables, tamaño y SHA-256 fijados, y solo escriben en la ubicación SAF elegida; deben superar cabecera LiteRT-LM, tamaño, resumen, flush y fsync. Los servicios también verifican el paquete AutoJs6, el UID y las firmas.

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
- En Ajustes > IA en línea, edite un perfil y seleccione sus modelos con entrada de imágenes. Los perfiles existentes quedan desactivados por defecto. Las imágenes solo se envían a ese servicio. LiteRT y ai.session persistente siguen siendo de texto; las capturas de Agent requieren Android 11+, versiones compatibles de AutoJs6 y AI Agent, el grupo observe y la entrada de imágenes activada para el modelo exacto seleccionado.
- AiGoCode gpt-5.6-sol pasó las pruebas reales de imagen inicial e imagen en resultados de herramientas con Provider 1.2.0 / build 218. Estas pruebas con imágenes sintéticas no demuestran compatibilidad con otros destinos ni una tarea visual completa de Agent.
- Los errores en línea solo exponen categorías fijas `ONLINE_*` en el campo opcional `providerCode`. Las excepciones desconocidas no tienen categoría; se excluyen URL, credenciales, respuestas del servicio y texto original de excepciones. El código de error y la política de reintentos no cambian.

******

### Capacidades no declaradas

******

- No se declara la salida reasoning. Los destinos locales LiteRT-LM siguen sin declarar tools.
- Las herramientas nativas admiten hasta 16 rondas y 32 llamadas pendientes. Los resultados deben coincidir exactamente con el lote pendiente; siguen vigentes los límites de contexto/salida, la cancelación y el plazo original. No se combinan con turnos persistentes ai.session; el historial inicial no acepta el rol tool.
- No se admite descargar archivos de modelos desde URL arbitrarias. Los modelos predefinidos se pueden actualizar desde el catálogo público del proyecto, pero no crean perfiles ni consultan el acceso de la cuenta. El catálogo de destinos solo expone modelos locales importados y perfiles en línea configurados explícitamente; las descargas locales se limitan a las recomendaciones integradas y fijadas.
- No se declara inferencia NPU: el perfil es visible como `unavailable` con `npu-runtime-not-packaged`. GPU solo se declara si `libOpenCL.so` puede cargarse y la extensión `.litertlm` aún no garantiza que el modelo se inicialice.

******

### Hoja de ruta

******

La hoja de ruta se organiza en funciones entregables para el usuario, cada una verificable por separado

- [Ver ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/ROADMAP.md)

******

### Historial de versiones

******

# v1.2.1

###### 2026/09/29

* `Aviso` Los cambios de compatibilidad de 32 bits son una versión candidata sin publicar. Consulta docs/dev/32-bit-compatibility-2026-09-29.md para las comprobaciones ejecutadas y los dispositivos pendientes.
* `Corrección` Cuando el host entrega una imagen de resultado de herramienta como descriptor de un archivo regular de su caché privada, reabrirlo mediante /proc/self/fd fallaba con EACCES porque el Provider no puede recorrer el directorio del host, y toda la continuación de herramientas se abortaba con PROTOCOL_VIOLATION; los archivos regulares ahora se leen mediante un descriptor duplicado (la lectura de un archivo regular nunca bloquea), mientras que las tuberías conservan la reapertura privada no bloqueante. En un dispositivo real (Sony XQ-DQ72, AutoJs6 5298, 3-Stove Agent 1.3.0), cada imagen de screen_capture enviada a los modelos Codex / Gemini como resultado de herramienta nativa sufría este fallo
* `Corrección` Tras cada lote aceptado de resultados de herramientas nativas, el tiempo límite de generación vuelve a empezar: antes todo el turno de herramientas, incluido el tiempo de espera de los resultados, compartía el tiempo límite de la primera solicitud, de modo que las tareas de varias rondas más largas siempre terminaban con TIMEOUT; ahora solo la espera sin enviar resultados sigue caducando con el tiempo límite original. Coherente con los cambios correspondientes del host AutoJs6 y de 3-Stove Agent
* `Corrección` Añadir APK x86 y armeabi-v7a para IA en línea e integración con el host; comprobar la ABI del proceso y las bibliotecas instaladas antes de habilitar la inferencia local, con un aviso en el gestor de modelos
* `Mejora` Unificar los iconos del lanzador de la serie Three con dibujos claros sobre un fondo oscuro fijo, mantener transparentes los del centro de complementos y de la aplicación según su tema y evitar fondos superpuestos en algunos dispositivos

# v1.2.0

###### 2026/09/26

* `Aviso` Versión de desarrollo no publicada. Las herramientas en línea usan AI Provider V2; su integración con Agent requiere el intermediario nativo del host build 5297+ y una versión compatible de Agent. El plugin devuelve llamadas al host y no ejecuta acciones del dispositivo por sí mismo.
* `Aviso` En Ajustes > IA en línea, edite un perfil y seleccione sus modelos con entrada de imágenes. Los perfiles existentes quedan desactivados por defecto. Las imágenes solo se envían a ese servicio. LiteRT y ai.session persistente siguen siendo de texto; las capturas de Agent requieren Android 11+, versiones compatibles de AutoJs6 y AI Agent, el grupo observe y la entrada de imágenes activada para el modelo exacto seleccionado.
* `Aviso` AiGoCode gpt-5.6-sol pasó las pruebas reales de imagen inicial e imagen en resultados de herramientas con Provider 1.2.0 / build 218. Estas pruebas con imágenes sintéticas no demuestran compatibilidad con otros destinos ni una tarea visual completa de Agent.
* `Función` Llamadas nativas a herramientas para destinos en línea compatibles con OpenAI, Anthropic Messages y Gemini GenerateContent, con argumentos en streaming, llamadas paralelas y continuación tras resultados
* `Función` Entrada JPEG/PNG y resultados de herramientas con imágenes mediante AI Provider 2.1 negociado, con ajustes por modelo
* `Función` Añadir actualización automática y manual de modelos predefinidos en línea con caché local y lista disponible sin conexión, conservando los perfiles guardados y los ID personalizados
* `Función` Agrupar los modelos predefinidos por proveedor y ampliar las opciones de OpenRouter con Qwen, Kimi, GLM, Grok, Meta y MiniMax, conservando los ID exactos
* `Corrección` Las lecturas de descriptores liberan hilos al cancelar o agotar el plazo y conservan los errores del productor de tuberías fiables
* `Corrección` Conservar categorías fijas de errores en línea en las devoluciones AI Provider, sin exponer solicitudes o respuestas ni añadir reintentos automáticos
* `Corrección` Corregir el fallo No APK found al ejecutar con F10 en IntelliJ IDEA usando el directorio APK real de AGP para cada variante y conservando las comprobaciones de alineación de 16 KB
* `Mejora` Actualizar los modelos en línea predefinidos según los catálogos oficiales, incluidos Claude Fable 5.1 y otros modelos actuales, y eliminar los identificadores retirados conservando los perfiles existentes y los modelos personalizados

# v1.1.4

###### 2026/09/19

* `Corrección` Advertencias de lectura de SDK XML v4 con AGP 9.1 y comprobaciones de alineación nativa de APK activadas por error al ensamblar pruebas unitarias JVM, mediante los plugins de compilación compartidos 1.8.3
* `Mejora` Tras compileSdk, targetSdk sube a 37 (Android 17); el comportamiento del plugin no depende del nuevo objetivo

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


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/docs/16kb.md)
