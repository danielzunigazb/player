# Monitor web

Controlar Player desde el navegador de la PC mientras el teléfono suena lejos: ver qué suena,
pausar, saltar, adelantar, buscar en la biblioteca y armar la cola.

## Cómo se ven entre sí

Ni el navegador ni el teléfono pueden recibir conexiones de internet, así que los dos se
conectan a un **relay** en Cloudflare (un Worker con un Durable Object por sala) que junta las
dos conexiones y pasa los mensajes. El relay es un tubo: no sabe de música ni puede leer nada.

```
navegador ──wss──▶ relay (Cloudflare) ◀──wss── Player (solo mientras está activo)
   player.danzuniga.xyz/monitor                 sala del teléfono
```

## Vinculación (como WhatsApp Web)

1. El monitor crea una sala temporal `P` y una clave `kP` y muestra un QR con
   `https://player.danzuniga.xyz/pair#r=P&k=kP`. El fragmento (`#…`) nunca sale del navegador
   ni del teléfono: el relay no lo ve.
2. La cámara del teléfono abre el enlace en Player (App Link). Player entra a `P` y manda,
   cifrado con `kP`, la sala `R` y la clave `K` del teléfono y su nombre.
3. El monitor guarda `R` y `K`, deja `P` y se conecta a `R`. Desde entonces conecta solo.

Cada teléfono tiene una sola sala `R` con su clave `K`; todos los navegadores vinculados la
comparten. "Desvincular todo" en Player genera `R` y `K` nuevas: los navegadores viejos se
quedan hablando solos.

## Relay

`wss://<relay>/room/<sala>?role=phone|web`

- `<sala>`: 22 caracteres base64url (128 bits al azar). Otro formato se rechaza.
- Un teléfono y hasta 8 navegadores por sala. Un teléfono nuevo reemplaza al anterior.
- Pasa cada mensaje del teléfono a todos los navegadores, y de cada navegador al teléfono.
- Mensajes de hasta 64 KB y 30 por segundo por conexión; lo que pase de ahí se descarta.
- Él mismo solo dice quién está: `{"relay":"peers","phone":true,"webs":2}`, en claro, a todos,
  cada vez que alguien entra o sale.
- No guarda nada. Con las conexiones quietas, el Durable Object hiberna y no cuesta.

## Cifrado

AES-256-GCM con la clave de la sala. En el cable, cada mensaje es texto base64url de
`iv (12 bytes) || cifrado+tag`. Dentro va JSON:

```json
{ "from": "<id del emisor>", "seq": 41, "ts": 1790000000000, "type": "…", … }
```

- `from`: id al azar de cada conexión (el teléfono usa `phone`).
- `seq`: sube de a uno por emisor; el receptor descarta lo que no sea mayor al último visto.
- `ts`: hora del emisor; se descarta lo que tenga más de 2 minutos de diferencia.
- Un mensaje que no se descifra (otra clave, alterado) se ignora sin más.

## Mensajes

Del teléfono:

| type | contenido |
|---|---|
| `state` | Estado completo, en cada cambio y cada 30 s mientras suena: `song` (id, title, artist, album, durationMs), `playing`, `positionMs` medido en `ts`, `shuffle`, `repeat` (`off`/`all`/`one`), `index`, `queue` (id, title, artist), `volume` (0–1). |
| `art` | Portada de la canción actual: `songId`, `jpeg` (base64, 300 px). Solo cuando cambia. |
| `results` | Respuesta a `search`: `query`, `songs` (hasta 50: id, title, artist, album, durationMs). |
| `welcome` | Solo en la sala temporal: `room`, `key`, `name`. |

Del navegador (`cmd`, con `op`):

`play`, `pause`, `next`, `previous`, `seek` (`positionMs`), `shuffle` (`on`),
`repeat` (`mode`), `volume` (`value`), `skipTo` (`index`), `remove` (`index`),
`move` (`from`, `to`), `search` (`query`), `playSongs` (`ids`, `index`), `playNext` (`ids`),
`addToQueue` (`ids`), `hello` (pide un `state` y un `art` ya).

## Del lado del teléfono

- Solo se conecta mientras el servicio de reproducción vive (sonando o pausado con la
  notificación) y si el monitor está activado. Sin nada vinculado, no se conecta.
- Reconecta sola, con espera creciente, y al volver la red.
- Los comandos entran por el mismo reproductor que usan la notificación, el widget y Android
  Auto; la búsqueda usa la misma que la app. La biblioteca nunca sale entera del teléfono.
