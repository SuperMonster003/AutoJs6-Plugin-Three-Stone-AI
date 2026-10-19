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
- Unificar los ajustes con grupos planos, filas coherentes y diálogos redondeados centrados. El idioma, el modo nocturno, el color y el icono solo cambian al confirmar; Cancelar conserva los valores guardados. El color sigue AutoJs6 por defecto, con una paleta común, entrada HEX/RGB y vista previa local. Los fondos neutros se mantienen estables y los controles siguen el tema. El icono usa el modo adaptativo automático por defecto, conservando las elecciones explícitas al actualizar.
- Un micrófono dentro del cuadro de entrada: tócalo para dictar, y el texto reconocido se inserta en el cursor para que lo revises antes de enviarlo; mantenlo pulsado para una conversación por voz que envía lo que dices y vuelve a escuchar después de leer la respuesta.
- Lectura de respuestas en voz alta: mantén pulsada una respuesta y elige "Leer en voz alta", o activa "Leer las respuestas en voz alta" en Ajustes > Voz para escuchar las respuestas nuevas mientras se generan. Las marcas de títulos y listas no se leen, y un bloque de código o una tabla se anuncia con una frase.
- Responde al `VOICE_COMMAND` del asistente digital de 3-Macaw Speech: mantén pulsado el botón de encendido, habla y toca "3-Stone AI"; la pregunta se envía en una conversación nueva y la respuesta se lee en voz alta.

******

### Voz

******

El dictado y la lectura usan las interfaces del sistema Android (`RecognizerIntent` y `TextToSpeech`). 3-Stone AI nunca graba audio por sí mismo ni pide el permiso del micrófono. Con 3-Macaw Speech instalado, "Preferir 3-Macaw Speech" en Ajustes > Voz está activado de forma predeterminada: el dictado abre su ventana de reconocimiento sin conexión y la lectura usa sus voces sin conexión. Sin él, o con la opción desactivada, se usan la aplicación de reconocimiento del teléfono y el motor de texto a voz predeterminado. En 1.4.0 cada turno de una conversación por voz abre la pantalla del reconocedor; mientras se lee una respuesta, toca la barra de estado sobre el cuadro de entrada para interrumpir y hablar enseguida, o usa su botón para salir.

- **Varias aplicaciones de reconocimiento de voz piden elegir cada vez?** Deja activado "Preferir 3-Macaw Speech" para usar 3-Macaw Speech directamente. Si lo desactivas, decide el sistema, y puedes fijar una aplicación predeterminada en su selector.
- **Pausas entre frases en un teléfono lento?** Algunas voces (como MeloTTS y Kokoro) sintetizan más despacio de lo que hablan en teléfonos lentos. Elige AISHELL-3, Piper o Matcha como voz predeterminada en 3-Macaw Speech.
- **El micrófono indica que no hay modelo de reconocimiento?** La ventana de 3-Macaw Speech te guía para descargar uno; mientras tanto, 3-Stone AI no cambia por su cuenta a otro reconocedor. Desactiva "Preferir 3-Macaw Speech" para usar otra aplicación entretanto.
- **Cómo pregunto a través del asistente digital de 3-Macaw Speech?** Configura 3-Macaw Speech como asistente digital predeterminado, mantén pulsado el botón de encendido (o de inicio), habla y toca "3-Stone AI" en la tarjeta del resultado. 3-Stone AI abre una conversación nueva, envía la pregunta enseguida y lee la respuesta; desbloquea antes el teléfono si está bloqueado. Si han pasado menos de 10 segundos desde la pregunta anterior de este tipo, el texto solo se pone en el cuadro de entrada y no se envía.
- **A dónde va el texto reconocido?** Al modelo seleccionado, igual que el texto escrito; un modelo en línea lo envía a su proveedor. 3-Macaw Speech funciona totalmente sin conexión; otras aplicaciones de reconocimiento o de voz pueden usar la red.

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

El plugin solicita `INTERNET` para descargas de modelos recomendados iniciadas por el usuario, solicitudes a un destino en línea configurado por el usuario y actualizaciones del catálogo público de modelos predefinidos desde GitHub en los ajustes de IA en línea. Estas actualizaciones no requieren una clave API ni realizan inferencia, y no se inician al activar el plugin ni durante la generación local. Las credenciales permanecen en el almacenamiento privado cifrado del plugin y nunca cruzan Binder ni el catálogo de destinos. No solicita ningún permiso general de almacenamiento. Las descargas de archivos de modelos usan revisiones HTTPS inmutables, tamaño y SHA-256 fijados, y solo escriben en la ubicación SAF elegida; deben superar cabecera LiteRT-LM, tamaño, resumen, flush y fsync. Los servicios también verifican el paquete AutoJs6, el UID y las firmas. Las funciones de voz no piden el permiso del micrófono: graba la aplicación de reconocimiento. El texto reconocido y el leído no se registran, y los mensajes dictados se guardan igual que los escritos.

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

# v1.4.1

###### 2026/10/20

* `Corrección` Los APK de 1.4.0 solo declaraban las arquitecturas de procesador de 64 bits (arm64-v8a, x86_64), por lo que en teléfonos de 32 bits (armeabi-v7a, x86) el centro de plugins de AutoJs6 podía considerar el plugin incompatible; ahora se declaran correctamente las cuatro arquitecturas

