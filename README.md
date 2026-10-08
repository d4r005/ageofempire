# Age of Empire 🏰

Un juego de estrategia en tiempo real (RTS) para Android inspirado en Age of Empires, escrito 100% en Kotlin nativo, sin dependencias externas.

## Estado: v0.3.0 — gráficos mejorados (estilo AoE2)

- **v0.3.0**: pasto con 3 variantes y relieve, agua animada, costas con borde mojado y espuma, árboles más grandes con 3 variantes, edificios isométricos y unidades con volumen, orden de dibujo por profundidad y viñeteado
- Mapa de tiles generado aleatoriamente (pasto, agua, arena) de 64×48, ahora con **texturas reales** en lugar de colores planos
- Edificios, unidades y recursos con **sprites ilustrados** estilo Age of Empires 2 (centro urbano, casa, cuartel, aldeano, milicia, árbol, mina de oro, mina de piedra, arbusto de bayas)
- Estandarte de color por equipo sobre los edificios y sombra de contacto bajo las unidades
- **Orientación vertical (portrait)** — pensado para jugar con una mano
- 4 recursos: madera, comida, oro y piedra
- Aldeanos que recolectan, acarrean y depositan en el centro urbano
- Construcción de casas (+población) y cuarteles (milicia)
- Combate cuerpo a cuerpo y destrucción de edificios
- IA enemiga que lanza incursiones cada vez más grandes
- Victoria al destruir todos los edificios enemigos; derrota si pierdes los tuyos
- Cámara táctil: arrastra para mover, pellizca para hacer zoom

## Cómo compilar

Requisitos:
- Android Studio (Koala o superior)
- JDK 17 (incluido en Android Studio)

Opciones:
1. Abre la carpeta del proyecto en Android Studio y pulsa **Run**.
2. O desde la terminal: `./gradlew assembleDebug` (la APK queda en `app/build/outputs/apk/debug/`).

Instala la APK en tu teléfono o usa un emulador.

## Controles

| Gesto | Acción |
|---|---|
| Tap sobre una unidad/edificio/recurso | Seleccionar |
| Tap en el suelo con una unidad seleccionada | Mover / atacar / recolectar según el objetivo |
| Arrastrar con un dedo | Mover cámara |
| Pellizco con dos dedos | Zoom |
| Botones inferiores | Construir, entrenar, detener |

## Flujo de juego

1. Selecciona un aldeano y envíalo a un árbol o arbusto de bayas.
2. Acumula madera y comida; entrena más aldeanos desde el centro urbano.
3. Construye casas para subir el límite de población.
4. Levanta un cuartel y entrena milicia (necesitas oro: manda aldeanos a las minas).
5. Destruye el centro urbano enemigo antes de que sus incursiones arrasen el tuyo.

## Hoja de ruta

- [x] Sprites ilustrados para edificios, unidades y recursos
- [x] Orientación vertical
- [ ] Animaciones (caminar, recolectar, atacar) en lugar de sprite estático
- [ ] Sonido y música
- [ ] Más edificios (molino, granja, murallas, torres)
- [ ] Más unidades (arqueros, caballería)
- [ ] Edades / tecnologías
- [ ] Pathfinding A* completo
- [ ] Multiselección y arrastre de caja de selección
- [ ] Guardado de partida
- [ ] Minimapa

## Estructura del código

```
app/src/main/java/com/d4r005/ageofempire/
├── MainActivity.kt   — actividad principal, pantalla completa
├── GameView.kt        — SurfaceView, input táctil (tap/pan/pinch)
├── GameThread.kt      — bucle de juego a ~60 FPS
├── GameState.kt       — simulación: unidades, edificios, recursos, IA, comandos
├── GameDef.kt        — constantes de balance (costes, tiempos, población)
├── Camera.kt          — transformación mundo↔pantalla
├── Renderer.kt        — dibujo del terreno y entidades con sprites (bitmaps)
├── Hud.kt             — barra de recursos, botones contextuales, mensajes
└── Entities.kt        — entidades y enums del juego

app/src/main/res/drawable/
└── tile_*.png, sprite_*.png — texturas y sprites del juego (arte generado)
```
