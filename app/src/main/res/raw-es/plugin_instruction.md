3-Stone AI ofrece a AutoJs6 chat con IA y gestión de modelos: los modelos locales se ejecutan sin conexión en el teléfono y los servicios en línea (por ejemplo OpenAI, Anthropic, Gemini) usan tu propia clave API. El plugin tiene su propia pantalla de chat y también proporciona los modelos del módulo `ai` de los scripts de AutoJs6.

### Primeros pasos

1. Abre 3-Stone AI desde su icono en la pantalla de inicio, toca "Ajustes" en el menú de la pantalla de chat y luego "Modelos y servicios".
2. Para un modelo local, toca "Descargar modelo" en la sección "Modelos locales" y elige un modelo recomendado; se importa automáticamente tras descargarlo y verificarlo. Si ya tienes un archivo `.litertlm`, toca "Importar archivo de modelo".
3. Para un servicio en línea, toca "Añadir servicio" en la sección "Servicios en línea", elige un proveedor, introduce el ID del modelo y la clave API y guarda. Después puedes elegir "Probar conexión" en el menú del servicio.
4. Vuelve a la pantalla de chat, toca el nombre del modelo en la parte superior para elegir un modelo, escribe una pregunta y envíala.

### Funciones principales

- **Chat**: las conversaciones se guardan automáticamente y se puede mostrar el razonamiento del modelo; al regenerar o editar un mensaje se conservan todas las versiones y puedes alternar entre ellas.
- **Adjuntos**: toca "+" a la izquierda del cuadro de entrada para añadir imágenes, archivos de texto o PDF. Las imágenes van a los modelos con "Entrada de imágenes" activada y los PDF solo a servicios Anthropic y Gemini; los modelos locales aún no admiten adjuntos.
- **Cambiar de modelo**: cambia de modelo en cualquier momento durante el chat; los scripts usan el "Destino predeterminado" de la página "Modelos y servicios".
- **Gestión de modelos**: comprueba, cambia el nombre o elimina modelos en la sección "Modelos locales". Las claves API solo se guardan en el dispositivo y no se incluyen al exportar servicios.
- **Voz**: toca el micrófono para dictar, mantenlo pulsado para iniciar una conversación por voz y mantén pulsada una respuesta para elegir "Leer en voz alta"; con 3-Macaw Speech instalado, el dictado y la lectura funcionan sin conexión.
- **Exportar y compartir**: elige "Exportar como Markdown" o "Compartir como imagen" en el menú de la pantalla de chat.

### Uso en scripts

Los scripts de AutoJs6 usan este plugin a través del módulo `ai`. Sin destino, se usa el "Destino predeterminado":

```js
ai.ask('Introduce AutoJs6 in one sentence.').then((text) => {
    console.log(text);
});
```

Para fijar un modelo concreto, busca su destino con `ai.catalog()` y pasa su `id` como `target`. Este ejemplo usa el primer modelo local disponible:

```js
ai.catalog().then((catalog) => {
    let target = catalog.targets.find((item) => item.available && item.locality === 'local');
    if (!target) throw new Error('No available local model');
    return ai.ask('Reply with OK.', { target: target.id, temperature: 0.7 });
}).then((text) => console.log(text));
```

Para conversaciones de varios turnos usa `ai.session`, envía solo el nuevo mensaje en cada turno y llama a `session.close()` al terminar:

```js
ai.session({ system: 'Keep every answer short.' }).then((session) => {
    return session.ask('Remember this code: Orion.')
        .then(() => session.ask('What code should you remember?'))
        .then((text) => {
            console.log(text);
            session.close();
        }, (error) => {
            session.close();
            throw error;
        });
});
```

- `ai.chat` devuelve la respuesta completa con el uso, y `ai.stream` devuelve el texto a medida que se genera.
- `temperature`, `topK`, `topP` y `maxTokens` controlan el muestreo y la longitud de salida; si el destino admite salida estructurada, pasar `responseSchema` devuelve texto que cumple ese JSON Schema.
- Si el destino no está disponible o no está configurado, la solicitud falla directamente (por ejemplo con `TARGET_UNAVAILABLE`) y nunca cambia a otro modelo.

### Requisitos

- Android 7.0 o posterior, AutoJs6 6.8.0 o posterior.
- Los modelos locales necesitan un dispositivo de 64 bits (arm64-v8a o x86_64) y suficiente almacenamiento; la confirmación de descarga muestra la memoria mínima necesaria. Los dispositivos de 32 bits solo pueden usar servicios en línea.
- Los servicios en línea necesitan tu propia clave API, y el proveedor puede cobrar por el uso.
- El permiso de red se usa para descargar modelos, acceder a servicios en línea y actualizar la lista de modelos predefinidos; la inferencia local no usa la red. El plugin no solicita permisos de almacenamiento ni de micrófono.

### Preguntas frecuentes

**Diferencias entre modelos locales y servicios en línea**

Los modelos locales se ejecutan en el teléfono, así que las conversaciones no salen del dispositivo, pero la velocidad y la calidad dependen del teléfono. Los servicios en línea envían las conversaciones al proveedor elegido y necesitan red y una clave API.

**Motivos por los que un fallo no cambia a otro modelo**

Las solicitudes solo usan el modelo que elegiste, para que las conversaciones nunca se envíen a otro servicio sin que lo sepas. Si una solicitud falla, revisa ese modelo en "Modelos y servicios" o cambia de modelo manualmente.

**Casos en que un modelo local no funciona**

Abre el menú de ese modelo en la sección "Modelos locales" y toca "Comprobar modelo" para ver el motivo; si falta memoria, usa un modelo recomendado más pequeño.

**Casos en que otras aplicaciones piden usar la IA**

Antes de que una aplicación empaquetada con AutoJs6 y firmada por separado pueda usar la IA, 3-Stone AI te pide que lo confirmes. Una vez autorizada, puede usar los modelos locales y los servicios en línea guardados, lo que puede consumir cuota de pago; retira el acceso en "Ajustes > Privacidad y permisos > Aplicaciones autorizadas para IA".

Todas las opciones, eventos y códigos de error están en el capítulo [Inteligencia artificial (IA)](https://docs.autojs6.com/#/ai) de la documentación de AutoJs6; la privacidad y los límites se describen en la [página del proyecto](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI).
