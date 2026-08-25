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
* `Función` Los fallos de generación local y en la nube permanecen en el destino seleccionado: el chat del iniciador añade una causa acotada sin datos sensibles, indica que no hubo fallback entre límites y ofrece un cambio manual explícito de destino sin descartar la salida parcial
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
