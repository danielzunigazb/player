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
