# Player

Reproductor de música personal, nativo para Android.

- **Kotlin + Jetpack Compose** (Material 3, colores dinámicos en Android 12+)
- **MVVM**: `MusicViewModel` → `MusicRepository` (MediaStore) y `PlayerConnection` (MediaController)
- **Media3 / ExoPlayer** en un `MediaSessionService`: la música sigue sonando en segundo plano, con controles en la notificación, la pantalla de bloqueo y los auriculares/Bluetooth.

## Funciones

- Escanea las canciones del dispositivo (ignora audios de menos de 10 s)
- Búsqueda por título, artista o álbum, sin distinguir mayúsculas ni acentos
- Reproducir, pausar, siguiente, anterior, barra para adelantar, aleatorio y repetir (todo / una)
- Mini reproductor abajo y pantalla completa de "Reproduciendo"
- Pausa automática al desconectar los auriculares

## Estructura

```
app/src/main/java/com/danielzuniga/player/
├── MainActivity.kt
├── data/        Song, SongSearch, MusicRepository (consulta a MediaStore)
├── playback/    PlaybackService (ExoPlayer + MediaSession), PlayerConnection
└── ui/          MusicViewModel, PlayerApp, library/, player/, components/, theme/
```

## Compilar e instalar

Requisitos: JDK 17+ y el Android SDK (API 35). Lo más fácil es abrir el proyecto en Android Studio y pulsar Run.

Desde la terminal, con el móvil conectado por USB (depuración USB activada):

```bash
./gradlew installDebug          # instala la versión debug
./gradlew assembleRelease       # APK optimizado en app/build/outputs/apk/release/
./gradlew testDebugUnitTest     # pruebas unitarias
```

El build `release` se firma con la clave de debug para poder instalarlo sin configurar un keystore. Está bien para uso personal, pero no sirve para publicarlo en Play Store.

Android 8.0 (API 26) o superior.
