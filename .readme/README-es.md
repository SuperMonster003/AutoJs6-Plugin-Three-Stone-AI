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
- Enumerar, renombrar, comprobar y eliminar modelos importados en la sección "Modelos locales", con el espacio que ocupa cada modelo en su tarjeta; al eliminar un modelo también se elimina la caché del motor LiteRT-LM creada en su primera carga. "Limpiar archivos sin referencia" recupera los archivos de modelos sin referencia, las cachés de motor sobrantes y los adjuntos que ya no se usan.
- Seleccionar explícitamente `cpu`, `gpu` o `npu` desde AutoJs6; CPU es el valor predeterminado, GPU se expone solo tras una prueba de carga OpenCL y NPU se informa como no disponible porque su runtime EAP no está incluido.
- Gestionar servicios en línea integrados y personalizados, credenciales de Android Keystore, el destino predeterminado único (un modelo en línea o local), redes medidas y pruebas de conexión explícitas y limitadas en Ajustes > Modelos y servicios. Los servicios se editan en la página a pantalla completa "Detalles del servicio": cada servicio puede tener varios modelos, que se reordenan arrastrando y entre los que se elige un modelo preferido, y los cambios solo se guardan con la marca de verificación.
- Define hasta 16 encabezados de solicitud personalizados por servicio en "Encabezados de solicitud" de la página de detalles del servicio; cada valor es un valor fijo, un valor secreto o "Generado por conversación" (derivado del servicio y de la conversación, por lo que no cambia dentro de una conversación). Hay ajustes predefinidos para Sesión de OpenCode Go, Atribución de OpenRouter, Helicone y Cloudflare AI Gateway; cuando la dirección del servicio está en opencode.ai sin el encabezado de sesión, o el servicio devuelve MissingSessionID, se ofrece una forma de añadirlo. Los encabezados que envía el propio plugin o que gestiona la conexión HTTP no se pueden personalizar; User-Agent sustituye el valor predeterminado, y anthropic-beta se combina con el valor del plugin. Los encabezados de solicitud se aplican automáticamente con la configuración del servicio y no se exponen a los scripts.
- Vincular cada conversación del iniciador a una instantánea de destino local o en línea; al cambiar una conversación con mensajes se recomienda una nueva, y continuar exige confirmación explícita y registra el cambio.
- Guardar una instantánea del destino, proveedor, modelo y ubicación reales en cada respuesta del asistente; la regeneración reutiliza exactamente el destino registrado, falla explícitamente si cambia o deja de estar disponible y nunca recurre silenciosamente al destino actual de la conversación.
- Mantener los fallos de generación local y en la nube dentro del destino seleccionado: el chat del iniciador añade una causa acotada sin datos sensibles, indica que no hubo fallback entre límites y ofrece un cambio manual explícito de destino sin descartar la salida parcial.
- Llamadas nativas a herramientas para destinos en línea compatibles con OpenAI, Anthropic Messages y Gemini GenerateContent, con argumentos en streaming, llamadas paralelas y continuación tras resultados.
- Entrada JPEG/PNG y resultados de herramientas con imágenes mediante AI Provider 2.1 negociado, con ajustes por modelo.
- Mantener al día los modelos predefinidos en línea con el catálogo público del proyecto en GitHub. La actualización automática está activada por defecto y comprueba una vez cada 24 horas al abrir la página "Modelos y servicios"; el menú de la sección "Servicios en línea" permite desactivarla o actualizar manualmente. Respeta el ajuste de redes medidas, conserva la lista almacenada o integrada sin conexión o ante errores y no cambia los servicios guardados ni los ID personalizados.
- Agrupar los modelos predefinidos por proveedor y ampliar las opciones de OpenRouter con Qwen, Kimi, GLM, Grok, Meta y MiniMax, conservando los ID exactos.
- Unificar los ajustes con grupos planos, filas coherentes y diálogos redondeados centrados. El idioma, el modo nocturno, el color y el icono solo cambian al confirmar; Cancelar conserva los valores guardados. El color sigue AutoJs6 por defecto, con una paleta común, entrada HEX/RGB y vista previa local. Los fondos neutros se mantienen estables y los controles siguen el tema. El icono usa el modo adaptativo automático por defecto, conservando las elecciones explícitas al actualizar.
- La página principal de ajustes solo conserva los ajustes de apariencia y cinco entradas: "Conversación", "Modelos y servicios", "Voz", "Privacidad y permisos" y "Actualizaciones y acerca de". Cada ajuste aparece en un solo lugar, y el historial de versiones está en "Actualizaciones y acerca de". El menú y el panel lateral del chat llevan a los ajustes de modelos a través de "Ajustes"; abrir los ajustes del plugin desde AutoJs6 lleva directamente a "Modelos y servicios", y Atrás vuelve a la página principal de ajustes.
- La generación de respuestas ya no depende de la pantalla del chat: sigue al girar la pantalla o al cambiar el modo oscuro, el idioma o el tamaño de letra de los mensajes, y una respuesta que termina mientras estás fuera del chat se muestra completa al volver (una respuesta aún se pierde si el sistema cierra el proceso de la aplicación). Pulsar "Detener", crear o cambiar de conversación, cambiar de modelo o eliminar mensajes corta la solicitud en línea en menos de 1 segundo, y una respuesta detenida ya no suma uso.
- Los servicios en línea pueden fijar un esfuerzo predeterminado (Predeterminado, Bajo, Medio, Alto, Muy alto o Máximo; con "Predeterminado" decide el modelo), y los servicios de Anthropic pueden activar el modo rápido de forma predeterminada (respuestas más rápidas a un precio mayor); ambos se pueden cambiar solo para la conversación actual, y el cambio se guarda con ella. Si un modelo no admite el nivel elegido, se usa el nivel inferior más cercano. Los scripts que llaman a la IA a través de la interfaz del plugin no usan estos dos ajustes.
- Cambiar de modelo abre un panel desde abajo: los favoritos están en el grupo "Favoritos" de arriba y el resto se agrupa por servicio, con cada modelo una sola vez; los resultados de búsqueda no se agrupan; cada fila muestra debajo su servicio (el tamaño del archivo en los modelos locales) y etiquetas de entrada de imágenes, razonamiento y modo rápido; el panel también cambia el esfuerzo y el modo rápido de la conversación actual, y "Administrar modelos y servicios" abajo abre los ajustes de modelos. La barra superior del chat muestra el esfuerzo y el modo rápido de la conversación actual.
- Muestra cómo piensa el modelo (Claude 5 y posteriores, Gemini 2.5 y posteriores, y los servicios compatibles con OpenAI que devuelven su razonamiento, como DeepSeek): mientras se genera la respuesta, el razonamiento aparece en tiempo real en una tarjeta encima de ella y luego se contrae en "Pensó durante N s"; al tocarla se abre o se cierra. Las opciones "Mostrar el razonamiento durante la generación" y "Contraer el razonamiento al terminar" están activadas de forma predeterminada. El razonamiento se guarda con la conversación, pero no se lee en voz alta, no aparece en la búsqueda ni se copia, y los scripts no lo reciben.
- Toca "+" a la izquierda del cuadro de entrada para añadir adjuntos, hasta 20 por mensaje: las imágenes se eligen en el selector de fotos del sistema (las demasiado grandes se reducen) y van a los modelos con "Entrada de imágenes" activada; los archivos de texto UTF-8 (txt, md, json, csv, archivos de código, etc.) se envían como texto; los PDF van a servicios cuyo proveedor es Anthropic o Gemini. Los modelos locales aún no admiten adjuntos, y los que el modelo actual no admite se atenúan y no se pueden enviar. Los adjuntos se guardan con el mensaje, que muestra una miniatura o el nombre del archivo; tócalo para abrirlo en otra aplicación. Los turnos siguientes vuelven a enviar los adjuntos anteriores en la medida que permita el contexto, y de los que no caben solo indican al modelo el nombre del archivo.
- Al regenerar una respuesta o editar y reenviar un mensaje, se crea una nueva versión de ese turno en lugar de borrar la conversación posterior: "< 2/3 >" bajo la respuesta cambia de versión, los mensajes posteriores cambian con ella y, al reabrir la conversación, se muestra la última versión elegida. La búsqueda, la exportación, la lectura en voz alta, las estadísticas de uso y la continuación de la conversación solo usan la versión mostrada.
- Las conversaciones se pueden exportar con "Exportar como Markdown" (tú eliges la ubicación en el cuadro de guardado del sistema, y otros lectores de Markdown muestran bien el archivo) o compartir con "Compartir como imagen" (una imagen larga dibujada como la página de chat y compartida con el panel de compartir del sistema), desde el menú de la página de chat o manteniendo pulsada una conversación en el panel lateral. Solo se exportan las versiones mostradas; si la conversación tiene razonamiento, se pregunta si incluirlo, y por defecto no se incluye.
- El historial de conversaciones usa el formato v6, que también guarda el esfuerzo y el modo rápido de cada conversación, el razonamiento y su duración de cada respuesta, y la información de los adjuntos y las otras versiones de los mensajes; las conversaciones guardadas por 1.5.0 y versiones anteriores se siguen abriendo. Tras volver a 1.6.0 o anterior solo se ven las versiones mostradas en ese momento: la versión antigua descarta las otras versiones de todas las conversaciones en cuanto abre la página de chat, y volver a actualizar no las recupera.
- Un micrófono dentro del cuadro de entrada: tócalo para dictar, y el texto reconocido se inserta en el cursor para que lo revises antes de enviarlo; con 3-Macaw Speech instalado dicta en directo en el cuadro de entrada, con el texto gris apareciendo mientras hablas y cada frase volviéndose definitiva mientras sigue escuchando, hasta que lo vuelves a tocar. Mantenlo pulsado para una conversación por voz que envía lo que dices y vuelve a escuchar después de leer la respuesta; con 3-Macaw Speech instalado ningún turno abre la pantalla del reconocedor, y un turno con el micrófono ocupado o sin modelo de reconocimiento usa la ventana.
- Lectura de respuestas en voz alta: mantén pulsada una respuesta y elige "Leer en voz alta", o activa "Leer las respuestas en voz alta" en Ajustes > Voz para escuchar las respuestas nuevas mientras se generan. Las marcas de títulos y listas no se leen, y un bloque de código o una tabla se anuncia con una frase.
- Responde al `VOICE_COMMAND` del asistente digital de 3-Macaw Speech: mantén pulsado el botón de encendido, habla y toca "3-Stone AI"; la pregunta se envía en una conversación nueva y la respuesta se lee en voz alta.

