<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/app/src/main/res/mipmap/ic_launcher_ai.png?raw=true" alt="ai-text-generation-ic-launcher" border="0" width="128" />
  </p>

  <p>Plugin local de generación de texto con IA. Transmisión de texto sin formato en el dispositivo con LiteRT-LM</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Idiomas

******

El README.md actual admite los siguientes idiomas:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-fr.md)
- Español [es] # actual
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ar.md)

******

### Introducción

******

AI Text Generation es un provider independiente en el dispositivo para la versión 1 del protocolo AI Text Generation de AutoJs6. Ejecuta en CPU un modelo LiteRT-LM importado por el usuario, acepta historial de mensajes de texto sin formato y devuelve texto sin formato mediante una sesión de streaming controlada.

******

### Funciones

******

- Importar un paquete de modelo `.litertlm` con el selector del sistema Android y guardar una copia verificada en el almacenamiento privado de la aplicación.
- Crear solicitudes de generación local con historial system, user y assistant en texto sin formato.
- Entregar chunks de texto en orden con contrapresión por credits y publicar un solo estado terminal completado, fallido o cancelado.
- Enumerar el modelo importado actualmente y exponer una nueva generation de lista después de reemplazarlo.
- Funcionar completamente en el dispositivo con un backend CPU, sin descargar modelos ni llamar a un servicio de inferencia remoto.

******

### Formatos de modelo y datos

******

La versión 1 declara únicamente el siguiente alcance:

```text
model package: .litertlm
input: text/plain message history
output: streamed text/plain chunks
runtime: LiteRT-LM 0.15.0
```

******

### Interfaz del plugin

******

El host descubre y llama al plugin con las siguientes identidades:

```text
service action: org.autojs.plugin.AI_TEXT_GENERATION
plugin id: ai-text-generation
protocol provider id: autojs6.local.text
engine: ai-text-generation
variant: default
protocol: V1
required host build: 5270
```

El plugin declara ejecución ON_DEVICE y modo credential NONE. Solo declara la capacidad `streaming` y entrada y salida `text/plain`.

Se requiere la build 5270 o posterior del host. Las versiones incluyen variantes APK arm64-v8a, x86_64, universal.

******

### Estado de integración con el host

******

> El repositorio principal de AutoJs6 todavía no proporciona el AI Android adapter, el provider selector ni el runtime bridge, y el `ai.*` integrado no ha migrado a este protocolo. Instalar solo este plugin no redirige las llamadas `ai.*` existentes. El uso de extremo a extremo requiere un futuro adapter del host o su activación explícita, además de seleccionar este provider.

******

### Seguridad y privacidad

******

El plugin no solicita permisos de red ni almacenamiento. Lee el modelo solo mediante un URI concedido por el selector del sistema, calcula SHA-256 mientras lo copia al directorio privado `files/models`, ejecuta fsync y lo activa mediante reemplazo atómico del pointer en el mismo directorio. Los servicios también verifican el nombre del paquete AutoJs6, la propiedad del UID y las firmas coincidentes.

******

### Límites operativos

******

- La importación de un modelo tiene un límite estricto de 8 GiB y debe dejar al menos 256 MiB libres.
- Un coordinador de importación única con alcance de aplicación mantiene el trabajo durante la recreación de Activity. Un pending journal sincronizado con fsync permite la recuperación en arranque frío y limpia los archivos temporales stale `.incoming`, `.current` y `.pending`. La recuperación solo elimina un destination creado por el intento actual y nunca publicado mediante current metadata; se conservan las generaciones publicadas, current e históricas con nombre de hash.
- Para evitar condiciones de carrera entre procesos con el proceso aislado `:provider`, una importación de reemplazo conserva las generaciones anteriores con nombre de hash SHA-256. Estos archivos continúan ocupando almacenamiento privado de la aplicación.
- Solo una sesión de generación puede estar activa en el proceso. Los descriptores se duplican antes del trabajo asíncrono y se cierran según las cuotas del protocolo.
- El provider anuncia un máximo de contexto de 256 KiB y un máximo de salida de 64 KiB. Las solicitudes y modelos pueden imponer límites menores.
- El streaming usa credits finitos y chunks limitados para evitar buffers ilimitados o callbacks sin contrapresión.
- La cancelación, el cierre de sesión y el timeout detienen la publicación y finalizan la solicitud con un solo estado terminal.

