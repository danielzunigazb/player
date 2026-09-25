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

## Fase 5 — Verificación sin dispositivo
- [ ] Pruebas de interfaz con Robolectric: la app arranca, se ven las pestañas, abre "Reproduciendo"
- [ ] Pruebas de la base de datos (favoritos, playlists, estadísticas) con Room en memoria

## Fase 6 — Más formas de explorar
- [ ] Explorador por carpetas
- [ ] Letras sincronizadas desde archivos `.lrc` junto a la canción

## Ideas futuras
- Widget de pantalla de inicio, Android Auto, scrobbling a Last.fm