******

### Voz

******

El dictado y la lectura usan las interfaces del sistema Android (`RecognizerIntent` y `TextToSpeech`) y, con 3-Macaw Speech instalado, también sus sesiones de dictado directamente. 3-Stone AI nunca graba audio por sí mismo ni pide el permiso del micrófono. Con 3-Macaw Speech instalado, "Preferir 3-Macaw Speech" en Ajustes > Voz está activado de forma predeterminada: al tocar el micrófono se dicta en directo en el cuadro de entrada. El texto que aún no es definitivo aparece en gris, cada frase se vuelve definitiva tras una pausa de un segundo aproximadamente y la escucha continúa, así que puedes decir varias frases seguidas; termina cuando vuelves a tocar el micrófono (mientras tanto es un botón de parada), tras 15 segundos sin hablar o a los 5 minutos. Una pulsación larga en el micrófono inicia una conversación por voz que ya no abre la pantalla del reconocedor en ningún turno: lo que dices aparece en gris al final del cuadro de entrada, cada frase se envía al terminarla, la escucha se reanuda después de leer la respuesta, y 8 segundos sin hablar la terminan; toca el micrófono mientras escucha para enviar enseguida lo que has dicho. Salir del chat, enviar o cambiar de conversación termina el dictado, y el texto no enviado se queda en el cuadro de entrada. Cuando otra aplicación está usando el micrófono (por ejemplo la escucha permanente de 3-Stove Agent) o todavía no se ha descargado un modelo de reconocimiento, esa vez se abre en su lugar la ventana de reconocimiento de 3-Macaw Speech; sin el permiso del micrófono se abre su pantalla de permisos. La lectura usa sus voces sin conexión. Sin él, o con la opción desactivada, se usan la aplicación de reconocimiento del teléfono y el motor de texto a voz predeterminado, y cada dictado y cada turno de una conversación por voz abren la pantalla del reconocedor. Mientras se lee una respuesta, toca la barra de estado sobre el cuadro de entrada para interrumpir y hablar enseguida, o usa su botón para salir.

