# Copia de seguridad

Favoritas, playlists y estadísticas de escucha en un archivo JSON, para llevarlos a otro
teléfono o recuperarlos tras reinstalar. *Ajustes → Copia de seguridad → Exportar* lo guarda
donde elijas; *Importar* lo suma a lo que ya hay.

## Por qué no se guardan ids

Todo lo que Player guarda apunta a canciones por su id de MediaStore, y ese id cambia de un
teléfono a otro (y a veces al reescanear). Un archivo con ids no serviría en ningún otro lado.
Cada canción se guarda con lo que una persona diría para nombrarla (título, artista, álbum y
duración) y la importación la vuelve a buscar en la biblioteca del teléfono.

## Formato

```json
{
  "format": "player-backup",
  "version": 1,
  "exportedAt": 1790000000000,
  "app": "1.6.1",
  "favorites": [
    { "song": { "title": "De Música Ligera", "artist": "Soda Stereo", "album": "Canción Animal", "durationMs": 210000 },
      "addedAt": 1789000000000 }
  ],
  "playlists": [
    { "name": "Rock", "createdAt": 1788000000000,
      "songs": [ { "title": "…", "artist": "…", "album": "…", "durationMs": 0 } ] }
  ],
  "stats": [
    { "song": { "title": "…", "artist": "…", "album": "…", "durationMs": 0 },
      "playCount": 12, "lastPlayedAt": 1789500000000 }
  ]
}
```

- Las canciones van escritas donde se usan, sin tabla aparte: otra herramienta puede leer o
  escribir el archivo sin saber nada más. Un respaldo grande pesa unos pocos MB; Player no lee
  archivos de más de 24 MB.
- `artist`, `album` y `durationMs` pueden faltar (o `artist: null`): se toman como vacíos.
  `title` en cada canción y `name` en cada playlist son obligatorios.
- Los campos que no conoce se ignoran, así que un archivo de un Player posterior que solo
  agregó cosas se sigue leyendo. Una `version` mayor que la que conoce se rechaza en vez de
  leerse a medias.
- Las fechas son milisegundos desde 1970. Una fecha de 0 o menos no es una fecha: se usa la de hoy.

## Cómo se reconoce una canción

Con las mismas reglas del resto de Player (`TrackMatch`): no importan los acentos, las
mayúsculas ni los créditos "feat."; una cola como "(Remastered 2011)" o "- Radio Edit" no hace
otra canción; un "Remix" o un "Live" sí; la duración debe estar a unos 10 segundos; y el
artista debe coincidir en alguno de los créditos. Si hay dos copias, gana la del mismo álbum y
luego la de duración más cercana, siempre la misma.

Se busca por título tal como está escrito, no por títulos parecidos: esto devuelve *tus*
canciones, no busca otras que se le parezcan.

## Qué hace importar

Solo agrega, y es todo o nada: si algo falla a la mitad (o sales de Ajustes), no queda nada a
medias. Importar el mismo archivo otra vez no cambia nada.

- **Favoritas**: se agregan las que faltan; las que ya eran favoritas conservan su fecha.
- **Playlists**: si hay una con el mismo nombre (sin distinguir mayúsculas) se le agregan al
  final las canciones que le faltan, en el orden del archivo; si no, se crea. Una playlist de la
  que no está ninguna canción en este teléfono no se crea (sería una lista vacía de relleno).
- **Estadísticas**: cada canción se queda con el mayor conteo y la última reproducción más
  reciente de las dos (el actual y el del archivo). Nunca sube sumando, o importar dos veces
  contaría doble. Un conteo de 0 no se restaura.
- Las canciones del archivo que este teléfono no tiene no se pierden en silencio: se cuentan y
  el aviso final lo dice.

Al exportar, una canción que en ese momento no está en la biblioteca (una tarjeta SD
desmontada, o más corta que la duración mínima) no tiene título con qué guardarse: se deja
fuera y también se cuenta en el aviso.

## Qué no incluye

Ajustes, letras guardadas, etiquetas completadas por internet ni la cola actual. Las letras y
las etiquetas se vuelven a buscar solas; la cola no es algo que se lleve a otro teléfono.
