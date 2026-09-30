******

### Historial de versiones

******

# v1.3.0

###### 2026/09/30

* `Función` Unificar los ajustes con grupos planos, filas coherentes y diálogos redondeados centrados. El idioma, el modo nocturno, el color y el icono solo cambian al confirmar; Cancelar conserva los valores guardados. El color sigue AutoJs6 por defecto, con una paleta común, entrada HEX/RGB y vista previa local. Los fondos neutros se mantienen estables y los controles siguen el tema. El icono usa el modo adaptativo automático por defecto, conservando las elecciones explícitas al actualizar.
* `Corrección` A través de una pasarela compatible con OpenAI, los modelos Claude o Gemini transmiten una cadena vacía en lugar de "{}" como arguments de una herramienta sin parámetros; la sesión lo rechazaba como respuesta inválida y terminaba con ONLINE_INVALID_RESPONSE (así fallaron la primera ronda de herramientas de Claude Code Ex y una ronda de Gemini Ex de 3-Stove Agent en la pasarela AIGoCode). Ahora los arguments en blanco se tratan como un objeto vacío y el mensaje del asistente reenviado al modelo lleva la forma normalizada
* `Corrección` Las rondas de continuación de herramientas nativas restaban del límite de tokens de salida de la primera solicitud lo consumido en rondas anteriores, por lo que los turnos largos terminaban con ONLINE_INVALID_RESPONSE al agotarse el límite tras unas diez rondas (el caso de la calculadora de 3-Stove Agent fallaba entre la llamada 9 y la 14 tanto en Claude Code Ex como en Gemini Ex). Ahora cada continuación conserva el límite completo y el presupuesto de tarea del llamador acota el total, en línea con la decisión del 2026-09-29 de reiniciar el tiempo de espera de continuación
* `Corrección` El protocolo Gemini GenerateContent enviaba las declaraciones de herramientas y el responseSchema con su JSON Schema intacto, y la API oficial responde HTTP 400 ("Unknown name additionalProperties") ante palabras clave que no conoce, por lo que cada tarea de herramientas nativas de 3-Stove Agent fallaba en su primera llamada con ONLINE_REQUEST_REJECTED. Ahora solo se envía el subconjunto que Gemini admite (type, enum, properties, required, items, anyOf, minimum / maximum, minLength / maxLength, default y similares); los llamadores siguen validando argumentos y respuestas con el esquema completo
* `Mejora` Mantener el marco redondeado del icono de Acerca de con el interior transparente sobre el fondo de la página. Mostrar las opciones del lanzador desde arriba con notas más pequeñas.

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

# v1.1.3

###### 2026/09/15

* `Mejora` compileSdk sube a 37 (Android 17); targetSdk se mantiene en 36 hasta verificar el comportamiento que depende del objetivo

# v1.1.2

###### 2026/09/13

* `Corrección` Mantener la fecha de versión del complemento en inglés sin depender del idioma del equipo de compilación
* `Mejora` Recursos traducidos coherentes, activación explícita del complemento y validación de los paquetes de publicación

# v1.1.1

###### 2026/09/12

* `Función` Eliminar el modelo local seleccionado con selección automática de otro disponible y un aviso claro en las conversaciones que usan el modelo eliminado
* `Mejora` Marcar los modelos importados en el catálogo y ofrecer directamente la confirmación de descarga y la selección del destino
* `Mejora` Mejorar los resúmenes de ajustes que siguen AutoJs6, el contraste del tema y las acciones del historial de conversaciones
* `Mejora` Verificación de compilación de la alineación de páginas de 16 KB en bibliotecas nativas de 64 bits, con controles del contrato manifest e informes JSON

# v1.1.0

###### 2026/09/01

