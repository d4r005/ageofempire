package com.d4r005.ageofempire

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.abs
import kotlin.math.sqrt

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    val state = GameState()
    private val renderer = Renderer(context, state)
    private val hud = Hud(state)
    private var thread: GameThread? = null

    @Volatile private var surfaceReady = false

    // Estados del gesto: 0 nada, 1 tap candidato, 2 pan, 3 pinch,
    // 4 pinch liberado, 5 caja de selección, 6 arrastre en minimapa
    private var gesture = 0
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var pinchDist = 0f
    private var moved = false
    private val selectionBox = RectF()
    @Volatile private var selectionBoxActive = false

    private val longPressHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        if (gesture == 1 && !moved) {
            gesture = 5
            selectionBox.set(downX, downY, downX, downY)
            selectionBoxActive = true
        }
    }

    init {
        holder.addCallback(this)
        isFocusable = true
        hud.onNewGame = {
            state.startNewGame()
            deleteSave()
        }
        if (!loadGame()) state.startNewGame()
    }

    // ------------------------------------------------------------- bucle

    fun updateState(dt: Float) { state.update(dt) }

    fun drawFrame(canvas: Canvas) {
        renderer.draw(canvas)
        hud.draw(canvas, if (selectionBoxActive) selectionBox else null)
    }

    fun resume() {
        if (thread == null && surfaceReady) {
            thread = GameThread(this)
            thread?.running = true
            thread?.start()
        }
    }

    fun pause() {
        thread?.running = false
        thread = null
    }

    // ------------------------------------------------------------- guardado

    private val saveFile = "aoe_save.json"

    fun saveGame() {
        if (state.result != 0) return
        try {
            context.openFileOutput(saveFile, Context.MODE_PRIVATE).use {
                it.write(state.toJsonString().toByteArray())
            }
        } catch (_: Exception) { }
    }

    fun deleteSave() {
        try { context.deleteFile(saveFile) } catch (_: Exception) { }
    }

    private fun loadGame(): Boolean {
        return try {
            val text = context.openFileInput(saveFile).bufferedReader().use { it.readText() }
            if (text.isEmpty()) return false
            val ok = state.loadFromString(text)
            if (ok) state.showMessage("Partida cargada")
            ok
        } catch (_: Exception) { false }
    }

    // ------------------------------------------------------------- surface

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        resume()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        state.camera.viewW = width
        state.camera.viewH = height
        state.camera.clamp()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        pause()
    }

    // ------------------------------------------------------------- táctil

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gesture = 1
                moved = false
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                pinchDist = 0f
                // ¿Toque en el minimapa? arrastra la cámara
                if (hud.minimapRect().contains(downX, downY)) {
                    gesture = 6
                    moveCameraToMinimap(downX, downY)
                } else {
                    longPressHandler.postDelayed(longPressRunnable, 380L)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    gesture = 3
                    moved = true
                    pinchDist = spacing(event)
                    selectionBoxActive = false
                    longPressHandler.removeCallbacks(longPressRunnable)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                when {
                    gesture == 6 -> moveCameraToMinimap(event.x, event.y)
                    gesture == 3 && event.pointerCount >= 2 -> {
                        val newDist = spacing(event)
                        val cam = state.camera
                        val midX = (event.getX(0) + event.getX(1)) / 2f
                        val midY = (event.getY(0) + event.getY(1)) / 2f
                        val worldX = cam.screenToWorldX(midX)
                        val worldY = cam.screenToWorldY(midY)
                        if (pinchDist > 10f && newDist > 10f) {
                            cam.zoom = (cam.zoom * (newDist / pinchDist)).coerceIn(0.35f, 2.5f)
                            cam.x = worldX - midX / cam.zoom
                            cam.y = worldY - midY / cam.zoom
                            cam.clamp()
                        }
                        pinchDist = newDist
                    }
                    gesture == 3 -> { /* pinch perdido */ }
                    gesture == 5 -> {
                        selectionBox.set(
                            minOf(downX, event.x), minOf(downY, event.y),
                            maxOf(downX, event.x), maxOf(downY, event.y)
                        )
                    }
                    else -> {
                        val dx = event.x - lastX
                        val dy = event.y - lastY
                        if (abs(event.x - downX) > 20f || abs(event.y - downY) > 20f) {
                            gesture = 2
                            moved = true
                            longPressHandler.removeCallbacks(longPressRunnable)
                        }
                        if (gesture == 2) {
                            val cam = state.camera
                            cam.x -= dx / cam.zoom
                            cam.y -= dy / cam.zoom
                            cam.clamp()
                        }
                        lastX = event.x
                        lastY = event.y
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                gesture = 4   // no generar tap tras pinch
            }
            MotionEvent.ACTION_UP -> {
                longPressHandler.removeCallbacks(longPressRunnable)
                when {
                    gesture == 5 -> {
                        // Caja de selección completada
                        val cam = state.camera
                        state.boxSelect(
                            cam.screenToWorldX(minOf(downX, event.x)),
                            cam.screenToWorldY(minOf(downY, event.y)),
                            cam.screenToWorldX(maxOf(downX, event.x)),
                            cam.screenToWorldY(maxOf(downY, event.y))
                        )
                    }
                    gesture == 1 && !moved -> {
                        val cam = state.camera
                        val wx = cam.screenToWorldX(event.x)
                        val wy = cam.screenToWorldY(event.y)
                        if (!hud.handleTap(event.x, event.y)) {
                            state.tapWorld(wx, wy)
                        }
                    }
                }
                selectionBoxActive = false
                gesture = 0
            }
            MotionEvent.ACTION_CANCEL -> {
                longPressHandler.removeCallbacks(longPressRunnable)
                selectionBoxActive = false
                gesture = 0
            }
        }
        return true
    }

    private fun moveCameraToMinimap(sx: Float, sy: Float) {
        val (wx, wy) = hud.minimapToWorld(sx, sy)
        val cam = state.camera
        cam.x = wx - cam.viewW / (2f * cam.zoom)
        cam.y = wy - cam.viewH / (2f * cam.zoom)
        cam.clamp()
    }

    private fun spacing(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return sqrt(dx * dx + dy * dy)
    }
}
