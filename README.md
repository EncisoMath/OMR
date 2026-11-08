# EncisoOMR — Android + Web · v0.1.0 (beta)

**Proyecto nuevo.** El código antiguo de Roque OMR fue eliminado de `main` y reemplazado por una aplicación original que utiliza exclusivamente **Supabase EncisoOMR** (`lsbwuwpvesdkwrwffjkc`). No utiliza código fuente de EvalBee ni recursos propietarios.

## Funciones de esta primera compilación

- Registro e inicio de sesión por Supabase Auth.
- Crear exámenes de hasta **100 preguntas**, con 2 a 5 opciones.
- Claves de respuestas editables y preguntas anuladas.
- Registro de estudiantes y códigos.
- Generación de hojas A4 e impresión a PDF con 4 marcadores negros.
- Cámara Android e importación de fotografías.
- Escáner OMR experimental para **las hojas EncisoOMR**; corrección manual.
- Calificación a partir de las claves; almacenamiento de respuestas y resultados.
- Fotografías comprimidas en **WebP** dentro del bucket privado `omr-scans`.
- Interfaz web común y aplicación Android basada en WebView.

## Estado

**Beta de prueba.** La lectura óptica requiere validación con papel impreso y teléfonos reales. **No usar para calificaciones oficiales sin revisar todas las respuestas.** No hay todavía paridad completa con EvalBee; faltan plantillas complejas, preguntas numéricas, importaciones masivas, trabajo compartido, reportes avanzados y sincronización offline de escritura. Las herramientas implementadas son gratuitas para los usuarios; se aplican las cuotas de infraestructura de Supabase.

Los datos solo se sincronizan cuando hay internet. Si se pierde conexión se muestran datos en caché, pero las nuevas operaciones todavía requieren conexión.

## Descargar APK

La automatización `Build EncisoOMR APK` compila el APK de prueba (Android 10+). Tras una compilación exitosa, publica `EncisoOMR-v0.1.0-debug.apk` en **GitHub Releases**. La versión debug no es una firma de distribución.

## Compilar

Gradle 8.9 + JDK 17 + Android SDK 35:

```bash
gradle :app:assembleDebug
```

La interfaz está en `web/`. Android la carga como asset local; un puente Java realiza peticiones HTTPS a Supabase. En el navegador se emplea `fetch`.

## Base de datos

`supabase/sql/initial_schema.sql` representa el esquema inicial aplicado al proyecto **EncisoOMR**. Incluye RLS privada por propietario y bucket `omr-scans` de imágenes WebP. No publicar claves `service_role` ni fotografías personales en GitHub.

## Cambios respecto al proyecto anterior

Solo existe código fuente nuevo en la rama principal. Se eliminó todo el árbol Java/Kotlin previo (`com.encisomath.roqueomr`), sus scripts y su flujo de construcción. La rama de archivo `archive/roque-omr-v1.2.0` conserva una copia histórica independiente.