* `Función` Identidad de marca y de ejecución del plugin oficial de IA local de AutoJs6 consolidada como 3-Stone AI
* `Función` La integración entre procesos usa las identidades neutrales `ai-provider-api`, `org.autojs.plugin.ai.provider.api`, `org.autojs.plugin.AI_PROVIDER` e `IAiProvider`/`IAiSession`/`IAiCallback` sin conservar alias de las identidades reemplazadas
* `Función` Entrada de configuración de AI exportada, sin parámetros y protegida por permiso de firma para que AutoJs6 abra la configuración unificada del plugin sin enviar datos de perfiles ni credenciales
* `Función` Exposición directa de `local:*` y `profile:*` en el catálogo paginado de destinos de AI Provider V2, con provider/model/locality, estados configured y available, capacidades, límites, controles, orígenes HTTPS y una marca `isDefault` exacta independientes para cada destino
* `Función` Transferencia de `temperature`, `topK`, `topP` y `maxTokens` mediante solicitudes de generación AI Provider V2 a los controles de muestreo y tokens de salida de LiteRT-LM
* `Función` Informe de los recuentos exactos de tokens de entrada, salida y totales de LiteRT-LM, junto con la duración de generación medida por el proveedor, mediante `ai.chat().usage` y eventos usage de streaming
* `Función` Sesiones persistentes de AI Provider V2 y reutilización de Conversation de varios turnos con `ai.session` de AutoJs6 sin reenviar el historial anterior
* `Función` Decodificación nativa restringida por JSON Schema de LiteRT-LM mediante `structuredJson` y `responseSchema` de AutoJs6, compatible con llamadas únicas, streaming y sesiones persistentes, con validación estricta del JSON completo
* `Función` Perfiles backend explícitos `cpu`, `gpu` y `npu` como controles opcionales de destino de AI Provider V2, con informe de compatibilidad del dispositivo, aislamiento de caché por modelo/perfil y sin fallback desde perfiles no disponibles; GPU solo se declara tras una prueba de carga de OpenCL y NPU permanece no disponible porque su runtime EAP no está empaquetado
* `Función` Descarga directa de modelos LiteRT Community fijados a una ubicación SAF elegida, con progreso, cancelación precisa, limpieza, verificación de cabecera LiteRT-LM, tamaño y SHA-256, e importación directa posterior
* `Función` Se añadió un espacio de conversación iniciable con Markdown en streaming, historial persistente, aviso al sustituir una rama tras editar mensajes anteriores, búsqueda con varios resultados y entrada adaptada al teclado
* `Función` Se añadieron ajustes de aplicación para color del tema, modo oscuro, idioma, información de la aplicación y del desarrollador e historial de versiones, con Seguir AutoJs6 como valor predeterminado cuando sea posible
* `Función` Se añadieron ajustes de conversación para tamaño de fuente, comportamiento de Enter, output tokens ilimitados o personalizados y muestreo `temperature`, `topK` y `topP` predeterminado por el modelo o personalizado
* `Función` Se renderiza contenido `$\text{...}$` en línea durante el streaming, con comandos matemáticos comunes y estilos de superíndice y subíndice
* `Función` Se añadió un almacén de credenciales gestionado por el plugin con Android Keystore, AES-256-GCM, texto cifrado autenticado vinculado al profile, archivos privados atómicos entre procesos, consultas limitadas al estado configured y borrado inmediato del texto sin cifrar
* `Función` Se añadió un repositorio estricto y sin secretos de perfiles en línea para endpoints OpenAI Compatible solo por HTTPS, con UUID canónicos, metadatos atómicos entre procesos y reemplazo o eliminación obligatorios de la credencial al cambiar el provider o el origin
* `Función` Se añadió el backend interno del plugin para ejecución HTTPS OpenAI Compatible con perfiles de baseUrl, credencial y modelo personalizados, streaming SSE acotado y fallback JSON, cancelación precisa, usage del provider, historial persistente de turnos completados, mapping de JSON Schema y errores fijos sin datos sensibles; los destinos `profile:*` configurados lo invocan directamente mediante AI Provider V2
* `Función` Se añadieron preajustes de OpenAI, Anthropic, Gemini, DeepSeek y OpenRouter alineados con el catálogo del host; la capa unificada de ejecución en línea reutiliza el protocolo compatible con OpenAI y adapta por separado la autenticación, las solicitudes, los terminales SSE, el uso y JSON Schema nativos de Anthropic Messages y Gemini GenerateContent, sin fallback entre protocolos ni entre local y en línea
* `Función` Se añadió la UI de servicios en línea en 10 idiomas para añadir, editar y eliminar perfiles, sustituir y borrar claves API sin mostrarlas, elegir el destino predeterminado, exigir permiso para redes medidas antes de leer credenciales y ejecutar pruebas explícitas cancelables de hasta 120 segundos; los ajustes comparten el documento atómico entre procesos y actualizan dinámicamente el catálogo de destinos V2
* `Función` Se añadió un selector unificado de destinos locales y en la nube al chat del iniciador: cada conversación conserva una instantánea de destino, las conversaciones con mensajes recomiendan iniciar una nueva al cambiar y continuar con el contexto exige confirmación explícita y registra el cambio
* `Función` Se añadió a cada respuesta del asistente una instantánea del destino, proveedor, modelo y ubicación reales; la regeneración reutiliza exactamente el destino registrado, falla explícitamente si cambia o deja de estar disponible y nunca recurre silenciosamente al destino actual de la conversación
* `Función` Los fallos de generación local y en la nube permanecen en el destino seleccionado: el chat del iniciador añade una causa acotada sin datos sensibles, indica que no hubo fallback entre límites y ofrece un cambio manual explícito de destino sin descartar la salida parcial
* `Corrección` Se eliminaron los límites implícitos de 256 tokens y 4 KiB de los ejemplos ejecutables: omitir `maxTokens` usa ahora el valor predeterminado del modelo o motor y el ejemplo Binder directo usa los 64 KiB completos permitidos por el proveedor
* `Corrección` Se actualizó el ejemplo Binder de bajo nivel de las 10 instrucciones localizadas a las API finales de solicitud y lista de destinos de AI Provider V2
* `Corrección` Se corrigió que el gestor de modelos conservara los colores de texto del tema claro en el modo oscuro del sistema, lo que hacía ilegibles el texto, las casillas y las filas de modelos sobre el fondo oscuro
* `Corrección` Se mantuvo el editor visible sobre el teclado, se eligió el texto del botón Enviar según el contraste con el color del tema y se unificaron los controles de búsqueda anterior, siguiente y cerrar
* `Corrección` Se corrigió el bloqueo al cerrar una session desde un callback listener de generación, donde la espera de inactividad se esperaba a sí misma indefinidamente; el cierre sigue esperando los callbacks ya activos en otros hilos
* `Corrección` Se corrigió el rechazo del almacenamiento privado de perfiles en línea y credenciales cuando Android canonicaliza la raíz confiable `/data/user/0` como `/data/data`; se siguen rechazando los enlaces de hijos directos y las fugas de contención
* `Mejora` Descripción del plugin, instrucciones y README en 10 idiomas actualizados conforme a la formalización de la ruta unificada de destinos `ai.*`
* `Mejora` ROADMAP reescrito como hoja de ruta de funciones con elementos verificables individualmente
* `Mejora` Se normalizó la puntuación ASCII en la aplicación y en el texto localizado generado, con una prueba de regresión para el texto empaquetado y generado
* `Mejora` Se introdujo una capa compartida `AiBackend`/`AiTarget`/`AiBackendSession` para que el chat del iniciador y el proveedor Binder usen la misma ruta `LiteRtLocalBackend` de catálogo, capacidades, creación de sesiones, streaming y cancelación
* `Mejora` Se combinaron los targets locales `local:*` y en línea `profile:*` en un catálogo y despachador únicos a nivel de Application, se expusieron ambos directamente mediante AI Provider V2 y se derivaron dinámicamente provider locality, credential mode y HTTPS origins sin exponer bytes de credenciales
* `Mejora` Unificar el diseño del README y la gestión de versiones de la plataforma Gradle
* `Mejora` Abrir la página integrada del historial de versiones desde el botón correspondiente del diálogo de actualización

