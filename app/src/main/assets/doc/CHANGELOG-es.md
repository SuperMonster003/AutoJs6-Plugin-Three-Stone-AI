******

### Historial de versiones

******

# v1.1.0

###### 2026/08/21

* `Función` Plugin renombrado a On-Device AI, posicionado como el plugin oficial de IA local de AutoJs6
* `Función` Compatible con el selector abreviado `plugin: true` de `ai.ask`/`ai.chat`/`ai.stream` y la enumeración de modelos `ai.models` de AutoJs6
* `Función` Transferencia de `temperature`, `topK`, `topP` y `maxTokens` mediante el protocolo On-Device AI 1.1 a los controles de muestreo y tokens de salida de LiteRT-LM
* `Función` Informe de los recuentos exactos de tokens de entrada, salida y totales de LiteRT-LM, junto con la duración de generación medida por el proveedor, mediante `ai.chat().usage` y eventos usage de streaming
* `Función` Sesiones persistentes del protocolo On-Device AI 1.2 y reutilización de Conversation de varios turnos con `ai.session` de AutoJs6 sin reenviar el historial anterior
* `Función` Decodificación nativa restringida por JSON Schema de LiteRT-LM mediante `structuredJson` y `responseSchema` de AutoJs6, compatible con llamadas únicas, streaming y sesiones persistentes, con validación estricta del JSON completo
* `Función` Perfiles backend explícitos `cpu`, `gpu` y `npu` mediante el protocolo 1.3 y las opciones de generación de AutoJs6, con informe de compatibilidad del dispositivo, aislamiento de caché por modelo/perfil y sin fallback desde perfiles no disponibles; GPU solo se declara tras una prueba de carga de OpenCL y NPU permanece no disponible porque su runtime EAP no está empaquetado
* `Función` Descarga directa de modelos LiteRT Community fijados a una ubicación SAF elegida, con progreso, cancelación precisa, limpieza, verificación de cabecera LiteRT-LM, tamaño y SHA-256, e importación directa posterior
* `Corrección` Se corrigió que el gestor de modelos conservara los colores de texto del tema claro en el modo oscuro del sistema, lo que hacía ilegibles el texto, las casillas y las filas de modelos sobre el fondo oscuro
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
