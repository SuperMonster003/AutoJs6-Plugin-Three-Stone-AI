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

3-Stone AI es el plugin oficial de generación de texto con IA para AutoJs6. Ejecuta los modelos LiteRT-LM importados por el usuario en un backend CPU seleccionado explícitamente o en una GPU compatible, y también llama a servicios OpenAI, Anthropic, Gemini, DeepSeek, OpenRouter y OpenAI Compatible configurados por el usuario. AI Provider V2 expone directamente esos destinos locales y en línea en un catálogo unificado; cada solicitud selecciona un destino explícito y los destinos locales funcionan sin acceso a la red ni subida de datos. Los ajustes del plugin gestionan credenciales cifradas, el destino predeterminado (en línea o local), las redes medidas y pruebas de conexión explícitas.

******

### Funciones

******

- Importar un paquete de modelo `.litertlm` con el selector del sistema Android y guardar una copia verificada en el almacenamiento privado de la aplicación.
- Descargar en un solo paso, desde el catálogo integrado, un modelo LiteRT Community recomendado, fijado y sin autenticación: se escribe en el almacenamiento privado de la aplicación y se importa automáticamente tras verificar exactamente el tamaño y el SHA-256. Un modelo en descarga aparece como una tarjeta en la sección "Modelos locales", con progreso, pausa, cancelación y reintento en caso de error, y una descarga interrumpida continúa desde la parte ya recibida; la confirmación muestra la memoria mínima del modelo y avisa si el dispositivo tiene menos.
- Comprobar el almacenamiento privado antes de abrir el selector, mostrar el presupuesto de importación actual y la ocupación estimada de la copia privada, y volver a comprobar el archivo seleccionado antes de copiarlo.
- Crear solicitudes de generación local con historial system, user y assistant en texto sin formato.
- Transferir `temperature`, `topK`, `topP` y `maxTokens` desde `ai.ask`, `ai.chat` y `ai.stream` de AutoJs6 hasta LiteRT-LM.
- Restringir la salida de forma nativa con JSON Schema de LiteRT-LM mediante `structuredJson` y `responseSchema` de AutoJs6; los valores completos siguen siendo texto JSON para `JSON.parse`.
- Informar los recuentos exactos de tokens de entrada, salida y totales de LiteRT-LM, junto con la duración de generación del proveedor, mediante `ai.chat().usage` y los eventos usage de streaming.
- Mantener el contexto de varios turnos en una única Conversation nativa de LiteRT-LM mediante `ai.session` de AutoJs6, enviando solo el nuevo prompt de usuario en los turnos posteriores.
- Reutilizar el Engine inicializado según el SHA-256 del modelo para evitar arranques en frío repetidos en solicitudes consecutivas al mismo modelo.
- Inicializar opcionalmente cada modelo importado una vez, conservar su estado Disponible/Incompatible y repetir la comprobación desde el gestor de modelos.
- Entregar chunks de texto en orden con contrapresión por credits y publicar un solo estado terminal completado, fallido o cancelado.
- Enumerar, renombrar, comprobar y eliminar modelos importados en la sección "Modelos locales", con el espacio que ocupa cada modelo en su tarjeta, y recuperar con un toque los archivos de modelos sin referencia.
- Seleccionar explícitamente `cpu`, `gpu` o `npu` desde AutoJs6; CPU es el valor predeterminado, GPU se expone solo tras una prueba de carga OpenCL y NPU se informa como no disponible porque su runtime EAP no está incluido.
- Gestionar servicios en línea integrados y personalizados, credenciales de Android Keystore, el destino predeterminado único (un modelo en línea o local), redes medidas y pruebas de conexión explícitas y limitadas en Ajustes > Modelos y servicios. Los servicios se editan en la página a pantalla completa "Detalles del servicio": cada servicio puede tener varios modelos, que se reordenan arrastrando y entre los que se elige un modelo preferido, y los cambios solo se guardan con la marca de verificación.
- Vincular cada conversación del iniciador a una instantánea de destino local o en línea; al cambiar una conversación con mensajes se recomienda una nueva, y continuar exige confirmación explícita y registra el cambio.
- Guardar una instantánea del destino, proveedor, modelo y ubicación reales en cada respuesta del asistente; la regeneración reutiliza exactamente el destino registrado, falla explícitamente si cambia o deja de estar disponible y nunca recurre silenciosamente al destino actual de la conversación.
- Mantener los fallos de generación local y en la nube dentro del destino seleccionado: el chat del iniciador añade una causa acotada sin datos sensibles, indica que no hubo fallback entre límites y ofrece un cambio manual explícito de destino sin descartar la salida parcial.
- Llamadas nativas a herramientas para destinos en línea compatibles con OpenAI, Anthropic Messages y Gemini GenerateContent, con argumentos en streaming, llamadas paralelas y continuación tras resultados.
- Entrada JPEG/PNG y resultados de herramientas con imágenes mediante AI Provider 2.1 negociado, con ajustes por modelo.
- Mantener al día los modelos predefinidos en línea con el catálogo público del proyecto en GitHub. La actualización automática está activada por defecto y comprueba una vez cada 24 horas al abrir la página "Modelos y servicios"; el menú de la sección "Servicios en línea" permite desactivarla o actualizar manualmente. Respeta el ajuste de redes medidas, conserva la lista almacenada o integrada sin conexión o ante errores y no cambia los servicios guardados ni los ID personalizados.
- Agrupar los modelos predefinidos por proveedor y ampliar las opciones de OpenRouter con Qwen, Kimi, GLM, Grok, Meta y MiniMax, conservando los ID exactos.
- Unificar los ajustes con grupos planos, filas coherentes y diálogos redondeados centrados. El idioma, el modo nocturno, el color y el icono solo cambian al confirmar; Cancelar conserva los valores guardados. El color sigue AutoJs6 por defecto, con una paleta común, entrada HEX/RGB y vista previa local. Los fondos neutros se mantienen estables y los controles siguen el tema. El icono usa el modo adaptativo automático por defecto, conservando las elecciones explícitas al actualizar.
- La generación de respuestas ya no depende de la pantalla del chat: sigue al girar la pantalla o al cambiar el modo oscuro, el idioma o el tamaño de letra de los mensajes, y una respuesta que termina mientras estás fuera del chat se muestra completa al volver (una respuesta aún se pierde si el sistema cierra el proceso de la aplicación). Pulsar "Detener", crear o cambiar de conversación, cambiar de modelo o eliminar mensajes corta la solicitud en línea en menos de 1 segundo, y una respuesta detenida ya no suma uso.
- Los servicios en línea pueden fijar un esfuerzo predeterminado (Predeterminado, Bajo, Medio, Alto, Muy alto o Máximo; con "Predeterminado" decide el modelo), y los servicios de Anthropic pueden activar el modo rápido de forma predeterminada (respuestas más rápidas a un precio mayor); ambos se pueden cambiar solo para la conversación actual, y el cambio se guarda con ella. Si un modelo no admite el nivel elegido, se usa el nivel inferior más cercano. Los scripts que llaman a la IA a través de la interfaz del plugin no usan estos dos ajustes.
- Cambiar de modelo abre un panel desde abajo: los favoritos están en el grupo "Favoritos" de arriba y el resto se agrupa por servicio, con cada modelo una sola vez; los resultados de búsqueda no se agrupan; cada fila muestra debajo su servicio (el tamaño del archivo en los modelos locales) y etiquetas de entrada de imágenes, razonamiento y modo rápido; el panel también cambia el esfuerzo y el modo rápido de la conversación actual, y "Administrar modelos y servicios" abajo abre los ajustes de modelos. La barra superior del chat muestra el esfuerzo y el modo rápido de la conversación actual.
- Muestra cómo piensa el modelo (Claude 5 y posteriores, Gemini 2.5 y posteriores, y los servicios compatibles con OpenAI que devuelven su razonamiento, como DeepSeek): mientras se genera la respuesta, el razonamiento aparece en tiempo real en una tarjeta encima de ella y luego se contrae en "Pensó durante N s"; al tocarla se abre o se cierra. Las opciones "Mostrar el razonamiento durante la generación" y "Contraer el razonamiento al terminar" están activadas de forma predeterminada. El razonamiento se guarda con la conversación, pero no se lee en voz alta, no aparece en la búsqueda ni se copia, y los scripts no lo reciben.
- El historial de conversaciones usa el formato v6, que también guarda el esfuerzo y el modo rápido de cada conversación y el razonamiento y su duración de cada respuesta; las conversaciones guardadas por 1.5.0 y versiones anteriores se siguen abriendo.
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

