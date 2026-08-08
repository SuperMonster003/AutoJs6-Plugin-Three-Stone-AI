# Generación de texto con IA de AutoJs6

Este plugin importa un paquete de modelo `.litertlm` local mediante Android Storage Access Framework (SAF), lo copia al almacenamiento privado de la aplicación y ejecuta generación LiteRT-LM solo con CPU, historial de texto sin formato y salida de texto por streaming.

El plugin requiere la build 5270 o posterior del host AutoJs6 y Android API 24 o posterior.

Seguridad y límites operativos:

- La importación de un modelo se limita a 8 GiB y debe dejar al menos 256 MiB libres.
- El contexto se limita a 256 KiB, la salida a 64 KiB y solo puede estar activa una sesión de generación.
- El provider no declara un máximo por cantidad de tokens. Por ello no se admiten solicitudes que definan `maximumOutputTokens`.
- Solo se declaran streaming y `text/plain`. No se admiten reasoning, tools, structured JSON ni usage.
- El plugin no solicita permisos de red ni almacenamiento.
- Solo el host AutoJs6 con la misma firma puede enlazar el servicio provider.
- Las generaciones anteriores con nombre de hash SHA-256 se conservan por seguridad entre procesos y siguen ocupando almacenamiento privado.