# v1.4.0

###### 2026/10/20

* `Aviso` 3-Stone AI no pide el permiso del micrófono: graba la aplicación de reconocimiento. 3-Macaw Speech funciona totalmente sin conexión, otras aplicaciones de reconocimiento o de voz pueden usar la red, y el texto reconocido se envía al modelo seleccionado igual que el texto escrito
* `Función` Nuevo botón de micrófono dentro del cuadro de entrada: tócalo y habla, y el texto reconocido se inserta en el cursor para revisarlo antes de enviar. Con 3-Macaw Speech el reconocimiento es sin conexión; si no, se usa la aplicación de reconocimiento de voz del teléfono
* `Función` Las respuestas se pueden leer en voz alta: mantén pulsada una respuesta y elige "Leer en voz alta", o activa "Leer las respuestas en voz alta" en Ajustes > Voz para escuchar las respuestas nuevas mientras se generan. Las marcas de títulos y listas no se leen, y los bloques de código y las tablas se anuncian con una frase. Se prefieren las voces sin conexión de 3-Macaw Speech si está instalado
* `Función` Mantén pulsado el micrófono para una conversación por voz: lo que dices se envía enseguida y la aplicación vuelve a escuchar después de leer la respuesta. Una barra de estado sobre el cuadro de entrada muestra qué está pasando y permite salir en cualquier momento; mientras se lee una respuesta, tócala para interrumpir y hablar enseguida
* `Función` Pregunta a través del asistente digital de 3-Macaw Speech: mantén pulsado el botón de encendido, habla y toca "3-Stone AI" para recibir la respuesta en una conversación nueva, leída en voz alta. Otra pregunta de este tipo en menos de 10 segundos solo se pone en el cuadro de entrada, para evitar abusos
* `Función` Nuevo grupo "Voz" en Ajustes: lectura automática, "Preferir 3-Macaw Speech" y qué aplicación dicta y lee en este momento

# v1.3.0

###### 2026/10/15

* `Función` Unificar los ajustes con grupos planos, filas coherentes y diálogos redondeados centrados. El idioma, el modo nocturno, el color y el icono solo cambian al confirmar; Cancelar conserva los valores guardados. El color sigue AutoJs6 por defecto, con una paleta común, entrada HEX/RGB y vista previa local. Los fondos neutros se mantienen estables y los controles siguen el tema. El icono usa el modo adaptativo automático por defecto, conservando las elecciones explícitas al actualizar.
* `Corrección` A través de una pasarela compatible con OpenAI, los modelos Claude o Gemini transmiten una cadena vacía en lugar de "{}" como arguments de una herramienta sin parámetros; la sesión lo rechazaba como respuesta inválida y terminaba con ONLINE_INVALID_RESPONSE (así fallaron la primera ronda de herramientas de Claude Code Ex y una ronda de Gemini Ex de 3-Stove Agent en la pasarela AIGoCode). Ahora los arguments en blanco se tratan como un objeto vacío y el mensaje del asistente reenviado al modelo lleva la forma normalizada
* `Corrección` Las rondas de continuación de herramientas nativas restaban del límite de tokens de salida de la primera solicitud lo consumido en rondas anteriores, por lo que los turnos largos terminaban con ONLINE_INVALID_RESPONSE al agotarse el límite tras unas diez rondas (el caso de la calculadora de 3-Stove Agent fallaba entre la llamada 9 y la 14 tanto en Claude Code Ex como en Gemini Ex). Ahora cada continuación conserva el límite completo y el presupuesto de tarea del llamador acota el total, en línea con la decisión del 2026-09-29 de reiniciar el tiempo de espera de continuación
* `Corrección` El protocolo Gemini GenerateContent enviaba las declaraciones de herramientas y el responseSchema con su JSON Schema intacto, y la API oficial responde HTTP 400 ("Unknown name additionalProperties") ante palabras clave que no conoce, por lo que cada tarea de herramientas nativas de 3-Stove Agent fallaba en su primera llamada con ONLINE_REQUEST_REJECTED. Ahora solo se envía el subconjunto que Gemini admite (type, enum, properties, required, items, anyOf, minimum / maximum, minLength / maxLength, default y similares); los llamadores siguen validando argumentos y respuestas con el esquema completo
* `Corrección` Completar las traducciones de los ajustes de muestreo y proveedores de modelos en línea
* `Corrección` En la interfaz en árabe, 3-Stone AI, 4-bit y las revisiones de modelo que empiezan por un dígito ya no aparecen invertidas ni partidas (como Stone AI-3 y bit-4)
* `Mejora` Mantener el marco redondeado del icono de Acerca de con el interior transparente sobre el fondo de la página. Mostrar las opciones del lanzador desde arriba con notas más pequeñas.
* `Mejora` Tamaño visual uniforme de los iconos del lanzador y del Centro de complementos, con fondos transparentes y diseños en blanco, negro o grises neutros
* `Mejora` Los iconos del centro de plugins usan los tamaños, posiciones, imágenes claras y oscuras y fondos circulares ajustados en Icon Studio, conservando fuentes y parámetros reproducibles
* `Mejora` Los iconos de información de la aplicación de Android comparten las imágenes y los fondos claros y oscuros de Icon Studio, conservando las imágenes transparentes del centro de plugins y las opciones del lanzador

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
