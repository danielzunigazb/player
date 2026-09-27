# Roadmap

Cada fase se entrega compilando, con pruebas y lint en verde, en su propio commit.

## Fase 0 — MVP ✅
- [x] Escaneo de MediaStore, búsqueda sin acentos
- [x] Reproducción en `MediaSessionService` (notificación, lockscreen, auriculares)
- [x] Mini reproductor y pantalla "Reproduciendo"

## Fase 1 — Biblioteca y navegación ✅
- [x] Pestañas: Canciones · Álbumes · Artistas · Playlists (Navigation Compose)
- [x] Detalle de álbum (orden por pista) y de artista (álbumes + canciones)
- [x] Ordenar canciones: título, artista, álbum, recientes, duración (se recuerda)
- [x] Menú por canción: reproducir a continuación, añadir a la cola, ir al álbum / artista
- [x] Recarga automática cuando cambia la música del dispositivo (ContentObserver)
- [x] Estado compartido vía `AppContainer` y ViewModels por pantalla

## Fase 2 — Tus datos (Room) ✅
- [x] Favoritos (corazón en la lista y en "Reproduciendo")
- [x] Playlists: crear, renombrar, borrar, añadir canciones, quitar, reordenar arrastrando
- [x] Estadísticas de escucha y listas inteligentes: Favoritas, Más escuchadas, Recién añadidas, Escuchadas recientemente

## Fase 3 — Motor de reproducción ✅
- [x] Reanudar la última cola, canción y posición al abrir la app
- [x] Reanudación desde los controles del sistema (Android 13+)
- [x] Temporizador de apagado (minutos o "al terminar la canción")
- [x] Velocidad de reproducción
- [x] Ecualizador con presets + refuerzo de graves (se recuerda)

## Fase 4 — Pulido ✅
- [x] Cola editable: saltar, quitar, reordenar arrastrando
- [x] Fondo de "Reproduciendo" con el color de la carátula
- [x] Deslizar el mini reproductor para cambiar de canción
- [x] Ajustes: tema (sistema / claro / oscuro / negro puro), duración mínima de audios

## Fase 5 — Verificación sin dispositivo ✅
- [x] Pruebas de interfaz con Robolectric: la app arranca con el servicio real, reproduce, abre "Reproduciendo", favoritos, navegación y búsqueda
- [x] Pruebas del servicio: cola, temporizador, aleatorio/repetir y reanudación de la última cola
- [x] Pruebas de la base de datos (favoritos, playlists, estadísticas) con Room en memoria
- [x] Arreglos encontrados: carátula que empujaba los controles fuera de pantalla en pantallas bajas; foco del teclado pedido antes de tiempo en diálogos

## Fase 6 — Más formas de explorar ✅
- [x] Explorador por carpetas (pestaña Carpetas)
- [x] Letras: archivo `.lrc` junto a la canción (cuando Android permite leerlo) o letra incrustada en MP3 (ID3 `USLT`) y FLAC (`LYRICS`)
- [x] Letra sincronizada: resalta la línea actual y tocar una línea salta a ese momento

## Fase 7 — Fuera del teléfono ✅
- [x] Android Auto: explorar Canciones, Álbumes, Artistas y Playlists desde el coche; al elegir una canción se encola su álbum/lista
- [x] Búsqueda por voz/texto ("pon X en Player") desde Android Auto y el asistente
- [x] GitHub Actions: compila, prueba y deja el APK descargable en cada push

## Fase 8 — Widget ✅
- [x] Widget de pantalla de inicio: carátula, título, artista y botones anterior / reproducir-pausar / siguiente (reproducir retoma la última cola aunque la app esté cerrada)

## Ideas futuras
- Scrobbling a Last.fm (requiere tu API key)

## v1.4.2 — primera versión estable ✅

Llave de release propia, releases automáticos por tag (`release.yml`), revisión de seguridad del código completo sin hallazgos y video de presentación publicado por CI.