- **Varias aplicaciones de reconocimiento de voz piden elegir cada vez?** Deja activado "Preferir 3-Macaw Speech" para usar 3-Macaw Speech directamente. Si lo desactivas, decide el sistema, y puedes fijar una aplicación predeterminada en su selector.
- **Pausas entre frases en un teléfono lento?** Algunas voces (como MeloTTS y Kokoro) sintetizan más despacio de lo que hablan en teléfonos lentos. Elige AISHELL-3, Piper o Matcha como voz predeterminada en 3-Macaw Speech.
- **El micrófono indica que no hay modelo de reconocimiento?** La ventana de 3-Macaw Speech te guía para descargar uno; mientras tanto, 3-Stone AI no cambia por su cuenta a otro reconocedor. Desactiva "Preferir 3-Macaw Speech" para usar otra aplicación entretanto.
- **Si tengo 3-Macaw Speech instalado, por qué a veces se abre la pantalla del reconocedor?** Cuando otra aplicación está usando el micrófono (por ejemplo la escucha permanente de 3-Stove Agent), todavía no se ha descargado un modelo de reconocimiento o no se puede conectar con 3-Macaw Speech por el momento, esa vez se usa su ventana de reconocimiento: la ventana pausa la escucha de la otra aplicación y te guía para descargar un modelo, y la siguiente vez se vuelve a intentar primero el dictado en directo. El dictado en directo necesita AutoJs6 instalado en el teléfono; Ajustes > Voz muestra si funciona y, si no, por qué.
- **El dictado en directo y la conversación por voz continúan en segundo plano?** No. Solo funcionan mientras el chat está en pantalla y terminan al cambiar a otra aplicación, bloquear la pantalla o recibir una llamada; el texto ya mostrado se queda en el cuadro de entrada. De vuelta en el chat, toca o mantén pulsado el micrófono para continuar.
- **Cómo pregunto a través del asistente digital de 3-Macaw Speech?** Configura 3-Macaw Speech como asistente digital predeterminado, mantén pulsado el botón de encendido (o de inicio), habla y toca "3-Stone AI" en la tarjeta del resultado. 3-Stone AI abre una conversación nueva, envía la pregunta enseguida y lee la respuesta; desbloquea antes el teléfono si está bloqueado. Si han pasado menos de 10 segundos desde la pregunta anterior de este tipo, el texto solo se pone en el cuadro de entrada y no se envía.
- **A dónde va el texto reconocido?** Al modelo seleccionado, igual que el texto escrito; un modelo en línea lo envía a su proveedor. 3-Macaw Speech funciona totalmente sin conexión; otras aplicaciones de reconocimiento o de voz pueden usar la red.

