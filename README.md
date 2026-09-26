# Player

Reproductor de música personal, nativo para Android. El plan completo y su avance están en [ROADMAP.md](ROADMAP.md).

**Última versión estable: [v1.4.2](https://github.com/danielzunigazb/player/releases/latest)**. Descarga el APK desde [Releases](https://github.com/danielzunigazb/player/releases/latest) (Android 8.0 o superior). Web del proyecto: **[player.danzuniga.xyz](https://player.danzuniga.xyz)**.

[![Video de presentación](docs/showcase-thumb.jpg)](https://github.com/danielzunigazb/player/releases/download/showcase-v1/PlayerShowcase.mp4)
<p><sub>▶ <a href="https://github.com/danielzunigazb/player/releases/download/showcase-v1/PlayerShowcase.mp4">Video de presentación</a> (45 s): hecho con Remotion en <code>player-showcase/</code> y publicado por CI como release. GitHub no reproduce en el README un mp4 de un release, así que el enlace abre o descarga el video.</sub></p>

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
- Letras sincronizadas (resaltan la línea actual; toca una línea para saltar): desde un `.lrc` junto a la canción, incrustadas en MP3/FLAC o, si no trae, buscadas en [LRCLIB](https://lrclib.net) (gratis y sin cuenta). Solo se envían título, artista, álbum y duración; cada letra se guarda en el teléfono tras la primera descarga. Se desactiva en *Ajustes → Letras*.

**Widget**
- Widget de pantalla de inicio con carátula y controles; reproducir retoma la última cola aunque la app esté cerrada

**Android Auto y voz**
- Explora tu biblioteca desde el coche; elegir una canción encola su álbum o playlist
- "Pon X en Player" por voz reproduce lo que coincida

**Ajustes**
- Tema del sistema, claro, oscuro o negro puro (AMOLED), colores Material You
- Ignorar audios cortos (notas de voz, tonos)

## Terminal

El botón `>_` del inicio abre una consola para manejar la música escribiendo, con autocompletado sobre tu biblioteca:

```
$ play soda stereo        # artista, álbum o canción, sin importar acentos
$ play album signos       # artist / album / song para ser específico
$ queue eres              # al final de la cola · next <algo> la pone a continuación
$ shuffle                 # toda la biblioteca
$ now                     # progreso [████░░░░] y la línea de la letra que suena
$ sleep 30m · speed 1.25 · seek 1:30 · repeat · fav · top · ls · help
```

## Diseño

La interfaz usa el sistema de diseño **Daniel Zúñiga** (claude.ai/design) portado a Compose:

- **Tokens** en `ui/theme/Theme.kt` (`DzColors`): temas Obsidiana (oscuro) y Pergamino (claro), `gold` como único acento, `verdigris` y `ember` para estados. La jerarquía se construye con `bg` → `surface` → `line`, sin sombras.
- **Tipografía** en `ui/theme/Type.kt`: JetBrains Mono como voz e Instrument Serif itálica como susurro (el artista en el reproductor, "música" en el inicio). Ambas tienen licencia SIL OFL y están en `res/font`.
- **Íconos** en `ui/theme/DzIcons.kt`: los 17 trazos del sistema más los del reproductor, dibujados con las mismas reglas (grilla de 24, trazo de 1.5, puntas cuadradas, sin relleno).
- **Componentes** en `ui/components/Dz.kt`: `DzButton`, `DzIconButton`, `DzTag`, `DzTitle` (con susurro y cursor), `DzMark`, `Eyebrow` y `Hairline`.
- El **ícono de la app** es el monograma: la ñ recortada de un bloque dorado.

## Firma

Los APK de release se firman con la llave de release de Player (certificado SHA-256 `8d7449ce…4f23cf36c`). Es la misma que firmó todas las versiones desde la 1.0.0, así que cada actualización se instala encima sin perder datos. **Si se pierde esa llave, ninguna versión futura podrá actualizar la app instalada**: guárdala en un lugar seguro.

- **CI:** toma la llave de tres secrets del repo: `PLAYER_KEYSTORE_BASE64` (el `.jks` en base64), `PLAYER_KEYSTORE_PASSWORD` y `PLAYER_KEY_PASSWORD`. El alias (`player`) va como valor fijo en los workflows. El paso "Show signing certificate" imprime la huella del APK para comprobarla.
- **Local:** copia `keystore.properties.example` a `keystore.properties` (ignorado por git) y completa la ruta y las contraseñas. También sirven las variables de entorno `PLAYER_KEYSTORE_FILE`, `PLAYER_KEYSTORE_PASSWORD`, `PLAYER_KEY_ALIAS` y `PLAYER_KEY_PASSWORD`.
- **Sin llave:** el build de release se firma con la llave de debug de esa máquina y avisa. Con `PLAYER_REQUIRE_RELEASE_KEY=true` (builds de versiones estables) falla en vez de hacerlo.

## Arquitectura

Kotlin, Jetpack Compose (Material 3), MVVM, Media3/ExoPlayer, Room, Navigation Compose.

```
app/src/main/java/com/danielzuniga/player/
├── PlayerApplication.kt   AppContainer: repositorios compartidos por la UI y el servicio
├── data/                  MediaStore (MusicRepository), Room (db/, UserDataRepository), ajustes,
│                          letras (lyrics/: LRC, ID3 USLT, FLAC Vorbis, cliente LRCLIB)
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

## Versiones

Las versiones estables se publican en [Releases](https://github.com/danielzunigazb/player/releases). Para sacar una nueva:

1. Sube `versionCode` y `versionName` en `app/build.gradle.kts`.
2. Escribe las notas en `docs/releases/vX.Y.Z.md`; si no existen, GitHub las genera a partir de los commits.
3. Publica: sube el tag (`git tag vX.Y.Z && git push origin vX.Y.Z`) o, desde GitHub, **Actions → Release → Run workflow** sobre `main`, que crea el tag a partir de `versionName`.

El workflow `release.yml` comprueba que el tag coincida con `versionName`, corre tests y lint, firma con la llave de release, verifica que el certificado sea el esperado y publica el APK con su `.sha256`. El video de presentación se vuelve a renderizar solo cada vez que cambia la versión.

Android 8.0 (API 26) o superior.

## Web

[player.danzuniga.xyz](https://player.danzuniga.xyz) sale de `site/` y la publica el workflow **Site** en GitHub Pages. Se vuelve a publicar cuando cambian el sitio, las capturas o `versionName`, y después de cada release o video nuevo, así que el enlace de descarga y el video no se quedan viejos. `site/build.sh` arma la página en `_site/` para verla en local (`python3 -m http.server -d _site`); el video solo lo agrega CI.