******

### Capacidades no declaradas

******

- No se declaran reasoning, tools, structured JSON ni usage.
- No se aceptan mensajes con rol tool, schemas de herramientas, tool calls ni tool results.
- No hay descubrimiento de modelos por red, descarga, inferencia cloud ni flujo credential.
- No se declara backend GPU o NPU. La extensión `.litertlm` por sí sola no garantiza que el runtime LiteRT-LM actual pueda cargar el modelo.

******

### Hoja de ruta

******

`R0` sigue en curso porque aún no se han ejecutado las validaciones de dispositivo y compilación. `R1` cubre ahora dos tramos del host desactivados y sin conectar por defecto, sin punto de llamada en producción: PackageManager `exact-action discovery`/`exact-component reinspection` de solo lectura, y un enlace Binder metadata-only al componente explícito. Cada ruta reinspecciona los límites de identidad que realmente alcanza; la ruta exitosa alcanza como máximo tres: antes del enlace, tras la conexión y después de verificar el descriptor y decodificar de forma estricta y acotada provider info/capabilities. El deadline absoluto hace fallar el intento e ignora resultados tardíos; solo activa un fusible de proceso para el componente si vence mientras sigue ejecutándose una llamada Binder síncrona para el descriptor de interfaz, `getProviderInfo()` o `getCapabilities()`. La decodificación síncrona estricta posterior a cada getter queda fuera del periodo RPC in-flight y no activa el fusible. Los timeouts en worker queue, durante el enlace o durante la reinspección final tampoco lo activan. El fusible no puede interrumpir por la fuerza una llamada Binder ya bloqueada; todavía no hay listado de modelos/despacho de sesiones, PFD/callback de generación, integración runtime/UI ni rutas `ai.*`. `R2` a `R8` siguen planificados. El estado de las casillas se mantiene en la hoja de ruta del proyecto.

- [Ver ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/ROADMAP.md)

******

### Historial de versiones

******

# v1.0.0

###### 2026/08/08

* `Función` Provider en el dispositivo para el protocolo AI Text Generation V1 con ID y motor `ai-text-generation`, provider ID `autojs6.local.text` y variante `default`
* `Función` Generación de texto sin formato con LiteRT-LM y CPU, historial system, user y assistant y streaming controlado por credits
* `Función` Importación SAF de `.litertlm` al almacenamiento privado con límite de 8 GiB, reserva de espacio, SHA-256, fsync y activación atómica
* `Función` Una sesión activa, I/O limitada, cuotas de descriptores, cancelación, timeout, un estado terminal y verificación del llamador AutoJs6 con la misma firma
* `Función` Omisión explícita de las capacidades reasoning, tools, structured JSON, usage, red y credential
* `Función` APK arm64-v8a, x86_64 y universal con README, changelog, interfaz Android e instrucciones del plugin en 10 idiomas
* `Mejora` Conservación de generaciones anteriores con nombre de hash SHA-256 tras una importación de reemplazo para evitar carreras entre procesos con `:provider`, por lo que los archivos conservados siguen ocupando almacenamiento privado
* `Mejora` Se añadió un coordinador de importación única con alcance de aplicación y un pending journal sincronizado con fsync para mantener el trabajo al recrear Activity, recuperar en arranque frío, limpiar temporales stale y limitar el borrado a destinos creados por el intento actual y nunca publicados, mientras se conservan las generaciones publicadas, current e históricas con nombre de hash
* `Dependencia` Se añadió LiteRT-LM 0.15.0 para generación de texto con CPU en el dispositivo

##### Más versiones

* [CHANGELOG-es.md](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/app/src/main/assets/doc/CHANGELOG-es.md)

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
ai-text-generation-api.aar
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
