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
2. La cámara del teléfono abre el enlace en Player (App Link; o el botón "Abrir en Player" de
   `/pair`, que pasa `P` y `kP` como extras del intent). Player muestra "Vincular un navegador"
   con un **código** de 6 caracteres que genera él mismo al azar (`SecureRandom`, alfabeto sin
   parecidos: `A–Z` y `2–9` sin `0/O/1/I/L`), para escribirlo en el monitor. Entra a `P` y le
   pide el código (`askCode`); el monitor muestra un campo para escribirlo y lo manda (`code`,
   en mayúsculas y sin espacios).
3. Player compara el código en tiempo constante. Si es el suyo, manda, cifrado con `kP`, la sala
   `R` y la clave `K` del teléfono y su nombre (`welcome`), y espera que el monitor confirme
   (`paired`). Si no, contesta `wrongCode` con los intentos que quedan; al tercero equivocado
   manda `pairFailed` y deja `P`. Nada de `R` ni `K` sale antes del código correcto. Los
   intentos cuentan para toda la vinculación: reconectar no da más. Si el navegador reconecta
   antes del código se le vuelve a pedir; después, recibe `welcome` otra vez. Un `code` que llega
   después del correcto se ignora.
4. El monitor guarda `R` y `K`, deja `P` y se conecta a `R`. Desde entonces conecta solo.

Por qué el teléfono muestra el código y la persona lo escribe en la computadora (y no al revés):
quien fabrique un enlace de vinculación y se lo haga abrir a alguien tiene `P` y `kP`, pero no
ve la pantalla del teléfono. Con 31⁶ códigos posibles y 3 intentos, adivinar no sirve. El
monitor valida contra ese alfabeto antes de mandar: un `0` por una `O` recibe una pista, no gasta
un intento.

Del lado del teléfono (`BrowserPairing`, a nivel de app): el diálogo pide escribir el código
solo en `player.danzuniga.xyz/monitor`, en la computadora, y no decírselo a nadie. Sigue abierto
mientras espera, y sobrevive a una rotación con el mismo código. Se cierra al vincular, con
Cancelar (deja `P` sin decir nada más), a los 2 minutos o tras 3 códigos equivocados (con un
aviso). Otro enlace de vinculación que llegue mientras hay uno pendiente se ignora: no cambia el
navegador detrás del código en pantalla.

En cuanto sale `welcome` la vinculación está hecha, confirme o no el navegador: `paired` no
agrega seguridad, y una página que recibió la sala y nunca confirma la tiene igual. Por eso,
desde ahí, ni el plazo ni Cancelar la deshacen: el monitor se activa y el teléfono avisa
"Navegador vinculado. Si no fuiste tú, usa Desvincular todos los navegadores en Ajustes."

### Riesgo que queda