Las aplicaciones con firma independiente solicitan acceso a IA en el complemento, ligado a su identidad e instalación; revocarlo en los ajustes detiene las solicitudes activas. Este permiso cubre modelos locales y cuentas en línea guardadas. Las llamadas en línea pueden consumir cuota de pago. Use ai.hasAccess() y ai.requestAccess() en Node.js, Bun o Workers.

******

### Seguridad y privacidad

******

El plugin solicita `INTERNET` para descargas de modelos recomendados iniciadas por el usuario, solicitudes a un destino en línea configurado por el usuario y actualizaciones del catálogo público de modelos predefinidos desde GitHub en la página "Modelos y servicios". Estas actualizaciones no requieren una clave API ni realizan inferencia, y no se inician al activar el plugin ni durante la generación local. Las credenciales permanecen en el almacenamiento privado cifrado del plugin y nunca cruzan Binder ni el catálogo de destinos. No solicita ningún permiso general de almacenamiento. Las descargas de archivos de modelos usan revisiones HTTPS inmutables, tamaño y SHA-256 fijados, y solo escriben en un archivo parcial del almacenamiento privado `files/models` (se reanudan tras una interrupción); el modelo pasa a la importación para activarse de forma atómica solo después de superar cabecera LiteRT-LM, tamaño, resumen, flush y fsync. El servicio Provider original del anfitrión también verifica el paquete AutoJs6, el UID y las firmas. Las funciones de voz no piden el permiso del micrófono: graba la aplicación de reconocimiento. El texto reconocido y el leído no se registran, y los mensajes dictados se guardan igual que los escritos. El razonamiento que devuelve un modelo solo se guarda en el historial local de conversaciones; no se reenvía al servicio en línea en los turnos siguientes, y los scripts no lo reciben a través de la interfaz del plugin.