## v1.5.0 — idiomas y correcciones ✅
- [x] Inglés y español completos, incluida la terminal; selector en Ajustes → Idioma
- [x] Compartir canciones y letras como imagen 9:16 para historias (menú, Reproduciendo, selección de líneas, `share` en la terminal)
- [x] Aviso de actualizaciones desde los releases de GitHub
- [x] Canciones sin etiquetas: artista y título confirmados en LRCLIB por nombre de archivo y duración, sin tocar los archivos y reversibles
- [x] Búsqueda: el cursor ya no se queda atrás al escribir rápido
- [x] Aleatorio: la cola mezclada empieza por la canción actual y se reproduce completa
- [x] Aleatorio: "Reproducir a continuación" suena a continuación y "Añadir a la cola" va al final
- [x] Letras: las canciones sin etiqueta de artista no se buscan en LRCLIB con "Artista desconocido"

## v1.5.1: madurez ✅

Nada de funciones nuevas: todo lo que ya existe tiene que ser sólido, y el proyecto tiene que mantenerse solo.

**Robustez** (de una auditoría del código completo)
- [x] Letras: una respuesta de LRCLIB que no es JSON (portal cautivo, mantenimiento) tumbaba la app
- [x] Playlists: quitar o reordenar borraba para siempre las canciones ocultas en ese momento (SD desmontada, filtro de duración)
- [x] Widget: al saltar rápido podía quedarse mostrando la canción anterior
- [x] Conexión con el reproductor: si fallaba, la app se cerraba en vez de quedar desconectada
- [x] Android Auto: artistas con `|` en el nombre rompían la cola y la búsqueda de elementos
- [x] Terminal: "nada sonando." sin traducir en tres comandos
- [x] Rendimiento: índice de la biblioteca, sugerencias de la terminal y expresiones regulares fuera del hilo principal o precalculadas

**Mantenimiento solo**
- [x] CI de PRs: si cambia `versionName`, exige `versionCode` mayor y `docs/releases/vX.Y.Z.md`
- [x] Cada release estable adjunta `CHANGELOG.md`, generado desde las notas por `scripts/`
- [x] Dependabot semanal para Gradle, npm y Actions

## v1.5.2: mantenimiento de la cadena de herramientas

Los PR de Dependabot dependían unos de otros: van juntos en uno propio.
- [x] AGP 8.9 → 8.13 y compileSdk 36 (targetSdk sigue en 35)
- [x] Kotlin 2.1 → 2.4 con su plugin de Compose y KSP 2.3 (#15)
- [x] AndroidX: Media3 1.11, Room 2.8, AppCompat 1.8, androidx.test (#16), con las APIs nuevas de Media3
- [x] reorderable 2.4 → 3.1 (#18)
- [ ] Probar a mano en el teléfono: reproducir, arrastrar en playlists y en la cola, Android Auto
- [ ] AGP 9, Compose BOM 2026 y ESLint 10: saltos mayores en pausa en Dependabot hasta hacerlos a propósito

## v1.6.0: monitor web (en curso)

El dueño decidió empezarlo antes de cerrar la 1.5.2. Diseño en `docs/monitor.md`.
- [x] Relay en Cloudflare (`relay/`): salas, límites, presencia; probado en local
- [x] Cifrado de punta a punta, el mismo vector comprobado en la app y en el monitor
- [x] App: vinculación por QR (App Link), conexión al relay mientras suena, comandos y estado
- [x] Monitor (`site/monitor/`): vincular, qué suena, controles, volumen, cola y búsqueda
- [ ] Desplegar el relay en la cuenta de Cloudflare del dueño
- [ ] Probar en el teléfono: vincular con la cámara, controlar desde la PC, reconexión en datos móviles
- [ ] Escena del video y notas de la versión

## Más adelante: ecosistema personal (no empezar antes de cerrar 1.5.x)

Ideas para cuando 1.5.x esté cerrada; no son compromisos:
- Exportar e importar playlists, favoritas y estadísticas (JSON y M3U)
- Sincronizar esos datos entre dispositivos sin servidor propio
- Editor de metadatos y vista de estadísticas de escucha

