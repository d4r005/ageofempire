package com.d4r005.ageofempire

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.ceil
import kotlin.math.floor

class Renderer(private val context: Context, private val state: GameState) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 30f
    }

    companion object {
        const val WATER_COLOR = 0xFF2E5C8A.toInt()
        const val GOLD_COLOR = 0xFFF3C614.toInt()
        const val WOOD_COLOR = 0xFF6B4A2B.toInt()
        const val STONE_COLOR = 0xFF9E9E9E.toInt()
        const val BERRY_COLOR = 0xFFB03030.toInt()
        const val SELECTION_COLOR = 0xFF3AE06A.toInt()
        const val HP_BACK = 0x99000000.toInt()
        const val HP_FRONT = 0xFF3AE06A.toInt()
    }

    private fun loadBitmap(name: String): Bitmap {
        val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
        return BitmapFactory.decodeResource(context.resources, resId)
    }

    private val tileGrass by lazy { loadBitmap("tile_grass") }
    private val tileSand by lazy { loadBitmap("tile_sand") }
    private val tileWater by lazy { loadBitmap("tile_water") }

    private val bmpTree by lazy { loadBitmap("sprite_tree") }
    private val bmpGold by lazy { loadBitmap("sprite_gold_mine") }
    private val bmpStone by lazy { loadBitmap("sprite_stone_mine") }
    private val bmpBerry by lazy { loadBitmap("sprite_berry_bush") }

    private val bmpTownCenter by lazy { loadBitmap("sprite_town_center") }
    private val bmpHouse by lazy { loadBitmap("sprite_house") }
    private val bmpBarracks by lazy { loadBitmap("sprite_barracks") }

    private val bmpVillagerBlue by lazy { loadBitmap("sprite_villager_blue") }
    private val bmpVillagerRed by lazy { loadBitmap("sprite_villager_red") }
    private val bmpMilitiaBlue by lazy { loadBitmap("sprite_militia_blue") }
    private val bmpMilitiaRed by lazy { loadBitmap("sprite_militia_red") }

    private val srcRectCache = Rect()
    private val dstRectF = RectF()

    fun draw(canvas: Canvas) {
        val cam = state.camera
        canvas.drawColor(WATER_COLOR)

        val ts = GameDef.TILE
        val tx0 = floor(cam.x / ts).toInt().coerceIn(0, GameDef.MAP_W - 1)
        val ty0 = floor(cam.y / ts).toInt().coerceIn(0, GameDef.MAP_H - 1)
        val tx1 = ceil((cam.x + cam.viewW / cam.zoom) / ts).toInt().coerceIn(0, GameDef.MAP_W - 1)
        val ty1 = ceil((cam.y + cam.viewH / cam.zoom) / ts).toInt().coerceIn(0, GameDef.MAP_H - 1)

        srcRectCache.set(0, 0, tileGrass.width, tileGrass.height)
        for (ty in ty0..ty1) {
            for (tx in tx0..tx1) {
                val bmp = when (state.tileAt(tx, ty)) {
                    GameDef.WATER -> tileWater
                    GameDef.SAND -> tileSand
                    else -> tileGrass
                }
                val left = cam.worldToScreenX(tx * ts)
                val top = cam.worldToScreenY(ty * ts)
                val size = ts * cam.zoom
                srcRectCache.set(0, 0, bmp.width, bmp.height)
                dstRectF.set(left, top, left + size + 0.5f, top + size + 0.5f)
                canvas.drawBitmap(bmp, srcRectCache, dstRectF, bitmapPaint)
            }
        }

        for (e in state.entities) if (e.isResource) drawResource(e, canvas, cam)
        for (e in state.entities) if (e.isBuilding) drawBuilding(e, canvas, cam)
        for (e in state.entities) if (e.isUnit) drawUnit(e, canvas, cam)
    }

    private fun resourceBitmap(kind: Kind): Bitmap = when (kind) {
        Kind.TREE -> bmpTree
        Kind.GOLD_MINE -> bmpGold
        Kind.STONE_MINE -> bmpStone
        Kind.BERRY_BUSH -> bmpBerry
        else -> bmpTree
    }

    private fun buildingBitmap(kind: Kind): Bitmap = when (kind) {
        Kind.TOWN_CENTER -> bmpTownCenter
        Kind.HOUSE -> bmpHouse
        Kind.BARRACKS -> bmpBarracks
        else -> bmpHouse
    }

    private fun unitBitmap(kind: Kind, team: Team): Bitmap = when (kind) {
        Kind.VILLAGER -> if (team == Team.PLAYER) bmpVillagerBlue else bmpVillagerRed
        Kind.MILITIA -> if (team == Team.PLAYER) bmpMilitiaBlue else bmpMilitiaRed
        else -> bmpVillagerBlue
    }

    private fun resourceColor(type: ResourceType?): Int = when (type) {
        ResourceType.WOOD -> WOOD_COLOR
        ResourceType.FOOD -> BERRY_COLOR
        ResourceType.GOLD -> GOLD_COLOR
        ResourceType.STONE -> STONE_COLOR
        null -> STONE_COLOR
    }

    private fun drawBitmapCentered(bmp: Bitmap, canvas: Canvas, cx: Float, cy: Float, halfW: Float, halfH: Float, alpha: Int = 255) {
        srcRectCache.set(0, 0, bmp.width, bmp.height)
        dstRectF.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
        bitmapPaint.alpha = alpha
        canvas.drawBitmap(bmp, srcRectCache, dstRectF, bitmapPaint)
        bitmapPaint.alpha = 255
    }

    private fun drawResource(e: Entity, canvas: Canvas, cam: Camera) {
        val sx = cam.worldToScreenX(e.x)
        val sy = cam.worldToScreenY(e.y)
        val span = e.radius * 2.3f * cam.zoom
        if (sx < -span || sy < -span || sx > canvas.width + span || sy > canvas.height + span) return
        drawBitmapCentered(resourceBitmap(e.kind), canvas, sx, sy, span / 2f, span / 2f)
    }

    private fun drawBuilding(e: Entity, canvas: Canvas, cam: Camera) {
        val sx = cam.worldToScreenX(e.x)
        val sy = cam.worldToScreenY(e.y)
        val hs = e.halfSize * cam.zoom
        val span = hs * 2.35f
        if (sx + span < 0f || sy + span < 0f || sx - span > canvas.width || sy - span > canvas.height) return

        val constructing = e.construction < 1f
        val alpha = if (constructing) (90 + 165 * e.construction).toInt().coerceIn(0, 255) else 255
        drawBitmapCentered(buildingBitmap(e.kind), canvas, sx, sy - hs * 0.15f, span / 2f, span / 2f, alpha)

        // Estandarte de equipo
        paint.style = Paint.Style.FILL
        paint.color = if (e.team == Team.PLAYER) Renderer2Colors.PLAYER else Renderer2Colors.ENEMY
        canvas.drawRect(sx - hs * 0.55f, sy - hs * 1.55f, sx - hs * 0.28f, sy - hs * 0.95f, paint)
        paint.color = Color.argb(80, 0, 0, 0)
        canvas.drawRect(sx - hs * 0.30f, sy - hs * 1.62f, sx - hs * 0.27f, sy - hs * 0.9f, paint)

        if (constructing) {
            val top = sy - hs - 30f
            paint.color = HP_BACK
            canvas.drawRect(sx - hs, top, sx + hs, top + 12f, paint)
            paint.color = GOLD_COLOR
            canvas.drawRect(sx - hs, top, sx - hs + 2f * hs * e.construction, top + 12f, paint)
        }
        drawHpBarIfDamaged(e, canvas, sx, sy - hs - 46f, hs * 2f)
        drawSelectionRing(e, canvas, sx, sy, hs + 14f)
    }

    private fun drawUnit(e: Entity, canvas: Canvas, cam: Camera) {
        val sx = cam.worldToScreenX(e.x)
        val sy = cam.worldToScreenY(e.y)
        val r = e.radius * cam.zoom
        val span = r * 3.6f
        if (sx + span < 0f || sy + span < 0f || sx - span > canvas.width || sy - span > canvas.height) return

        // Sombra de contacto
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(70, 0, 0, 0)
        canvas.drawOval(sx - r * 0.9f, sy + r * 0.35f, sx + r * 0.9f, sy + r * 1.05f, paint)

        drawBitmapCentered(unitBitmap(e.kind, e.team), canvas, sx, sy - r * 0.5f, span / 2f, span / 2f)

        if (e.carryAmount > 0 && e.carryType != null) {
            paint.color = resourceColor(e.carryType)
            canvas.drawRect(sx - 6f, sy - r - 24f, sx + 6f, sy - r - 12f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = Color.BLACK
            canvas.drawRect(sx - 6f, sy - r - 24f, sx + 6f, sy - r - 12f, paint)
            paint.style = Paint.Style.FILL
        }
        if (e.state == UnitState.GATHERING || e.state == UnitState.BUILDING) {
            paint.color = GOLD_COLOR
            val wfrac = (e.workTimer / GameDef.GATHER_INTERVAL).coerceIn(0f, 1f)
            canvas.drawRect(sx - r, sy - r - 16f, sx - r + 2f * r * wfrac, sy - r - 10f, paint)
        }
        drawHpBarIfDamaged(e, canvas, sx, sy - r - 34f, r * 2.2f)
        drawSelectionRing(e, canvas, sx, sy, r + 12f)
    }

    private fun drawSelectionRing(e: Entity, canvas: Canvas, sx: Float, sy: Float, r: Float) {
        if (state.selectedId != e.id) return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = SELECTION_COLOR
        canvas.drawOval(sx - r, sy - r * 0.55f, sx + r, sy + r * 0.55f, paint)
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

private object Renderer2Colors {
    const val PLAYER = 0xFF3D6FE0.toInt()
    const val ENEMY = 0xFFD03A3A.toInt()
}
