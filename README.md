# Age of Empire 🏰

Un juego de estrategia en tiempo real (RTS) para Android inspirado en Age of Empires, escrito 100% en Kotlin nativo, sin dependencias externas.

## Estado: v0.6.0 — arqueros, caballería y sistema de edades

- **v0.3.0**: pasto con 3 variantes y relieve, agua animada, costas con borde mojado y espuma, árboles más grandes con 3 variantes, edificios isométricos y unidades con volumen, orden de dibujo por profundidad y viñeteado
- Mapa de tiles generado aleatoriamente (pasto, agua, arena) de 64×48, ahora con **texturas reales** en lugar de colores planos
- **Arqueros** (a distancia, Edad Feudal) y **caballería** (rápidos y fuertes, Edad de los Castillos)
**Tres edades**: Oscura → Feudal → Castillos, investigadas en el centro urbano; cada edad da +10% de daño
**Torres de vigilancia** que disparan flechas a enemigos en rango y **murallas** de piedra (defensa pasiva)
- **Efectos de sonido y música**: hachazos, espadas, flechas, cuernos de incursión, fanfarrias
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
| Pulsación larga + arrastrar | Caja de selección (multiselección) |
| Tap en el suelo con unidades seleccionadas | Mover en formación / atacar / recolectar |
| Arrastrar con un dedo | Mover cámara |
| Pellizco con dos dedos | Zoom |
| Tap o arrastre en el minimapa (arriba a la derecha) | Mover la cámara |
| Botones inferiores | Construir, entrenar, investigar, detener |

**Guardado automático**: la partida se guarda al cerrar la app y se recupera al abrirla. El botón «Nuevo» en la barra superior empieza una partida fresca.

## Flujo de juego

1. Selecciona un aldeano y envíalo a un árbol o arbusto de bayas.
2. Acumula madera y comida; entrena más aldeanos desde el centro urbano.
3. Construye casas para subir el límite de población.
4. Levanta un cuartel y entrena milicia (necesitas oro: manda aldeanos a las minas).
5. Con piedra, levanta murallas y torres: las torres disparan solas a los enemigos en rango.
5. Destruye el centro urbano enemigo antes de que sus incursiones arrasen el tuyo.

## Hoja de ruta

- [x] Sprites ilustrados para edificios, unidades y recursos
- [x] Orientación vertical
- [x] Animaciones básicas (bob al caminar, balanceo al recolectar, giro según dirección)
- [x] Pathfinding A* completo (evita agua, edificios y bosques)
- [x] Multiselección por caja (pulsación larga + arrastrar) y órdenes en formación
- [x] Guardado automático de partida
- [x] Minimapa táctil
- [ ] Animaciones completas de sprite sheet
- [x] Sonido y música
- [x] Más edificios (murallas y torres)
- [x] Más unidades (arqueros y caballería)
- [x] Edades / tecnologías (Oscura → Feudal → Castillos, +10% daño por edad)

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