******

### Límites operativos

******

- La importación de un modelo tiene un límite estricto de 8 GiB y debe dejar al menos 256 MiB libres.
- Solo se conserva una descarga de modelo sin terminar; empezar la de otro modelo la descarta (la confirmación lo indica). La descarga se escribe en `files/models/.download-<sha256>.part`: pausar, perder la red, cerrar la aplicación o que termine el proceso conservan el archivo, y al reanudar o reintentar se vuelve a verificar la parte recibida antes de continuar; la cancelación, un contenido no válido o un resumen que no coincide lo eliminan. En un arranque en frío, el archivo parcial más reciente de un modelo recomendado aún no importado se restaura en pausa y los demás se eliminan.
- Los modelos locales descargados e importados se guardan en los datos privados de la aplicación (`files/models`), y el espacio que ocupan se muestra en las tarjetas de la sección "Modelos locales" y en el panel para cambiar de modelo. Desinstalar el plugin o borrar sus datos elimina también estos modelos; los archivos de modelos descargados antes en otra ubicación se pueden añadir con "Importar archivo de modelo".
- Un coordinador de importación única con alcance de aplicación mantiene el trabajo durante la recreación de Activity. Un pending journal sincronizado con fsync permite la recuperación en arranque frío y limpia los archivos temporales stale `.incoming`, `.current` y `.pending`. La recuperación solo elimina un destination creado por el intento actual y nunca publicado mediante current metadata; se conservan las generaciones publicadas, current e históricas con nombre de hash.
- Para evitar condiciones de carrera entre procesos con el proceso aislado `:provider`, las importaciones no eliminan automáticamente las generaciones anteriores con nombre de hash SHA-256. La sección "Modelos locales" permite eliminar modelos del catálogo y recuperar archivos con nombre de hash que ya no estén referenciados.
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
- En Ajustes > Modelos y servicios, abra los "Detalles del servicio" y active "Entrada de imágenes" desde el menú de fila de cada modelo de la lista que acepte imágenes. Está desactivada por defecto en los servicios existentes. Las imágenes solo se envían a ese servicio. LiteRT y ai.session persistente siguen siendo de texto; las capturas de Agent requieren Android 11+, versiones compatibles de AutoJs6 y AI Agent, el grupo observe y la entrada de imágenes activada para el modelo exacto seleccionado.
- AiGoCode gpt-5.6-sol pasó las pruebas reales de imagen inicial e imagen en resultados de herramientas con Provider 1.2.0 / build 218. Estas pruebas con imágenes sintéticas no demuestran compatibilidad con otros destinos ni una tarea visual completa de Agent.
- Los errores en línea solo exponen categorías fijas `ONLINE_*` en el campo opcional `providerCode`. Las excepciones desconocidas no tienen categoría; se excluyen URL, credenciales, respuestas del servicio y texto original de excepciones. El código de error y la política de reintentos no cambian.

******

### Capacidades no declaradas

******

