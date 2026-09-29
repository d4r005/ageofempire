package com.d4r005.ageofempire

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.ceil
import kotlin.math.floor

class Renderer(private val state: GameState) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 30f
    }
    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f
    }

    companion object {
        const val GRASS_COLOR = 0xFF5D8A3A.toInt()
        const val SAND_COLOR = 0xFFC2B280.toInt()
        const val WATER_COLOR = 0xFF2E5C8A.toInt()
        const val TREE_COLOR = 0xFF2F5E1F.toInt()
        const val TREE_TRUNK = 0xFF6B4A2B.toInt()
        const val GOLD_COLOR = 0xFFF3C614.toInt()
        const val STONE_COLOR = 0xFF8A8A8A.toInt()
        const val BERRY_COLOR = 0xFFB03030.toInt()
        const val PLAYER_COLOR = 0xFF3D6FE0.toInt()
        const val ENEMY_COLOR = 0xFFD03A3A.toInt()
        const val SELECTION_COLOR = 0xFF3AE06A.toInt()
        const val HP_BACK = 0x99000000.toInt()
        const val HP_FRONT = 0xFF3AE06A.toInt()
        const val NEUTRAL_COLOR = 0xFF9E9E9E.toInt()
    }

    fun draw(canvas: Canvas) {
        val cam = state.camera
        canvas.drawColor(WATER_COLOR)

        // Terreno visible
        val ts = GameDef.TILE
        val tx0 = floor(cam.x / ts).toInt().coerceIn(0, GameDef.MAP_W - 1)
        val ty0 = floor(cam.y / ts).toInt().coerceIn(0, GameDef.MAP_H - 1)
        val tx1 = ceil((cam.x + cam.viewW / cam.zoom) / ts).toInt().coerceIn(0, GameDef.MAP_W - 1)
        val ty1 = ceil((cam.y + cam.viewH / cam.zoom) / ts).toInt().coerceIn(0, GameDef.MAP_H - 1)
        paint.style = Paint.Style.FILL
        for (ty in ty0..ty1) {
            for (tx in tx0..tx1) {
                paint.color = when (state.tileAt(tx, ty)) {
                    GameDef.WATER -> WATER_COLOR
                    GameDef.SAND -> SAND_COLOR
                    else -> GRASS_COLOR
                }
                val left = cam.worldToScreenX(tx * ts)
                val top = cam.worldToScreenY(ty * ts)
                canvas.drawRect(left, top, left + ts * cam.zoom + 1f, top + ts * cam.zoom + 1f, paint)
            }
        }

        for (e in state.entities) if (e.isResource) drawResource(e, canvas, cam)
        for (e in state.entities) if (e.isBuilding) drawBuilding(e, canvas, cam)
        for (e in state.entities) if (e.isUnit) drawUnit(e, canvas, cam)
    }

    private fun teamColor(team: Team): Int = when (team) {
        Team.PLAYER -> PLAYER_COLOR
        Team.ENEMY -> ENEMY_COLOR
        Team.NEUTRAL -> NEUTRAL_COLOR
    }

    private fun resourceColor(type: ResourceType?): Int = when (type) {
        ResourceType.WOOD -> TREE_COLOR
        ResourceType.FOOD -> BERRY_COLOR
        ResourceType.GOLD -> GOLD_COLOR
        ResourceType.STONE -> STONE_COLOR
        null -> NEUTRAL_COLOR
    }

    private fun drawResource(e: Entity, canvas: Canvas, cam: Camera) {
        val sx = cam.worldToScreenX(e.x)
        val sy = cam.worldToScreenY(e.y)
        if (sx < -80f || sy < -80f || sx > canvas.width + 80f || sy > canvas.height + 80f) return
        val r = e.radius * cam.zoom
        paint.style = Paint.Style.FILL
        when (e.kind) {
            Kind.TREE -> {
                paint.color = TREE_TRUNK
                canvas.drawRect(sx - r * 0.2f, sy, sx + r * 0.2f, sy + r * 0.6f, paint)
                paint.color = TREE_COLOR
                canvas.drawCircle(sx, sy - r * 0.25f, r, paint)
                paint.color = 0xFF3F7A2A.toInt()
                canvas.drawCircle(sx - r * 0.3f, sy - r * 0.5f, r * 0.55f, paint)
            }
            Kind.BERRY_BUSH -> {
                paint.color = 0xFF4E7A3A.toInt()
                canvas.drawCircle(sx, sy, r, paint)
                paint.color = BERRY_COLOR
                canvas.drawCircle(sx - r * 0.4f, sy - r * 0.3f, r * 0.3f, paint)
                canvas.drawCircle(sx + r * 0.4f, sy + r * 0.2f, r * 0.3f, paint)
            }
            else -> {
                paint.color = STONE_COLOR
                canvas.drawCircle(sx, sy, r, paint)
                paint.color = if (e.kind == Kind.GOLD_MINE) GOLD_COLOR else 0xFFCFCFCF.toInt()
                canvas.drawCircle(sx - r * 0.3f, sy - r * 0.3f, r * 0.35f, paint)
                canvas.drawCircle(sx + r * 0.35f, sy + r * 0.25f, r * 0.35f, paint)
            }
        }
    }

    private fun drawBuilding(e: Entity, canvas: Canvas, cam: Camera) {
        val sx = cam.worldToScreenX(e.x)
        val sy = cam.worldToScreenY(e.y)
        val hs = e.halfSize * cam.zoom
        val left = sx - hs
        val top = sy - hs
        val right = sx + hs
        val bottom = sy + hs
        if (right < -100f || bottom < -100f || left > canvas.width + 100f || top > canvas.height + 100f) return

        val constructing = e.construction < 1f
        paint.style = Paint.Style.FILL
        if (constructing) paint.alpha = (90 + 160 * e.construction).toInt()

        when (e.kind) {
            Kind.TOWN_CENTER -> {
                paint.color = 0xFF8A6B4A.toInt()   // muros
                canvas.drawRect(left, top + hs * 0.4f, right, bottom, paint)
                paint.color = 0xFFA0522D.toInt()     // techo
                val path = Path()
                path.moveTo(left - hs * 0.1f, top + hs * 0.4f)
                path.lineTo(sx, top - hs * 0.1f)
                path.lineTo(right + hs * 0.1f, top + hs * 0.4f)
                path.close()
                canvas.drawPath(path, paint)
                paint.color = teamColor(e.team)
                canvas.drawRect(sx - hs * 0.12f, top - hs * 0.55f, sx + hs * 0.12f, top - hs * 0.1f, paint)
            }
            Kind.HOUSE -> {
                paint.color = 0xFFB08A5A.toInt()
                canvas.drawRect(left, top + hs * 0.35f, right, bottom, paint)
                paint.color = 0xFF8A3A2A.toInt()
                val path = Path()
                path.moveTo(left, top + hs * 0.35f)
                path.lineTo(sx, top - hs * 0.1f)
                path.lineTo(right, top + hs * 0.35f)
                path.close()
                canvas.drawPath(path, paint)
            }
            Kind.BARRACKS -> {
                paint.color = 0xFF6E6E6E.toInt()
                canvas.drawRect(left, top, right, bottom, paint)
                paint.color = 0xFF4E4E4E.toInt()
                canvas.drawRect(sx - hs * 0.75f, sy - hs * 0.1f, sx + hs * 0.75f, sy + hs * 0.1f, paint)
                paint.color = teamColor(e.team)
                canvas.drawRect(right - hs * 0.2f, top - hs * 0.5f, right - hs * 0.05f, top, paint)
            }
            else -> { }
        }
        paint.alpha = 255

        // Barra de progreso de construcción
        if (constructing) {
            paint.color = HP_BACK
            canvas.drawRect(left, top - 26f, right, top - 12f, paint)
            paint.color = 0xFFF3C614.toInt()
            canvas.drawRect(left, top - 26f, left + (right - left) * e.construction, top - 12f, paint)
        }
        drawHpBarIfDamaged(e, canvas, sx, top - 40f, hs * 2f)
        drawSelectionRing(e, canvas, sx, sy, maxOf(hs, hs) + 10f)
    }

    private fun drawUnit(e: Entity, canvas: Canvas, cam: Camera) {
        val sx = cam.worldToScreenX(e.x)
        val sy = cam.worldToScreenY(e.y)
        val r = e.radius * cam.zoom
        if (sx < -60f || sy < -60f || sx > canvas.width + 60f || sy > canvas.height + 60f) return

        paint.style = Paint.Style.FILL
        paint.color = teamColor(e.team)
        canvas.drawCircle(sx, sy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.BLACK
        canvas.drawCircle(sx, sy, r, paint)

        // Sombra del estado
        if (e.kind == Kind.MILITIA) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = Color.WHITE
            canvas.drawLine(sx + r * 0.3f, sy - r * 0.8f, sx + r * 1.2f, sy + r * 0.2f, paint)
        }
        if (e.carryAmount > 0 && e.carryType != null) {
            paint.style = Paint.Style.FILL
            paint.color = resourceColor(e.carryType)
            canvas.drawRect(sx - 6f, sy - r - 18f, sx + 6f, sy - r - 6f, paint)
        }
        if (e.state == UnitState.GATHERING || e.state == UnitState.BUILDING) {
            paint.style = Paint.Style.FILL
            paint.color = 0xFFF3C614.toInt()
            val w = (e.workTimer / GameDef.GATHER_INTERVAL).coerceIn(0f, 1f)
            canvas.drawRect(sx - r, sy - r - 12f, sx - r + 2f * r * w, sy - r - 6f, paint)
        }
        drawHpBarIfDamaged(e, canvas, sx, sy - r - 30f, r * 2f)
        drawSelectionRing(e, canvas, sx, sy, r + 8f)
    }

    private fun drawSelectionRing(e: Entity, canvas: Canvas, sx: Float, sy: Float, r: Float) {
        if (state.selectedId != e.id) return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = SELECTION_COLOR
        canvas.drawCircle(sx, sy, r, paint)
    }

    private fun drawHpBarIfDamaged(e: Entity, canvas: Canvas, cx: Float, top: Float, width: Float) {
        if (e.hp >= e.maxHp) return
        val w = width.coerceAtLeast(30f)
        val h = 8f
        paint.style = Paint.Style.FILL
        paint.color = HP_BACK
        canvas.drawRect(cx - w / 2f, top, cx + w / 2f, top + h, paint)
        paint.color = if (e.hp > e.maxHp * 0.5f) HP_FRONT else 0xFFE03A3A.toInt()
        val frac = e.hp.toFloat() / e.maxHp.toFloat()
        canvas.drawRect(cx - w / 2f, top, cx - w / 2f + w * frac, top + h, paint)
    }
}
