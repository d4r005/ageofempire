package com.d4r005.ageofempire

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

class Hud(private val state: GameState) {

    private data class ButtonDef(val rect: RectF, val action: Int, val label: String)

    companion object {
        const val A_HOUSE = 1
        const val A_BARRACKS = 2
        const val A_VILLAGER = 3
        const val A_MILITIA = 4
        const val A_STOP = 5
        const val A_CANCEL = 6
        const val A_NEW_GAME = 7

        const val TOP_BAR_H = 64f
        const val BTN_W = 196f
        const val BTN_H = 72f
        const val BTN_GAP = 14f
        const val BTN_BOTTOM = 24f

        const val MINI_W = 136f
        const val MINI_H = 102f
        const val MINI_MARGIN = 10f
    }

    private val buttons = ArrayList<ButtonDef>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 28f }
    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 22f }
    private val titleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 72f; textAlign = Paint.Align.CENTER }
    private val msgTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 34f; textAlign = Paint.Align.CENTER }
    private val miniBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = 0xFFE8D9A0.toInt()
    }
    private val miniCamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.WHITE
    }
    private val miniBmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private var layoutW = 0
    private var layoutH = 0

    // Minimapa: terreno cacheado por versión de mundo
    private var miniTerrain: Bitmap? = null
    private var miniVersion = -1
    private val miniRect = RectF()
    private val newGameRect = RectF()

    /** Se invoca al pedir una partida nueva; el GameView borra el guardado. */
    var onNewGame: (() -> Unit)? = null

    // ------------------------------------------------------------- dibujo

    fun draw(canvas: Canvas, selectionBox: RectF? = null) {
        layoutW = canvas.width
        layoutH = canvas.height
        rebuildButtons()

        drawTopBar(canvas)
        drawMinimap(canvas)
        drawMessage(canvas)
        drawBottomPanel(canvas)
        drawGameOver(canvas)
        if (selectionBox != null) {
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(36, 58, 224, 110)
            canvas.drawRect(selectionBox, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = 0xFF3AE06A.toInt()
            canvas.drawRect(selectionBox, paint)
            paint.style = Paint.Style.FILL
        }
    }

    private fun drawTopBar(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = 0x99302010.toInt()
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), TOP_BAR_H, paint)

        var x = 24f
        val cy = TOP_BAR_H / 2f
        val items = listOf(
            Triple(0xFF8A5A2A.toInt(), state.resources[ResourceType.WOOD] ?: 0, "madera"),
            Triple(0xFFB03030.toInt(), state.resources[ResourceType.FOOD] ?: 0, "comida"),
            Triple(0xFFF3C614.toInt(), state.resources[ResourceType.GOLD] ?: 0, "oro"),
            Triple(0xFF8A8A8A.toInt(), state.resources[ResourceType.STONE] ?: 0, "piedra")
        )
        for ((color, value, _) in items) {
            paint.color = color
            canvas.drawRect(x, cy - 13f, x + 24f, cy + 13f, paint)
            textPaint.textAlign = Paint.Align.LEFT
            paint.color = Color.WHITE
            canvas.drawText(value.toString(), x + 30f, cy + 9f, textPaint)
            x += 26f + textPaint.measureText(value.toString()) + 26f
        }
        // Población
        val pop = "${state.popCount(Team.PLAYER)}/${state.popCap(Team.PLAYER)}"
        textPaint.textAlign = Paint.Align.LEFT
        paint.color = Color.WHITE
        canvas.drawText("pob $pop", x + 20f, cy + 10f, textPaint)

        // Botón "Nuevo" (esquina superior derecha, bajo la barra no: dentro)
        newGameRect.set(canvas.width - 150f, cy - 26f, canvas.width - 16f, cy + 26f)
        paint.color = 0xFF6B4A2B.toInt()
        canvas.drawRoundRect(newGameRect, 10f, 10f, paint)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("Nuevo", newGameRect.centerX(), newGameRect.centerY() + 9f, textPaint)
    }

    // ------------------------------------------------------------- minimapa

    private fun miniColors(): IntArray {
        val colors = IntArray(GameDef.MAP_W * GameDef.MAP_H)
        for (i in colors.indices) {
            colors[i] = when (state.tiles[i]) {
                GameDef.WATER -> 0xFF2A6A9E.toInt()
                GameDef.SAND -> 0xFFD8B478.toInt()
                else -> 0xFF4C8A2C.toInt()
            }
        }
        return colors
    }

    private fun drawMinimap(canvas: Canvas) {
        val bmp = miniTerrain?.takeIf { miniVersion == state.worldVersion } ?: run {
            val b = Bitmap.createBitmap(
                miniColors(), GameDef.MAP_W, GameDef.MAP_H, Bitmap.Config.ARGB_8888
            )
            miniTerrain = b
            miniVersion = state.worldVersion
            b
        }
        val left = canvas.width - MINI_W - MINI_MARGIN
        val top = TOP_BAR_H + MINI_MARGIN
        miniRect.set(left, top, left + MINI_W, top + MINI_H)

        paint.style = Paint.Style.FILL
        paint.color = 0xA0100808.toInt()
        canvas.drawRect(miniRect.left - 3f, miniRect.top - 3f, miniRect.right + 3f, miniRect.bottom + 3f, paint)
        canvas.drawBitmap(bmp, null, miniRect, miniBmpPaint)

        // Entidades: puntos
        paint.style = Paint.Style.FILL
        val worldW = GameDef.MAP_W * GameDef.TILE
        val worldH = GameDef.MAP_H * GameDef.TILE
        for (e in state.entities) {
            if (e.dead) continue
            val px = miniRect.left + (e.x / worldW) * MINI_W
            val py = miniRect.top + (e.y / worldH) * MINI_H
            when {
                e.isBuilding && e.team == Team.PLAYER -> {
                    paint.color = 0xFF4C90FF.toInt()
                    canvas.drawRect(px - 2.4f, py - 2.4f, px + 2.4f, py + 2.4f, paint)
                }
                e.isBuilding -> {
                    paint.color = 0xFFFF5040.toInt()
                    canvas.drawRect(px - 2.4f, py - 2.4f, px + 2.4f, py + 2.4f, paint)
                }
                e.isUnit && e.team == Team.PLAYER -> {
                    paint.color = 0xFF6CC0FF.toInt()
                    canvas.drawRect(px - 1.6f, py - 1.6f, px + 1.6f, py + 1.6f, paint)
                }
                e.isUnit -> {
                    paint.color = 0xFFFF8070.toInt()
                    canvas.drawRect(px - 1.6f, py - 1.6f, px + 1.6f, py + 1.6f, paint)
                }
            }
        }

        // Rectángulo de la cámara
        val cam = state.camera
        val vw = (cam.viewW / cam.zoom) / worldW * MINI_W
        val vh = (cam.viewH / cam.zoom) / worldH * MINI_H
        miniCamPaint.strokeWidth = 2f
        canvas.drawRect(
            miniRect.left + (cam.x / worldW) * MINI_W,
            miniRect.top + (cam.y / worldH) * MINI_H,
            miniRect.left + (cam.x / worldW) * MINI_W + vw,
            miniRect.top + (cam.y / worldH) * MINI_H + vh,
            miniCamPaint
        )
        canvas.drawRect(miniRect, miniBorderPaint)
    }

    /** ¿El toque cae sobre el minimapa? GameView lo usa para mover la cámara. */
    fun minimapRect(): RectF = miniRect

    /** Convierte un punto del minimapa a coordenadas de mundo. */
    fun minimapToWorld(sx: Float, sy: Float): Pair<Float, Float> {
        val worldW = GameDef.MAP_W * GameDef.TILE
        val worldH = GameDef.MAP_H * GameDef.TILE
        val fx = ((sx - miniRect.left) / MINI_W).coerceIn(0f, 1f)
        val fy = ((sy - miniRect.top) / MINI_H).coerceIn(0f, 1f)
        return Pair(fx * worldW, fy * worldH)
    }

    private fun drawMessage(canvas: Canvas) {
        if (state.messageTimer <= 0f || state.message.isEmpty()) return
        paint.color = 0x99000000.toInt()
        val tw = msgTextPaint.measureText(state.message)
        canvas.drawRect(canvas.width / 2f - tw / 2f - 20f, TOP_BAR_H + 16f, canvas.width / 2f + tw / 2f + 20f, TOP_BAR_H + 70f, paint)
        canvas.drawText(state.message, canvas.width / 2f, TOP_BAR_H + 54f, msgTextPaint)
    }

    private fun drawBottomPanel(canvas: Canvas) {
        val sel = state.selected()
        val mode = state.buildMode
        if (sel == null && mode == null && buttons.isEmpty()) return

        // Fondo del panel
        val bottom = canvas.height - BTN_BOTTOM
        val top = bottom - BTN_H - 60f
        paint.style = Paint.Style.FILL
        paint.color = 0x88000000.toInt()
        canvas.drawRect(0f, top, canvas.width.toFloat(), bottom + BTN_BOTTOM, paint)

        // Nombre y vida de la selección (y tamaño del grupo)
        if (sel != null) {
            val name = when (sel.kind) {
                Kind.VILLAGER -> "Aldeano"
                Kind.MILITIA -> "Milicia"
                Kind.TOWN_CENTER -> "Centro urbano"
                Kind.HOUSE -> "Casa"
                Kind.BARRACKS -> "Cuartel"
                Kind.TREE -> "Árbol"
                Kind.GOLD_MINE -> "Mina de oro"
                Kind.STONE_MINE -> "Mina de piedra"
                Kind.BERRY_BUSH -> "Arbusto de bayas"
            } + if (sel.team == Team.ENEMY) " (enemigo)" else ""
            val group = if (state.selectedIds.size > 1) "   [${state.selectedIds.size} unidades]" else ""
            smallTextPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("$name   HP ${sel.hp}/${sel.maxHp}$group", 24f, top + 32f, smallTextPaint)
        }
        if (mode != null) {
            smallTextPaint.textAlign = Paint.Align.LEFT
            val label = if (mode == Kind.HOUSE) "Toca el mapa para colocar una Casa (30 madera)" else "Toca el mapa para colocar un Cuartel (100 madera)"
            canvas.drawText(label, 24f, top + 32f, smallTextPaint)
        }

        // Botones centrados
        var totalW = 0f
        for (b in buttons) totalW += b.rect.width()
        totalW += BTN_GAP * (buttons.size - 1).coerceAtLeast(0)
        var bx = canvas.width / 2f - totalW / 2f
        for (i in buttons.indices) {
            val b = buttons[i]
            b.rect.left = bx
            b.rect.top = bottom - BTN_H
            b.rect.right = bx + BTN_W
            b.rect.bottom = bottom
            paint.color = 0xFF3D6FE0.toInt()
            if (b.action == A_CANCEL) paint.color = 0xFFD03A3A.toInt()
            canvas.drawRoundRect(b.rect, 12f, 12f, paint)
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(b.label, b.rect.centerX(), b.rect.centerY() + 10f, textPaint)
            bx += BTN_W + BTN_GAP
        }
    }

    private fun drawGameOver(canvas: Canvas) {
        if (state.result == 0) return
        paint.color = 0xB3000000.toInt()
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
        val msg = if (state.result == 1) "¡VICTORIA!" else "DERROTA"
        canvas.drawText(msg, canvas.width / 2f, canvas.height / 2f - 40f, titleTextPaint)
        smallTextPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("Toca la pantalla para jugar de nuevo", canvas.width / 2f, canvas.height / 2f + 40f, smallTextPaint)
    }

    // ------------------------------------------------------------- acciones

    fun handleTap(sx: Float, sy: Float): Boolean {
        if (state.result != 0) {
            state.startNewGame()
            return true
        }
        rebuildButtons()
        if (newGameRect.contains(sx, sy)) {
            onNewGame?.invoke()
            return true
        }
        for (b in buttons) {
            if (b.rect.contains(sx, sy)) {
                doAction(b.action)
                return true
            }
        }
        return false
    }

    private fun rebuildButtons() {
        buttons.clear()
        val sel = state.selected()
        val mode = state.buildMode
        if (mode != null) {
            add(A_CANCEL, "Cancelar")
            return
        }
        if (sel == null) return
        val group = state.selectedUnits()
        when {
            // grupo mixto de unidades propias
            group.size > 1 -> add(A_STOP, "Alto")
            sel.kind == Kind.VILLAGER && sel.team == Team.PLAYER -> {
                add(A_HOUSE, "Casa 30m")
                add(A_BARRACKS, "Cuartel 100m")
                add(A_STOP, "Alto")
            }
            sel.kind == Kind.MILITIA -> add(A_STOP, "Alto")
            sel.kind == Kind.TOWN_CENTER && sel.team == Team.PLAYER -> add(A_VILLAGER, "Aldeano 50c")
            sel.kind == Kind.BARRACKS && sel.team == Team.PLAYER -> add(A_MILITIA, "Milicia 60c 20o")
            else -> { }
        }
    }

    private fun add(action: Int, label: String) {
        val index = buttons.size
        val left = 0f + index * (BTN_W + BTN_GAP)  // se recoloca al dibujar
        buttons.add(ButtonDef(RectF(left, 0f, left + BTN_W, BTN_H), action, label))
    }

    private fun doAction(action: Int) {
        val sel = state.selected()
        when (action) {
            A_HOUSE -> state.startBuildMode(Kind.HOUSE)
            A_BARRACKS -> state.startBuildMode(Kind.BARRACKS)
            A_VILLAGER -> if (sel != null) state.tryQueueUnit(sel, Kind.VILLAGER)
            A_MILITIA -> if (sel != null) state.tryQueueUnit(sel, Kind.MILITIA)
            A_STOP -> state.stopSelected()
            A_CANCEL -> state.buildMode = null
            A_NEW_GAME -> onNewGame?.invoke()
        }
    }
}