- La interfaz del plugin no declara la salida reasoning, así que los scripts no reciben el razonamiento (solo se muestra en el chat del lanzador). Los destinos locales LiteRT-LM siguen sin declarar tools.
- Las herramientas nativas admiten hasta 16 rondas y 32 llamadas pendientes. Los resultados deben coincidir exactamente con el lote pendiente; siguen vigentes los límites de contexto/salida, la cancelación y el plazo original. No se combinan con turnos persistentes ai.session; el historial inicial no acepta el rol tool.
- No se admite descargar archivos de modelos desde URL arbitrarias. Los modelos predefinidos se pueden actualizar desde el catálogo público del proyecto, pero no crean servicios ni consultan el acceso de la cuenta. El catálogo de destinos solo expone modelos locales importados y servicios en línea configurados explícitamente; las descargas locales se limitan a las recomendaciones integradas y fijadas.
- No se declara inferencia NPU: el perfil es visible como `unavailable` con `npu-runtime-not-packaged`. GPU solo se declara si `libOpenCL.so` puede cargarse y la extensión `.litertlm` aún no garantiza que el modelo se inicialice.

******

### Hoja de ruta

******

La hoja de ruta se organiza en funciones entregables para el usuario, cada una verificable por separado

- [Ver ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/ROADMAP.md)

******

### Historial de versiones

******

# v1.7.0

###### 2026/11/12

