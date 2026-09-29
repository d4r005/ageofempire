package com.d4r005.ageofempire

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

        const val TOP_BAR_H = 64f
        const val BTN_W = 240f
        const val BTN_H = 72f
        const val BTN_GAP = 24f
        const val BTN_BOTTOM = 24f
    }

    private val buttons = ArrayList<ButtonDef>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 28f }
    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 22f }
    private val titleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 72f; textAlign = Paint.Align.CENTER }
    private val msgTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 34f; textAlign = Paint.Align.CENTER }

    private var layoutW = 0
    private var layoutH = 0

    fun draw(canvas: Canvas) {
        layoutW = canvas.width
        layoutH = canvas.height
        rebuildButtons()

        drawTopBar(canvas)
        drawMessage(canvas)
        drawBottomPanel(canvas)
        drawGameOver(canvas)
    }

    fun handleTap(sx: Float, sy: Float): Boolean {
        if (state.result != 0) {
            state.startNewGame()
            return true
        }
        rebuildButtons()
        if (sy < TOP_BAR_H) return true   // consumir toques en la barra superior
        for (b in buttons) {
            if (b.rect.contains(sx, sy)) {
                doAction(b.action)
                return true
            }
        }
        return false
    }

    // --------------------------------------------------------------- dibujo

    private fun drawTopBar(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = 0xAA000000.toInt()
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
            canvas.drawRect(x, cy - 14f, x + 28f, cy + 14f, paint)
            textPaint.textAlign = Paint.Align.LEFT
            paint.color = Color.WHITE
            canvas.drawText(value.toString(), x + 40f, cy + 10f, textPaint)
            x += 40f + textPaint.measureText(value.toString()) + 40f
        }
        // Población
        val pop = "${state.popCount(Team.PLAYER)}/${state.popCap(Team.PLAYER)}"
        textPaint.textAlign = Paint.Align.LEFT
        paint.color = Color.WHITE
        canvas.drawText("pob $pop", x + 20f, cy + 10f, textPaint)
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

        // Nombre y vida de la selección
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
            smallTextPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("$name   HP ${sel.hp}/${sel.maxHp}", 24f, top + 32f, smallTextPaint)
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

    private fun rebuildButtons() {
        buttons.clear()
        val sel = state.selected()
        val mode = state.buildMode
        if (mode != null) {
            add(A_CANCEL, "Cancelar")
        } else if (sel != null) {
            when {
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
        }
    }
}