Los scripts pueden componer las mismas preguntas y respuestas habladas con el módulo `speech` de AutoJs6 (proporcionado por 3-Macaw Speech) y el módulo `ai`, sin la interfaz de 3-Stone AI: `speech.listen` escucha una frase, `ai.chat` o `ai.stream` genera la respuesta y `speech.speak` la lee. El ejemplo siguiente deja de escuchar después de la primera frase:

```js
let session = speech.listen({ timeoutMillis: 8000 });
session.on('final', () => session.stop());
session.on('end', () => {
    let question = session.result();
    console.log('Heard:', question);
    if (!question) return;
    ai.chat(question, { maxTokens: 256 })
        .then((response) => {
            console.log('Answer:', response.text);
            return speech.speak(response.text);
        })
        .catch((error) => console.error(error.code, error.message));
});
```

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

El plugin solicita `INTERNET` para descargas de modelos recomendados iniciadas por el usuario, solicitudes a un destino en línea configurado por el usuario y actualizaciones del catálogo público de modelos predefinidos desde GitHub en la página "Modelos y servicios". Estas actualizaciones no requieren una clave API ni realizan inferencia, y no se inician al activar el plugin ni durante la generación local. Las credenciales permanecen en el almacenamiento privado cifrado del plugin y nunca cruzan Binder ni el catálogo de destinos. No solicita ningún permiso general de almacenamiento. Las descargas de archivos de modelos usan revisiones HTTPS inmutables, tamaño y SHA-256 fijados, y solo escriben en un archivo parcial del almacenamiento privado `files/model-downloads` (se reintentan automáticamente y se reanudan tras una interrupción); el modelo se mueve a `files/models` y pasa a la importación para activarse de forma atómica solo después de superar cabecera LiteRT-LM, tamaño, resumen, flush y fsync. El servicio Provider original del anfitrión también verifica el paquete AutoJs6, el UID y las firmas. Las funciones de voz no piden el permiso del micrófono: graba la aplicación de reconocimiento o 3-Macaw Speech. El dictado en directo y la conversación por voz sin pantalla del reconocedor se vinculan directamente al servicio de voz de 3-Macaw Speech (`org.autojs.plugin.SPEECH`, que solo pueden vincular las aplicaciones con el permiso de plugin de AutoJs6); 3-Stone AI solo recibe el texto reconocido, nunca el audio. El texto reconocido y el leído no se registran, y los mensajes dictados se guardan igual que los escritos. El razonamiento que devuelve un modelo solo se guarda en el historial local de conversaciones; no se reenvía al servicio en línea en los turnos siguientes, y los scripts no lo reciben a través de la interfaz del plugin. Los adjuntos del chat se leen con los selectores del sistema, sin permiso de almacenamiento ni de fotos; los archivos elegidos se copian en `files/attachments`, privado de la aplicación, se envían solo al servicio del modelo en línea elegido y se eliminan con la conversación. Abrir un adjunto en otra aplicación concede acceso de lectura temporal mediante un FileProvider que solo expone `files/attachments`. Una imagen larga compartida se escribe en la caché de la aplicación `cache/share` y se entrega al panel de compartir mediante otro FileProvider que solo expone ese directorio; antes de cada imagen nueva se eliminan las de más de 24 horas y se conservan como máximo 3. El Markdown exportado solo se escribe en la ubicación que eliges en el cuadro de guardado del sistema. Entre los encabezados de solicitud personalizados de un servicio, los valores secretos se guardan cifrados en el dispositivo mediante Android Keystore, igual que la clave API; nunca se vuelven a mostrar en la interfaz ni se incluyen en las exportaciones, y hay que volver a introducirlos tras importar, clonar o cambiar de proveedor u origen HTTPS. Las claves API y los valores secretos que aparecen en los detalles de error del proveedor se sustituyen por `[redacted]`. Los valores fijos se guardan en texto plano y se incluyen en las exportaciones, así que no introduzcas claves como valores fijos.