# v1.0.0

###### 2026/08/08

* `Función` Base de AI Provider en el dispositivo con ID y motor `three-stone-ai`, provider ID `autojs6.three-stone-ai` y variante `default`
* `Función` Generación de texto sin formato con LiteRT-LM y CPU, historial system, user y assistant y streaming controlado por credits
* `Función` Importación SAF de `.litertlm` al almacenamiento privado con límite de 8 GiB, reserva de espacio, SHA-256, fsync y activación atómica
* `Función` Una sesión activa, I/O limitada, cuotas de descriptores, cancelación, timeout, un estado terminal y verificación del llamador AutoJs6 con la misma firma
* `Función` Omisión explícita de las capacidades reasoning, tools, structured JSON, usage, red y credential
* `Función` APK arm64-v8a, x86_64 y universal con README, changelog, interfaz Android e instrucciones del plugin en 10 idiomas
* `Función` Pantalla de gestión de modelos para consultar el catálogo completo y el espacio usado en el almacenamiento privado, con selección atómica del modelo actual sin copiar archivos de modelo
* `Mejora` Conservación de generaciones anteriores con nombre de hash SHA-256 tras una importación de reemplazo para evitar carreras entre procesos con `:provider`, por lo que los archivos conservados siguen ocupando almacenamiento privado
* `Mejora` Se añadió un coordinador de importación única con alcance de aplicación y un pending journal sincronizado con fsync para mantener el trabajo al recrear Activity, recuperar en arranque frío, limpiar temporales stale y limitar el borrado a destinos creados por el intento actual y nunca publicados, mientras se conservan las generaciones publicadas, current e históricas con nombre de hash
* `Dependencia` Se añadió LiteRT-LM 0.15.0 para generación de texto con CPU en el dispositivo
