# Player

Reproductor de música personal, nativo para Android. El plan completo y su avance están en [ROADMAP.md](ROADMAP.md).

## Funciones

**Biblioteca**
- Pestañas de Canciones, Álbumes, Artistas, Carpetas y Playlists, con detalle de cada uno
- Búsqueda en toda la biblioteca, sin distinguir mayúsculas ni acentos
- Ordenar por título, artista, álbum, añadidas recientemente o duración
- Se actualiza sola cuando agregas o borras música del teléfono

**Tus datos**
- Favoritas, playlists propias (crear, renombrar, borrar, reordenar arrastrando)
- Listas automáticas: Más escuchadas, Escuchadas recientemente, Añadidas recientemente

**Reproducción**
- Música en segundo plano con controles en la notificación, la pantalla de bloqueo y los auriculares/Bluetooth
- Al abrir la app retoma la última cola, canción y posición
- Cola editable: saltar, quitar y reordenar
- Reproducir a continuación / añadir a la cola desde cualquier canción
- Temporizador de apagado (por minutos o al terminar la canción), velocidad de reproducción
- Ecualizador con presets y refuerzo de graves
- Pantalla "Reproduciendo" con el color de la carátula; desliza la carátula o el mini reproductor para cambiar de canción
- Letras sincronizadas (resaltan la línea actual; toca una línea para saltar): desde un `.lrc` junto a la canción o incrustadas en MP3/FLAC

**Widget**
- Widget de pantalla de inicio con carátula y controles; reproducir retoma la última cola aunque la app esté cerrada

**Android Auto y voz**
- Explora tu biblioteca desde el coche; elegir una canción encola su álbum o playlist
- "Pon X en Player" por voz reproduce lo que coincida

**Ajustes**
- Tema del sistema, claro, oscuro o negro puro (AMOLED), colores Material You
- Ignorar audios cortos (notas de voz, tonos)

## Diseño (v1.1 "Ember")

- **Colores de la portada**: toda la app se tiñe con el color de la canción que suena, con transición animada (se puede desactivar en Ajustes). Sin música, usa la paleta propia: coral sobre tinta violeta.
- **Tipografía propia**: Space Grotesk para títulos y Manrope para el texto (ambas con licencia SIL OFL, incluidas en `res/font`).
- **Reproductor**: fondo con la portada difuminada y resplandor de color, portada que "respira" al reproducir o pausar, barra de progreso ondulada que se aplana en pausa, botón de play que cambia de forma y corazón con rebote.
- **Inicio**: saludo según la hora, pestañas en forma de píldora, tarjeta de "Aleatorio" con degradado y carrusel de álbumes añadidos recientemente.
- **Listas**: barras animadas sobre la canción que suena y mini reproductor flotante con anillo de progreso.
- **Ícono** nuevo, basado en la barra ondulada.

## Arquitectura

Kotlin, Jetpack Compose (Material 3), MVVM, Media3/ExoPlayer, Room, Navigation Compose.

```
app/src/main/java/com/danielzuniga/player/
├── PlayerApplication.kt   AppContainer: repositorios compartidos por la UI y el servicio
├── data/                  MediaStore (MusicRepository), Room (db/, UserDataRepository), ajustes,
│                          letras (lyrics/: LRC, ID3 USLT, FLAC Vorbis)
├── playback/              PlaybackService (ExoPlayer + MediaSession), PlayerConnection,
│                          ecualizador, guardado de la cola
├── widget/                Widget de pantalla de inicio
└── ui/                    ViewModels, navegación, pantallas (library, detail, playlists,
                           player, equalizer, settings)
```

## Compilar e instalar

Requisitos: JDK 17+ y el Android SDK (API 35). Lo más fácil es abrir el proyecto en Android Studio y pulsar Run.

Desde la terminal, con el móvil conectado por USB (depuración USB activada):

```bash
./gradlew installDebug          # instala la versión debug
./gradlew assembleRelease       # APK optimizado en app/build/outputs/apk/release/
./gradlew testDebugUnitTest     # pruebas (JVM + Robolectric: UI, servicio y base de datos)
./gradlew lintDebug             # análisis estático
```

Cada push a GitHub compila, prueba y publica el APK como artefacto en la pestaña **Actions** (`player-apk`).

El build `release` se firma con la clave de debug para poder instalarlo sin configurar un keystore. Está bien para uso personal, pero no sirve para publicarlo en Play Store.

Android 8.0 (API 26) o superior.