******

### Límites operativos

******

- La importación de un modelo tiene un límite estricto de 8 GiB y debe dejar al menos 256 MiB libres.
- Solo se conserva una descarga de modelo sin terminar; empezar la de otro modelo la descarta (la confirmación lo indica). La biblioteca de descargas común a los plugins oficiales de AutoJs6 escribe la descarga en el almacenamiento privado `files/model-downloads/<sha256>/model.litertlm.part`: tras perder la red reintenta automáticamente dos veces (tras unos 2 y 5 segundos), cada vez continuando con una petición HTTP Range; pausar, cerrar la aplicación o que termine el proceso conservan el archivo, y al reanudar o reintentar se vuelve a verificar la parte recibida antes de continuar; la cancelación, un contenido no válido o un resumen que no coincide lo eliminan. Una descarga sin terminar nunca se elimina por su antigüedad. Un archivo verificado se mueve a `files/models/.download-<sha256>.part` y pasa a la importación. En un arranque en frío, la descarga más reciente de un modelo recomendado aún no importado se restaura en pausa y las demás se eliminan; un archivo sin terminar que la 1.7.0 dejó en `files/models/.download-<sha256>.part` se mueve a la nueva ubicación y continúa desde los bytes ya recibidos.
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
- Límites de adjuntos: 5 MB por imagen, 256 KB por archivo de texto, que debe ser UTF-8 válido, 32 MB y como máximo 100 páginas por PDF (los PDF cifrados se rechazan), 20 por mensaje y 48 MB en total por conversación. Bytes originales de adjuntos por solicitud: 32 MB para Anthropic y servicios compatibles con OpenAI, 20 MB para Gemini; los servicios compatibles con OpenAI no aceptan PDF. Un mensaje que supera el límite del destino elegido no se envía, y se indica el límite.
- Cada turno guarda como máximo 8 versiones; cuando las versiones ocultas de una conversación superan unos 2 MiB en total, se descartan primero las más antiguas. Al eliminar un mensaje con varias versiones, solo se elimina la versión mostrada.
- Una imagen larga de más de 16384 píxeles de alto se reduce proporcionalmente; si falta memoria, la escala se reduce a la mitad y se reintenta, y si vuelve a fallar se sugiere exportar como Markdown. Las conversaciones cuya respuesta aún se está generando no se pueden exportar.
- AiGoCode gpt-5.6-sol pasó las pruebas reales de imagen inicial e imagen en resultados de herramientas con Provider 1.2.0 / build 218. Estas pruebas con imágenes sintéticas no demuestran compatibilidad con otros destinos ni una tarea visual completa de Agent.
- Los errores en línea solo exponen categorías fijas `ONLINE_*` en el campo opcional `providerCode`. Las excepciones desconocidas no tienen categoría; se excluyen URL, credenciales, respuestas del servicio y texto original de excepciones. El código de error y la política de reintentos no cambian.
- Cada servicio tiene como máximo 16 encabezados de solicitud personalizados: los nombres tienen 1 - 64 bytes de caracteres token de RFC 9110 y son únicos sin distinguir mayúsculas; los valores fijos y secretos tienen 1 - 1024 bytes de caracteres ASCII visibles y espacios; el total de los encabezados de solicitud no supera 8192 bytes (cada entrada "Generado por conversación" cuenta como 36 bytes). Los 22 encabezados que envía el plugin o que gestiona la conexión HTTP no se pueden personalizar y también se rechazan al importar. Un encabezado secreto cuyo valor nunca se definió no se envía; si no se puede leer el registro de un valor secreto, toda la solicitud falla en lugar de enviarse sin el encabezado.

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