El código es secreto frente a quien tiene la sala temporal del QR, no frente a la persona. Una
página parecida que muestre su propio QR, o alguien que pida el código ("dime los 6 caracteres
que te salen"), puede lograr una vinculación si la persona lo escribe ahí o lo dice. Lo que lo
mitiga: el aviso del diálogo (dónde escribirlo, no decírselo a nadie), el monitor muestra su
propia dirección junto al campo para compararla, el aviso al vincular hace notar una
vinculación inesperada, y "Desvincular todos los navegadores" la corta (sala y clave nuevas).

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
  cada vez que alguien entra o sale. Como solo él habla en JSON en claro, descarta el texto de
  un cliente que empiece con `{`: así nadie con la sala pero sin la clave se hace pasar por él
  (por ejemplo, diciéndole al teléfono que no hay navegadores para que deje de mandar).
- Esas decisiones (quién entra, qué pasa) son funciones puras en `relay/src/room.js`, con
  pruebas en `relay/test/`.
- No guarda nada. Con las conexiones quietas, el Durable Object hiberna y no cuesta.

## Cifrado

AES-256-GCM con la clave de la sala. En el cable, cada mensaje es texto base64url de
`iv (12 bytes) || cifrado+tag`. Dentro va JSON:

```json
{ "from": "<id del emisor>", "seq": 41, "ts": 1790000000000, "type": "…", … }
```

- `from`: id al azar de cada conexión: `web-<8 caracteres>` en el navegador,
  `phone-<8 caracteres>` en el teléfono, nuevo cada vez que el teléfono arma su conexión (al
  reiniciar la app o el servicio). Si el teléfono usara siempre el mismo id, una pestaña abierta
  que ya vio su `seq` 500 ignoraría al teléfono reiniciado, que vuelve a contar desde 1.
- `to`: solo en los `cmd`, el id del teléfono al que va (el `from` del último mensaje que el
  navegador recibió de él). El teléfono ignora los `cmd` con otro `to` o sin él, salvo `hello`,
  que solo pide el estado y pasa siempre: puede salir antes de que el navegador oiga al
  teléfono. Así, comandos capturados antes de que el teléfono reiniciara (su `Inbox` nuevo no
  los recuerda) no le sirven a un relay malicioso para repetirlos. El navegador olvida ese id
  cuando el relay dice que el teléfono se fue, y deja los controles apagados hasta volver a oírlo.
- `seq`: sube de a uno por emisor; el receptor descarta lo que no sea mayor al último visto.
- `ts`: hora del emisor; se descarta lo que tenga más de 2 minutos de diferencia, hacia atrás
  o hacia adelante.
- Un mensaje que no se descifra (otra clave, alterado) se ignora sin más.

## Mensajes

Del teléfono:

| type | contenido |
|---|---|
| `state` | Estado completo, en cada cambio y cada 30 s mientras suena: `song` (id, title, artist, album, durationMs), `playing`, `positionMs` medido en `ts`, `shuffle`, `repeat` (`off`/`all`/`one`), `index`, `queue` (id, title, artist), `volume` (0–1). |
| `art` | Portada de la canción actual: `songId`, `jpeg` (base64, 300 px). Solo cuando cambia. |
| `results` | Respuesta a `search`: `query`, `songs` (hasta 50: id, title, artist, album, durationMs). |
| `askCode` | Solo en la sala temporal: pide el código que muestra el teléfono. Otra vez si el navegador reconecta. |
| `wrongCode` | Solo en la sala temporal: el código no era; `attemptsLeft`. |
| `pairFailed` | Solo en la sala temporal: 3 códigos equivocados; el teléfono se va de la sala. |
| `welcome` | Solo en la sala temporal, tras el código correcto: `room`, `key`, `name`. |

Del navegador en la sala temporal: `code` (`code`: los 6 caracteres, en mayúsculas y sin
espacios) y `paired` (ya guardó la sala del teléfono).

Del navegador en la sala del teléfono (`cmd`, con `op`):

`play`, `pause`, `next`, `previous`, `seek` (`positionMs`), `shuffle` (`on`),
`repeat` (`mode`), `volume` (`value`), `skipTo` (`index`), `remove` (`index`; nunca la
canción actual, como en la cola de la app), `move` (`fromIndex`, `toIndex`: `from` y `to` son
del sobre), `search` (`query`), `playSongs` (`ids`, `index`), `playNext` (`ids`),
`addToQueue` (`ids`), `hello` (pide un `state` y un `art` ya). Todos llevan `to`, salvo
quizá `hello`. `play` con la cola terminada vuelve a la primera canción en orden de
reproducción (la del orden aleatorio, si está activo).

## Del lado del teléfono

- Solo se conecta mientras el servicio de reproducción vive (sonando o pausado con la
  notificación) y si el monitor está activado. Sin nada vinculado, no se conecta.
- Reconecta sola, con espera creciente (1 s, 2 s, 4 s… hasta 30 s).
- Los comandos entran por el mismo reproductor que usan la notificación, el widget y Android
  Auto; la búsqueda usa la misma que la app. La biblioteca nunca sale entera del teléfono.

## Desplegar el relay

Lo despliega Cloudflare (Workers Builds) desde este repo: el Worker `player` del subdominio
`danzuniga`, con *Root directory* `relay`, sin comando de build y con `npm run deploy` como
comando de despliegue: corre los tests (`node --test`) y solo si pasan despliega con Wrangler.
Ese comando se configura en Cloudflare (la configuración de build del Worker), no en el repo.
Queda en `wss://player.danzuniga.workers.dev`, la dirección que usan `RELAY_URL` en
`app/build.gradle.kts` y `site/monitor/protocol.js` (el workflow Monitor comprueba que sean la
misma); el nombre del Worker tiene que coincidir con `name` en `relay/wrangler.toml`.

A mano: `npx wrangler login` y `npm run deploy` en `relay/`.

Para probar todo en local: `npm run dev` en `relay/`, y el monitor servido desde localhost con
`?relay=ws://localhost:8787`.