* `Función` Ahora se pueden añadir adjuntos en el chat: toca "+" a la izquierda del cuadro de texto para elegir imágenes o archivos, hasta 20 por mensaje. Se admiten imágenes (hasta 5 MB cada una; las imágenes demasiado grandes se reducen automáticamente), archivos de texto UTF-8 (por ejemplo txt, md, json, csv y archivos de código, hasta 256 KB) y PDF (hasta 32 MB y 100 páginas). Las imágenes requieren que "Entrada de imágenes" esté activado para el modelo actual; por ahora los PDF solo se pueden enviar a servicios cuyo proveedor sea Anthropic o Gemini (con Gemini, los adjuntos de una solicitud no pueden superar 20 MB en total); los modelos locales aún no admiten adjuntos. Los adjuntos que el modelo actual no admite se muestran atenuados; quítalos o cambia de modelo antes de enviar
* `Función` Los adjuntos se guardan con el mensaje en los datos de la aplicación: el mensaje muestra miniaturas de las imágenes o los nombres de los archivos, y al tocar uno se abre en otra aplicación; los adjuntos elegidos pero aún no enviados se conservan al girar la pantalla o si se cierra la aplicación. Al continuar la conversación más adelante, los adjuntos anteriores se vuelven a enviar al modelo en la medida en que lo permita el contexto; de los que no caben, solo se indica al modelo el nombre del archivo. Los adjuntos de una conversación no pueden superar 48 MB en total; al eliminar una conversación se eliminan también sus adjuntos, y "Limpiar archivos sin referencia" en la página "Modelos y servicios" también elimina los adjuntos que ya no se usan
* `Corrección` En conversaciones largas, los mensajes más antiguos se resumen automáticamente para ahorrar contexto, pero algunos servicios en línea y modelos locales envolvían el resumen en marcas de bloque de código Markdown o le añadían texto explicativo, por lo que el resumen fallaba y el modelo olvidaba con más facilidad las partes anteriores de la conversación; ahora se quitan esas marcas y ese texto antes de leer el resumen
* `Mejora` Ajustes reorganizados: la página principal solo contiene los ajustes de apariencia y 5 entradas, "Conversación", "Modelos y servicios", "Voz", "Privacidad y permisos" y "Actualizaciones y acerca de", y cada ajuste aparece en un solo lugar; "Generación" dentro de "Conversación" pasa a llamarse "Longitud de salida y muestreo", su formulario ya no se cierra al deslizar hacia abajo ni al tocar fuera y pregunta antes si hay cambios sin guardar; el historial de versiones se movió a "Actualizaciones y acerca de". El menú y el panel lateral de la página de chat ya no tienen una entrada "Modelos" aparte, todo se abre desde "Ajustes"; al abrir los ajustes de este plugin desde AutoJs6 se va directamente a "Modelos y servicios", y Atrás vuelve a la página principal de ajustes
* `Mejora` Las antiguas páginas "Modelos" y "Servicios en línea" se unieron en una sola página "Modelos y servicios": arriba están el destino predeterminado y "Permitir redes móviles y de uso medido", y debajo dos secciones, "Servicios en línea" y "Modelos locales". Cada servicio y cada modelo local se muestra con el mismo tipo de tarjeta y una etiqueta "Predeterminado", "Listo", "Falta la API key" o "No disponible"; acciones como establecer como predeterminado, probar, renombrar, clonar, borrar la clave API y eliminar están en el menú a la derecha de cada tarjeta, e importar / exportar servicios, actualizar los modelos predefinidos y limpiar archivos sin referencia están en el menú a la derecha del título de cada sección. Toque un modelo local para ver sus detalles y copiar su ID. Los perfiles en línea pasan a llamarse "servicios" en todas partes
* `Mejora` El antiguo "Destino en línea predeterminado" y el modelo local seleccionado se unieron en un solo "Destino predeterminado", que puede ser cualquier modelo en línea o local y lo usan las conversaciones nuevas y los scripts que no indican un modelo; tras la actualización se conserva su elección predeterminada anterior. Si el destino predeterminado no está disponible por un tiempo (por ejemplo, tras borrar su clave API), se usa automáticamente otro modelo disponible hasta que vuelva a estarlo; si se elimina su servicio o su modelo, también se usa otro modelo disponible. El "Modelo predeterminado" de un servicio pasa a llamarse "Modelo preferido"
* `Mejora` Los servicios ahora se añaden y editan en una página de pantalla completa "Detalles del servicio" en lugar del antiguo formulario que aparecía desde abajo: arriba están los datos de conexión (nombre del servicio, proveedor, Base URL, clave API, etc.) y debajo la "Lista de modelos"; mantenga pulsada una fila o arrastre su controlador para cambiar el orden, o use el menú de cada fila para establecerlo como modelo preferido, marcarlo para "Entrada de imágenes", subirlo, bajarlo o quitarlo, y añada varios modelos a la vez. Por eso desaparecen los antiguos selectores "ID del modelo", "Modelo predeterminado" y "Modelos de entrada de imágenes". Toque la marca de verificación arriba a la derecha para guardar y después pruebe la conexión en la misma página; al volver con cambios sin guardar se pregunta antes, y lo que haya escrito se conserva al girar la pantalla o si el sistema recupera la aplicación en segundo plano. Tras cambiar el orden, el destino predeterminado y los favoritos siguen apuntando a los mismos modelos
* `Mejora` Descargar un modelo local recomendado ahora se hace en un solo paso: tras confirmar, el modelo se descarga en el almacenamiento privado de la aplicación y se importa automáticamente una vez verificado, sin tener que "Elegir ubicación" antes ni "Importar modelo descargado" después. Durante la descarga, la página "Modelos y servicios" muestra una tarjeta de descarga para pausar, reanudar o cancelar, y reintentar tras un error; si se corta la red o se cierra la aplicación, la parte descargada se conserva y al reanudar o reintentar se continúa desde donde se detuvo. La confirmación de descarga muestra la memoria mínima del modelo y avisa cuando el dispositivo tiene menos, aunque sigue permitiendo la descarga. Los archivos de modelo descargados antes en otra ubicación se pueden seguir añadiendo con "Importar archivo de modelo". Los modelos descargados se guardan en los datos de la aplicación y se eliminan al borrar esos datos o desinstalar la aplicación
* `Mejora` En el panel para cambiar de modelo, cada modelo aparece ahora una sola vez: los favoritos se muestran solo en el grupo "Favoritos" de arriba, ya no también bajo su servicio, y vuelven a su sitio al quitarlos de favoritos; los resultados de búsqueda ya no se agrupan y se muestran en una sola lista en el orden habitual. Cada fila muestra debajo su servicio, y los modelos locales muestran el tamaño del archivo; el grupo "En este dispositivo" pasa a llamarse "Modelos locales"

# v1.6.0

###### 2026/11/12