# v1.9.0

###### 2026/11/28

* `Función` Con 3-Macaw Speech instalado, al tocar el micrófono se dicta en directo en el cuadro de entrada en lugar de abrir la pantalla del reconocedor: el texto que aún no es definitivo aparece en gris, cada frase se vuelve definitiva tras una pausa y la escucha continúa, así que puedes decir varias frases seguidas; termina cuando vuelves a tocar el micrófono (mientras tanto es un botón de parada), tras 15 segundos sin hablar o a los 5 minutos. Salir del chat, enviar o cambiar de conversación termina el dictado, y el texto ya mostrado se queda en el cuadro de entrada
* `Función` Con 3-Macaw Speech instalado, la conversación por voz que se inicia con una pulsación larga en el micrófono tampoco abre ya la pantalla del reconocedor: lo que dices aparece en gris al final del cuadro de entrada, cada frase se envía al terminarla y la escucha se reanuda después de leer la respuesta; termina tras 8 segundos sin hablar, con el botón de salida de la barra de estado o al salir del chat, y tocar el micrófono mientras escucha envía enseguida lo que has dicho
* `Corrección` En una conversación seguida con un modelo local (sobre todo en la conversación por voz), el turno siguiente a veces se detenía con "Resource cache already has an active lease": pedía el modelo antes de que el turno anterior lo hubiera devuelto. Ahora el turno siguiente espera a que lo devuelva (normalmente menos de un segundo); si el modelo sigue realmente ocupado con otra tarea más de un minuto, se muestra "El modelo en el dispositivo sigue ocupado con otra tarea" en lugar del error interno
* `Corrección` En conversaciones largas con un modelo local, los mensajes más antiguos nunca se resumían (cada intento fallaba), por lo que el modelo olvidaba con más facilidad el principio de la conversación; ahora un modelo local resume después de 30 segundos de pausa en la conversación y cede el paso a un turno nuevo que empiece mientras tanto
* `Corrección` Después de chatear con un modelo local, un script de AutoJs6 que llamaba a un modelo local (o al revés) hacía que el chat y el servicio de scripts cargaran cada uno su propia copia del modelo, lo que duplicaba la memoria usada (unos 1,5 GB más con Gemma 4 E2B); en teléfonos con poca memoria el sistema podía terminar la llamada del script ("BINDER_DIED"). Ahora, antes de cargar el modelo, cada lado pide al otro que devuelva su copia sin usar, de modo que ambos tienen una solo mientras los dos generan una respuesta a la vez
* `Corrección` Cuando la apariencia del plugin no coincidía con la del sistema (por ejemplo, un sistema claro con el plugin siguiendo un AutoJs6 oscuro), un arranque en frío mostraba primero el modo y el idioma del sistema y luego saltaba a los del plugin. Ahora el plugin recuerda el último idioma, modo y color de tema de AutoJs6, así que la primera pantalla de un arranque en frío ya los usa; en Android 12 y posteriores además comunica su apariencia al sistema, así que desde el segundo inicio después de instalarlo la pantalla de inicio también la usa desde el principio (en Android 11 y anteriores la pantalla de inicio sigue al sistema)
* `Corrección` La conversación por voz y el dictado en directo en el cuadro de entrada mostraban "escuchando" antes de que el micrófono empezara a grabar de verdad, así que podían perderse las primeras palabras; ahora muestran primero "preparando el micrófono" y "escuchando" cuando empieza la grabación
* `Mejora` Cuando otra aplicación está usando el micrófono (por ejemplo la escucha permanente de 3-Stove Agent) o 3-Macaw Speech todavía no tiene un modelo de reconocimiento, ese dictado o ese turno de conversación abre en su lugar su ventana de reconocimiento (la ventana pausa la escucha de la otra aplicación y te guía para descargar un modelo); sin el permiso del micrófono se abre la pantalla de permisos de 3-Macaw Speech. Ajustes > Voz muestra si funciona el dictado en directo en el cuadro de entrada y, si no, por qué. Sin 3-Macaw Speech, o con "Preferir 3-Macaw Speech" desactivado, todo funciona como antes
* `Dependencia` Se añade la interfaz de plugin de voz speech-api de AutoJs6, contrato v3 (dictado en directo y conversación por voz en el cuadro de entrada)

