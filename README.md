# Roque OMR — Android

Aplicación experimental de hojas de respuesta OMR para Android 8 o superior.

- Generar hojas A4 de 1 a 100 preguntas, con opciones AB, ABC o ABCD.
- Captura con cámara o carga de fotografía, lectura de marcas y calificación.
- Corrección manual de preguntas ambiguas y guardado local de resultados.
- Hoja de prueba preconfigurada de 40 preguntas, clave ABCD repetida.
- Sin conexión obligatoria, sin cuentas y sin servidor.

## Construcción
GitHub Actions > Android APK > ejecutar flujo; descargar artefacto RoqueOMR-debug-apk. En local: JDK 17, Android SDK 35, Gradle 8.9; ejecutar gradle assembleDebug.

## Alcance
La v1 lee exclusivamente hojas creadas por Roque OMR. Requiere cuatro marcas de referencia visibles y escribir manualmente el ID al escanear. El reconocimiento usa geometría y análisis de píxeles, no IA entrenada. Verificar resultados antes de utilizarlos oficialmente.
