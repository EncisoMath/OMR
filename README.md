# EncisoOMR

Aplicación Android y plataforma web de lectura óptica de respuestas (OMR), con código propio y sincronización mediante **Supabase**. Objetivo: cubrir de forma gratuita las funciones principales de un sistema de evaluación OMR completo, sin suscripciones ni anuncios obligatorios.

> Estado: **inicio de migración**. El código Android existente en `main` todavía es Roque OMR v1.2.0, un prototipo local. **No está conectado aún a Supabase**. No se debe considerar lista la nueva aplicación ni un escáner verificado para notas oficiales.

## Proyectos de trabajo

- Repositorio: `EncisoMath/OMR`
- Supabase exclusivamente: **EncisoOMR**, ref `lsbwuwpvesdkwrwffjkc`
- URL del proyecto: `https://lsbwuwpvesdkwrwffjkc.supabase.co`
- Respaldo del prototipo Android anterior: rama `archive/roque-omr-v1.2.0`

## Estado implementado

- [x] Proyecto de Supabase separado y acceso confirmado
- [x] Base de datos inicial: espacios personales, exámenes, estudiantes, clave de respuestas, escaneos y eventos de sincronización
- [x] RLS habilitado en las tablas iniciales; acceso de propietario por cuenta
- [x] Bucket privado `omr-scans` con tipo MIME `image/webp`, máximo de 3 MiB por imagen, rutas que comienzan por UID
- [x] Migración reproducible en `supabase/sql/initial_schema.sql`
- [ ] Autenticación Android y web
- [ ] Lectura/escritura a través de Supabase desde Android
- [ ] Captura original, escaneo OMR, compresión WebP posterior, cola offline y sincronización
- [ ] Editor completo de exámenes y plantillas de respuesta
- [ ] Importación, exportación, análisis, reportes y colaboración docente
- [ ] Interfaz web completa
- [ ] Compilación y prueba del nuevo APK

## Arquitectura

`Android (Kotlin, CameraX, OMR local, cola offline)` + `Web` → `Supabase Auth + PostgreSQL con RLS + Storage privado`.

Se conservará la máxima precisión posible: el algoritmo de detección trabaja con la imagen original, **antes** de la compresión a WebP. Un escaneo se considera válido solo después de revisar marcas dudosas. Las imágenes y los resultados se sincronizarán por separado para evitar pérdidas de datos.

Los usuarios tendrán todas las capacidades implementadas disponibles sin suscripción. El backend **no** tiene cuota ilimitada: los límites y costos de infraestructura siguen aplicando.

## Seguridad y privacidad

- No publicar contraseñas, claves secretas ni claves `service_role` en GitHub o en las aplicaciones.
- Las tablas usan RLS y el bucket `omr-scans` es privado.
- Los nombres de estudiantes, imágenes de hojas y resultados son datos sensibles; no deben cargarse en repositorios públicos.
- Antes de permitir organizaciones y docentes colaboradores debe añadirse un sistema de membresías y políticas de autorización verificadas.
- Para cumplir con las normas de privacidad aplicables a menores, definir retención y eliminación de fotografías, y controles de acceso.

## Prototipo anterior

El código fuente original del lector Android sigue disponible en `archive/roque-omr-v1.2.0`. Detectaba hojas propias con seis marcas y generaba PDF; está sin validar en distintos teléfonos. Se evaluará qué algoritmos aprovechar con implementación propia.

## Licencias y referencia de producto

La aplicación se desarrollará con código original. La referencia de experiencia puede ser EvalBee 5.8.4, pero no se incorporan código, marcas ni recursos visuales protegidos de terceros.