# v1.8.0

###### 2026/11/27

* `Aviso` Después de añadir encabezados de solicitud a un servicio, la configuración de los servicios se guarda en un formato nuevo (también los archivos exportados que contienen encabezados de solicitud), que la versión 1.7.2 y anteriores no pueden leer. Para volver a una versión anterior, elimina primero los encabezados de solicitud de todos los servicios y guarda
* `Función` Los servicios en línea pueden tener encabezados de solicitud personalizados: en la página "Modelos y servicios", abre un servicio y toca "Encabezados de solicitud" en "Conexión" para añadirlos, editarlos o eliminarlos, hasta 16 por servicio. Hay tres orígenes de valor: un valor fijo; un valor secreto (marca "Secreto"; se marca de forma predeterminada cuando el nombre contiene auth, key, token o secret); y "Generado por conversación" (no cambia dentro de una conversación, ni siquiera al cambiar a otro modelo del mismo servicio). Pueden usarlos los servicios de los 6 proveedores. Los encabezados de solicitud se aplican automáticamente con la configuración del servicio, así que los scripts no necesitan cambios, y las sesiones persistentes de los scripts (ai.session) también usan un ID de sesión fijo
* `Función` Los valores secretos se guardan cifrados en este dispositivo; la interfaz solo muestra "Definido", no se vuelven a mostrar al editar y no se incluyen en las exportaciones. Tras importar o clonar un servicio, los valores secretos quedan "Sin definir" y la tarjeta del servicio indica "Falta un valor de encabezado"; el encabezado no se envía hasta que vuelvas a introducir el valor. Al cambiar de proveedor u origen HTTPS y guardar, los valores secretos guardados se borran junto con la clave API. Si en los detalles de error devueltos por el proveedor aparece una clave API o un valor secreto, se sustituye por [redacted]
* `Función` Los encabezados que envía el propio plugin (por ejemplo Authorization, x-api-key, Content-Type) y los que gestiona la conexión HTTP (por ejemplo Host, Content-Length) no se pueden personalizar, y el diálogo indica el motivo; User-Agent sí se puede personalizar (sustituye el valor predeterminado del plugin), y anthropic-beta se combina con el valor que el plugin calcula para la solicitud. Cuando el formato o la longitud de un nombre o un valor no son válidos, o el total de los encabezados de solicitud supera 8192 bytes, el diálogo también indica el motivo
* `Función` Al añadir un encabezado de solicitud puedes desplegar "Ajustes predefinidos comunes": elegir Sesión de OpenCode Go o Atribución de OpenRouter añade los encabezados correspondientes, y elegir Helicone o Cloudflare AI Gateway rellena el nombre, de modo que solo hay que introducir la clave. Cuando la dirección del servicio está en opencode.ai pero falta el encabezado de sesión x-opencode-session, la página de detalles del servicio muestra un aviso con un botón "Añadir"; si al probar la conexión o al chatear se recibe un error MissingSessionID, el mensaje de error explica la causa y ofrece un botón "Añadir encabezado de sesión" para resolverlo
* `Corrección` Cuando el idioma del plugin y el del sistema se escriben en sentidos opuestos (por ejemplo, el plugin cambiado a árabe en un sistema en chino), los botones de la parte inferior de los diálogos, como "Cancelar" y "Aceptar", ahora se invierten junto con el resto de la interfaz en lugar de quedarse en el lado del idioma del sistema

# v1.7.2

###### 2026/11/20

* `Mejora` La página del historial de versiones ahora se expande o contrae por versión: una versión contraída muestra su fecha y el número de entradas por categoría, una expandida nombra cada categoría una sola vez; todas las versiones se pueden expandir o contraer a la vez, la más reciente se abre expandida y el subtítulo muestra el nombre del plugin
* `Mejora` Se reescribieron las instrucciones del Centro de plugins en cinco partes: primeros pasos, funciones principales, uso en scripts, requisitos y preguntas frecuentes
* `Dependencia` Se añadió Jetpack Compose BOM 2026.09.00 (página Historial de versiones)
* `Dependencia` Se actualizaron AndroidX Core a 1.16.0, AndroidX Activity a 1.12.2 y AndroidX Lifecycle a 2.9.4 (los requiere Jetpack Compose)
* `Dependencia` Se actualizaron los plugins de compilación compartidos versión 1.9.0 -> 1.9.7

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

Los parámetros proceden de `version.properties`. El SDK mínimo actual es 24, el SDK objetivo es 37 y se requiere JDK 21 o posterior.

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