* `Función` Los perfiles en línea pueden fijar un esfuerzo predeterminado (Bajo, Medio, Alto, Muy alto o Máximo; con "Predeterminado" decide el modelo), y los perfiles de Anthropic pueden usar el modo rápido de forma predeterminada (respuestas más rápidas a un precio mayor); ambos también se pueden cambiar solo para la conversación actual, el cambio se guarda con la conversación y las conversaciones nuevas empiezan con los valores del perfil. Si un modelo no admite el nivel elegido, se usa el nivel inferior más cercano
* `Función` Cambiar de modelo ahora abre un panel desde abajo: búsqueda, modelos agrupados por perfil, favoritos fijados arriba y etiquetas que indican qué modelos admiten entrada de imágenes, razonamiento y modo rápido; el panel también cambia el esfuerzo y el modo rápido de la conversación actual, y "Administrar modelos y servicios" abajo abre los ajustes de modelos. La barra superior del chat muestra el esfuerzo y el modo rápido de la conversación actual
* `Función` El chat ahora muestra cómo piensa el modelo (Claude 5 y posteriores, Gemini 2.5 y posteriores, y los servicios compatibles con OpenAI que devuelven su razonamiento, como DeepSeek): mientras se genera la respuesta, el razonamiento aparece en tiempo real en una tarjeta encima de ella; al terminar, la tarjeta se contrae en "Pensó durante N s", y al tocarla se abre o se cierra. El razonamiento se guarda con la conversación, pero no se lee en voz alta ni aparece en la búsqueda de mensajes. Las opciones "Mostrar el razonamiento durante la generación" y "Contraer el razonamiento al terminar" están activadas de forma predeterminada y se pueden desactivar. Los scripts que llaman a la IA a través del plugin no reciben el razonamiento
* `Corrección` Una respuesta en generación se detenía al girar la pantalla o al cambiar el modo oscuro, el idioma o el tamaño de letra de los mensajes; ahora la respuesta sigue generándose, y una respuesta que termina mientras estás fuera del chat se muestra completa al volver
* `Corrección` Tras pulsar "Detener", la solicitud en línea seguía en segundo plano hasta completar la respuesta (y seguía contando en el uso); ahora pulsar Detener corta la solicitud en menos de 1 segundo, y lo mismo ocurre al crear una conversación, cambiar de conversación, cambiar de modelo o eliminar mensajes
* `Corrección` Los modelos Claude más recientes rechazaban las solicitudes de perfiles de Anthropic que fijaban temperatura, Top K o Top P; ahora los perfiles de Anthropic ya no envían estos tres ajustes, y la página de ajustes lo indica
* `Corrección` Tras cambiar el modelo predeterminado de un perfil, una conversación nueva ya abierta y sin mensajes seguía usando el modelo predeterminado anterior; ahora pasa al nuevo modelo predeterminado (salvo que hayas elegido otro modelo en esa conversación)
* `Corrección` Al añadir o editar un perfil en línea, deslizar hacia abajo sobre el formulario lo cerraba con facilidad y se perdía todo lo escrito; ahora el formulario solo se cierra con el botón de cerrar de arriba a la derecha, "Cancelar" o Atrás, pregunta antes de descartar los cambios sin guardar y conserva lo escrito al girar la pantalla
* `Corrección` Fallo de actualización automática del catálogo de modelos en línea tras el traslado de la documentación de Gemini y el cambio de formato de sus tablas

# v1.5.0

###### 2026/11/04

* `Función` Las aplicaciones con firma independiente solicitan acceso a IA en el complemento, ligado a su identidad e instalación; revocarlo en los ajustes detiene las solicitudes activas
* `Función` La pantalla permanece encendida mientras se genera una respuesta en lugar de apagarse al agotarse el tiempo de espera
* `Corrección` Las solicitudes AI siguientes fallaban si el servicio se detenía antes de cerrar la sesión anterior
* `Corrección` Las solicitudes AI consecutivas se rechazaban mientras se cerraba la sesión del modelo cancelada
* `Corrección` Los perfiles de Anthropic enviaban siempre max_tokens=1024 aunque el máximo de tokens de salida estuviera sin límite, por lo que las respuestas se cortaban hacia los 1024 tokens sin ningún aviso (los modelos Claude que razonan por defecto descuentan también su razonamiento); ahora el valor por defecto es 16384 tokens y una respuesta detenida en el límite de salida se marca bajo el mensaje como "Límite de salida alcanzado" (también en perfiles compatibles con OpenAI y Gemini)
* `Corrección` La prueba de conexión solo permitía al modelo 8 tokens de salida; los modelos Claude que razonan por defecto los gastaban en razonar sin devolver texto, así que un perfil de Anthropic bien configurado se daba por respuesta no válida. Ahora se permiten 1024 tokens
* `Corrección` Cuando fallaba la generación en la nube o la prueba de conexión solo se mostraba una categoría fija de fallo y la causa real (por ejemplo, un saldo de cuenta agotado) quedaba oculta; ahora bajo el mensaje de fallo aparecen el estado HTTP y la explicación del propio proveedor (máximo 300 caracteres, con las claves enmascaradas)

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
