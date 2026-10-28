# Roque OMR v1.2.0 — Android

Lector experimental offline de hojas de respuesta OMR para Android 8 o superior.

## Flujo de cámara integrada

1. Abrir **LEER** y escribir el código del estudiante.
2. Tocar **Escanear automáticamente** (conceder permiso de cámara).
3. La vista CameraX detecta automáticamente las seis guías negras de la hoja.
4. Tras tres lecturas geométricamente estables, la imagen se congela sola.
5. La app superpone guías verdes y círculos sobre las respuestas identificadas; las dudosas se indican en ámbar.
6. Tocar **Repetir** para otra captura o **Usar lectura** para revisar y calificar. Ninguna lectura se guarda sin confirmación.

**IMPORTANTE:** Imprime las nuevas hojas PDF v1.2 que vienen adjuntas a la Release. Las hojas v1.0/v1.1 con cuadrados sólidos **no son compatibles** con el detector de la v1.2. Las seis guías nuevas son cuadrados negros de centro blanco (cuatro esquinas y dos laterales). La validación exige forma de guía, proporciones A4, posición de laterales y cabecera oscura antes de permitir autocaptura.

La vista en vivo usa CameraX ImageAnalysis con descarte de fotogramas atrasados. El reconocimiento sigue utilizando algoritmos OMR y homografía (NO una inteligencia artificial entrenada). La captura proviene del fotograma analizado y puede tener menor resolución que una foto completa.

## Otras funciones
- Crear pruebas de 1 a 100 ítems, 2–4 opciones; configurar claves y generar hoja A4 en PDF.
- Importar imagen/PDF cuando no se utiliza la cámara.
- Revisar/corregir respuestas y guardar resultados localmente.
- Exportar CSV. Prueba demo de 40 preguntas con clave ABCD repetida.
- Todo offline, sin cuentas ni servidor.

## Descargar
[GitHub Releases](https://github.com/EncisoMath/OMR/releases) incluye APK debug y dos hojas PDF de muestra.

## Compilación
GitHub Actions > Android APK; compilar localmente con JDK17, Android SDK35 y Gradle8.9 ejecutando `gradle assembleDebug`.

## Límites importantes
- Reconoce exclusivamente hojas generadas por Roque OMR, no hojas de terceros.
- Las seis marcas de referencia deben estar completas y legibles.
- El ID se introduce manualmente y el análisis de cámara puede variar con iluminación, distorsión o dispositivo.
- **Versión beta no verificada en dispositivos físicos**. Antes de utilizarla para notas oficiales, realizar pruebas con hojas impresas y comparar manualmente todas las lecturas.
