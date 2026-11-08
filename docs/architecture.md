# Arquitectura planeada · EncisoOMR

**Backend único**: Supabase proyecto `lsbwuwpvesdkwrwffjkc`. Nunca escribir en RoqueObjetiva desde este proyecto.

## Datos iniciales

- `omr_workspaces`: espacios de trabajo privados por usuario.
- `omr_exams`: exámenes y configuración extensible.
- `omr_students`: estudiantes y código externo opcional.
- `omr_answer_keys`: respuestas correctas, puntos, penalizaciones y anulaciones.
- `omr_scans`: imágenes, respuestas detectadas, respuestas revisadas, resultado.
- `omr_sync_events`: idempotencia por dispositivo + ID de evento local.

Todos los registros iniciales tienen `owner_id` obligatorio. Las políticas de acceso restringen lectura, inserción, modificación y borrado al propietario autenticado. **No** hay todavía acceso de docentes colaboradores, ni debe suponerse.

## Imágenes de escaneos

Bucket privado `omr-scans`, solo `image/webp`, límite inicial de 3 MiB por imagen. Ruta sugerida: `<auth.uid()>/<exam_uuid>/<scan_uuid>.webp`.

Pipeline objetivo: foto original → detección A4/esquinas → normalización geométrica → reconocimiento OMR y confianza → pantalla de revisión → archivo WebP → cola local → subida a Storage → escritura de resultados. Los errores de red no deben afectar lecturas guardadas en el dispositivo. El límite de tamaño del bucket podrá ajustarse tras ensayos.

## Próximas migraciones

1. Perfiles, preferencias, política de retención y soft-delete controlado.
2. Versión de plantillas, secciones, grupos, baterías de preguntas y respuestas alternativas.
3. Membresías entre docentes con políticas RLS por rol y organización.
4. Auditoría de correcciones, cambios de respuestas y exportaciones.
5. Pruebas RLS de acceso cruzado, carga WebP, reconexión e idempotencia de sincronización.

## Validación requerida

- Muestras de hojas impresas con bolígrafo, lápiz, marcas débiles y dobles.
- Comparar puntuación OMR con lectura humana; no introducir notas oficiales sin validación.
- Comprobar tamaño y legibilidad de WebP en teléfonos de gama baja.
- Ejecutar pruebas automatizadas de RLS con dos cuentas, incluyendo URLs temporales del bucket.
