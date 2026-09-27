# player-showcase

Video de presentación de Player, hecho con [Remotion](https://remotion.dev) y el sistema de diseño Daniel Zúñiga
(Obsidiana, oro, JetBrains Mono + Instrument Serif, la ñ).

- `src/dz.tsx`: tokens, fuentes (las mismas `.ttf` de la app, en `public/fonts`) y piezas compartidas.
- `src/scenes/`: una escena por archivo; `src/PlayerShowcase.tsx` las une con fundidos cortos.
- `public/img`: capturas reales de la app, renderizadas con Robolectric.

```console
npm ci
npm run dev                                   # Remotion Studio
npm run render                                # video + miniatura en out/, con la versión y los tests de la app
```

El workflow `.github/workflows/showcase.yml` hace lo mismo en CI. Solo publica después de un release estable: renderiza el commit de su tag `vX.Y.Z` y sube el video a ese release y a `showcase-v1`, que enlazan la web y el README. Los push (también a `main`) y los dev builds no publican nada: el video queda como artefacto del workflow. Para volver a publicar el video de un release, lanza el workflow a mano con `release` (por ejemplo `v1.6.0`); reemplaza el que ya tenga.

Si Remotion no puede descargar su Chrome, pásale uno local: `npm run render -- --browser-executable=/ruta/a/chrome-headless-shell`.
