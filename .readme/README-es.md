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

`R0` sigue en curso. Las validaciones de compilación del 2026-08-10 se superaron: 32 tests/0 fallos, lint 0 errores, Debug/Release `BUILD SUCCESSFUL` en 3m37s, con `VERSION_BUILD`/`BUILD_TIME` sin cambios; las pruebas en dispositivo con modelo real, clipboard y script de ejemplo siguen pendientes. `R1` cubre ahora cinco tramos del host desactivados y sin conectar por defecto, sin punto de llamada en producción: PackageManager `exact-action discovery`/`exact-component reinspection` de solo lectura, un enlace Binder metadata-only al componente explícito, una policy/catalog independiente del transporte para el transcript del listado de modelos y un coordinator Android con transporte Binder `IAiModelListCallback`. El handshake metadata heredado reinspecciona cada límite de identidad alcanzado y solo activa el fuse si el deadline absoluto vence mientras aún se ejecuta la RPC Binder síncrona del interface descriptor, `getProviderInfo()` o `getCapabilities()`. La policy model-list decodifica estrictamente resultados acotados de una o varias páginas y errores del provider, rechaza cambios de generation, repetición/ciclos de token, ID de modelo duplicados, incompatibilidades de capacidad y callbacks obsoletos/duplicados, y garantiza un único estado terminal ganador. Solo durante la inicialización del listing, el coordinator revalida provider metadata sobre el mismo Binder cuyo descriptor ya fue verificado; después usa `AiTextProviderPackageSnapshot.samePackageIdentityAs` para reinspeccionar exactamente la identidad package/component antes de cada dispatch inicial o de continuación. Cada callback valida el UID invocador, el tamaño del typed page/error envelope y un flood slot acotado antes de la única copia del payload; un page-token ledger acotado queda aislado por cada pinned identity completa y estable. A diferencia del handshake heredado, el coordinator conserva el watchdog para cualquier `operationsInFlight` admitida, incluidos exact PackageManager inspect, bind, prepare, dispatch y callback admission, y activa el fuse del exact component si vence el deadline antes del unwind. Ningún fuse puede interrumpir una operación bloqueada; el gate del coordinator permanece `BUSY` hasta el unwind tardío. El quinto tramo es `AiTextProviderSessionPolicy`, desactivado por defecto, sin conectar e independiente del transporte: fija plan/request/provider/model/context, retiene tras un commit explícito los callbacks recibidos antes del retorno síncrono de `openSession`, valida UID y después límites de envelopes tipados y ownership de descriptors, gestiona 8 credits iniciales y backpressure acotado por chunk, admite started/chunk/usage/completed/failed/cancelled con un único terminal ganador entre finalización del provider, cancel, timeout y death, espera el settlement de descriptors, control remoto y cleanup antes de publicarlo, y rechaza tools en modo fail-closed. No incluye adapter Android `IAiTextCallback`/`openSession`/PFD, reinspection del package antes de session dispatch, ni dispatch real, integración runtime/UI o rutas `ai.*`. Su evidencia ahora incluye código/JVM estática y Gradle JVM focused: compilación standalone Kotlin 2.3.21 K2/JDK 21/JVM 17, 40/40 JUnit y 1200/1200 en 30 runs; focused AutoJs6 Gradle `:app:testAppDebugUnitTest` terminó con exit 0 y su XML registró 40 tests, 0 skipped, 0 failures y 0 errors, mientras la compilación siguió teniendo éxito mediante fallback tras un reintento del Kotlin daemon. Sigue sin incluir adapter Android `IAiTextCallback`/`openSession`/PFD, evidencia ADB/device, runtime/UI ni `ai.*`; los elementos R1 generales y los gates de salida siguen sin marcar. El gate Gradle aislado de AutoJs6 del 2026-08-10 pasó coordinator 15/0 y ensambló host Debug, androidTest y fake APK sin cambiar la metadata de versión. En QV710AF65F (API 31, arm64-v8a), metadata y model-list obtuvieron `OK (1 test)` cada uno como evidencia positiva PARTIAL, con cuatro páginas/cuatro modelos fake y `pageSize=1`; signer/hash, identidad anterior/posterior, callback fuera de mai…6436 tokens truncated…atalog и Android model-list coordinator с Binder transport `IAiModelListCallback`. Старый metadata handshake повторно проверяет достигнутые границы identity и включает fuse только при истечении absolute deadline, когда синхронный Binder RPC для interface descriptor, `getProviderInfo()` или `getCapabilities()` все еще выполняется. Model-list policy строго декодирует ограниченные одно- и многостраничные результаты и ошибки provider, отклоняет изменение generation, replay/cycles token, дублирующиеся model ID, несовместимость capabilities и устаревшие/повторные callbacks, а также допускает только одно победившее terminal state. Только при инициализации listing coordinator повторно проверяет provider metadata через тот же Binder с проверенным descriptor; затем перед каждым начальным или continuation dispatch он использует `AiTextProviderPackageSnapshot.samePackageIdentityAs` для точной проверки identity package/component. Каждый callback проверяет calling UID, размер typed page/error envelope и ограниченный flood slot до единственного копирования payload; ограниченный page-token ledger изолирован для каждой полной стабильной pinned identity. В отличие от старого handshake coordinator сохраняет watchdog для любых принятых `operationsInFlight`, включая exact PackageManager inspect, bind, prepare, dispatch и callback admission, и включает fuse для exact component, если к deadline не произошел unwind. Ни один fuse не может принудительно прервать застрявшую operation; gate coordinator остается `BUSY` до позднего unwind. Этот срез не включает session/`openSession`, PFD, credit, `IAiTextCallback`, session dispatch, интеграцию runtime/UI или маршрутизацию `ai.*`. Изолированный AutoJs6 Gradle gate от 2026-08-10 прошел coordinator 15/0 и собрал host Debug, androidTest и fake APK без изменения version metadata. На QV710AF65F (API 31, arm64-v8a) metadata и model-list дали по `OK (1 test)` как положительное PARTIAL-доказательство, собрав четыре страницы/четыре fake model при `pageSize=1`; signer/hash, identity до/после, callback вне main и единственный terminal подробно записаны в host evidence. Все три package отсутствовали до установки и снова отсутствовали после cleanup. Это подтверждает только узкий R1 item; общие пункты и exit gates остаются неотмеченными. На QV710AF65F не было реального plugin/model, поэтому R0 остается открытым. `R2`-`R8` остаются в плане. El sexto tramo estrecho añade un coordinator Android de sesión por exact-component con transporte `IAiTextCallback`/PFD, desactivado y sin conectar por defecto. El standalone K2 pasó 18/18 y el mismo artefacto 540/540 en 30 rondas; el Gradle focused registró 18 tests/0 failures y se ensamblaron los tres APK. En QV710AF65F (API 31, arm64-v8a), los dos métodos dieron cada uno `OK (1 test)`: request mediante reliable-pipe PFD y unos 18 chunks que cruzaron los 8 credits iniciales, además de descriptor exact/short/trailing/reliable producer error e idempotent close. La evidencia es solo `PARTIAL`: siguen faltando callback completion/tool PFD entre procesos, wrong UID, hostile death/update, plugin/model reales, runtime/UI y `ai.*`; por ello los elementos R1 generales y los gates de salida permanecen sin marcar. Un tramo estrecho posterior y marcado de conformidad hostile Android session queda registrado por los commits H1 aislados `edd10008f`/`06ebc788c` y los commits integrados `0cbc19d9f`/`72eb4d0c0`. El Gradle focused superó las suites coordinator 18/0 y fake provider 31/0 y ensambló los tres APK. En QV710AF65F (API 31, arm64-v8a), siete métodos instrumentation exactos devolvieron cada uno `OK (1 test)`: un callback completion mediante ordinary pipe validó la transferencia entre procesos del ownership PFD, exact length/EOF, SHA-256, la materialización UTF-8 y el cleanup; un tool PFD se tomó en propiedad y después se rechazó una sola vez con `TOOLS_UNSUPPORTED`; chunk-before-start, sequence-gap y una referencia descriptor inválida fallaron en modo fail-closed; duplicate terminal quedó acotado a un smoke con un solo terminal; y una sesión bloqueada se canceló después de `Started`, con reutilización de owner/gate. Este elemento `[x]` sigue siendo estrecho: no cubre cross-process reliable-pipe status, wrong UID, no-credit, provider death, package update/uninstall, plugin/model reales, runtime/UI ni `ai.*`. Los elementos R1 generales y los gates de salida siguen sin marcar. Consulte la hoja de ruta del proyecto para conocer el estado de las casillas.

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
