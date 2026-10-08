package com.d4r005.ageofempire

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Shader
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
    private val tileGrassB by lazy { loadBitmap("tile_grass_b") }
    private val tileGrassC by lazy { loadBitmap("tile_grass_c") }
    private val tileWaterB by lazy { loadBitmap("tile_water_b") }
    private val bmpTreeB by lazy { loadBitmap("sprite_tree_b") }
    private val bmpTreeC by lazy { loadBitmap("sprite_tree_c") }
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

        val size = ts * cam.zoom
        val time = (System.currentTimeMillis() % 100000L) / 1000f

        // 1) Base: pasto con variantes, arena y agua animada
        for (ty in ty0..ty1) {
            for (tx in tx0..tx1) {
                val t = state.tileAt(tx, ty)
                val h = tileHash(tx, ty)
                val bmp = when (t) {
                    GameDef.WATER -> if (((time * 0.6f).toInt() + h) % 2 == 0) tileWater else tileWaterB
                    GameDef.SAND -> tileSand
                    else -> when (h % 3) { 0 -> tileGrass; 1 -> tileGrassB; else -> tileGrassC }
                }
                val left = cam.worldToScreenX(tx * ts)
                val top = cam.worldToScreenY(ty * ts)
                srcRectCache.set(0, 0, bmp.width, bmp.height)
                dstRectF.set(left, top, left + size + 1f, top + size + 1f)
                canvas.drawBitmap(bmp, srcRectCache, dstRectF, bitmapPaint)
            }
        }

        // 2) Costas suaves: esquinas redondeadas, borde húmedo y espuma
        for (ty in ty0..ty1) {
            for (tx in tx0..tx1) {
                val t = state.tileAt(tx, ty)
                if (t == GameDef.WATER) continue
                val left = cam.worldToScreenX(tx * ts)
                val top = cam.worldToScreenY(ty * ts)
                drawShore(canvas, tx, ty, left, top, size, time)
            }
        }

        // 3) Viñeteado de luz suave para dar profundidad
        drawVignette(canvas)

        // Orden por profundidad (Y) para que lo cercano tape lo lejano
        drawList.clear()
        for (e in state.entities) drawList.add(e)
        drawList.sortBy { it.y }
        for (e in drawList) {
            when {
                e.isResource -> drawResource(e, canvas, cam)
                e.isBuilding -> drawBuilding(e, canvas, cam)
                e.isUnit -> drawUnit(e, canvas, cam)
            }
        }
    }

    private val drawList = ArrayList<Entity>()
    private val path = Path()
    private var vignette: android.graphics.Paint? = null

    private fun tileHash(x: Int, y: Int): Int {
        var h = x * 73856093 xor y * 19349663
        h = h xor (h ushr 13)
        return h and 0x7fffffff
    }

    private fun isWater(x: Int, y: Int) = state.tileAt(x, y) == GameDef.WATER

    /** Dibuja el borde de agua sobre un tile de tierra, redondeando las esquinas. */
    private fun drawShore(canvas: Canvas, tx: Int, ty: Int, left: Float, top: Float, size: Float, time: Float) {
        val n = isWater(tx, ty - 1); val s = isWater(tx, ty + 1)
        val w = isWater(tx - 1, ty); val e = isWater(tx + 1, ty)
        val nw = isWater(tx - 1, ty - 1); val ne = isWater(tx + 1, ty - 1)
        val sw = isWater(tx - 1, ty + 1); val se = isWater(tx + 1, ty + 1)
        if (!(n || s || w || e || nw || ne || sw || se)) return

        val r = size * 0.5f          // radio de las esquinas redondeadas
        val bw = size * 0.16f        // ancho del borde mojado
        val foam = 0.5f + 0.5f * kotlin.math.sin(time * 2f + tx * 0.7f + ty * 0.9f)

        // Agua que "muerde" la esquina de la tierra (esquinas convexas de la costa)
        paint.style = Paint.Style.FILL
        paint.color = 0xFF2A7AAE.toInt()
        if (n && w) cornerBite(canvas, left, top, r, 0)
        if (n && e) cornerBite(canvas, left + size, top, r, 1)
        if (s && w) cornerBite(canvas, left, top + size, r, 2)
        if (s && e) cornerBite(canvas, left + size, top + size, r, 3)

        // Borde mojado (arena oscura) y espuma en los lados con agua
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(120, 120, 98, 60)
        if (n) canvas.drawRect(left, top, left + size, top + bw, paint)
        if (s) canvas.drawRect(left, top + size - bw, left + size, top + size, paint)
        if (w) canvas.drawRect(left, top, left + bw, top + size, paint)
        if (e) canvas.drawRect(left + size - bw, top, left + size, top + size, paint)

        paint.color = Color.argb((90 + 90 * foam).toInt(), 255, 255, 255)
        val fw = size * 0.05f
        if (n) canvas.drawRect(left, top, left + size, top + fw, paint)
        if (s) canvas.drawRect(left, top + size - fw, left + size, top + size, paint)
        if (w) canvas.drawRect(left, top, left + fw, top + size, paint)
        if (e) canvas.drawRect(left + size - fw, top, left + size, top + size, paint)

        // Esquinas cóncavas (solo diagonal con agua): pequeño charco redondeado
        paint.color = 0xFF2A7AAE.toInt()
        val q = size * 0.28f
        if (nw && !n && !w) canvas.drawArc(left - q, top - q, left + q, top + q, 0f, 90f, true, paint)
        if (ne && !n && !e) canvas.drawArc(left + size - q, top - q, left + size + q, top + q, 90f, 90f, true, paint)
        if (sw && !s && !w) canvas.drawArc(left - q, top + size - q, left + q, top + size + q, 270f, 90f, true, paint)
        if (se && !s && !e) canvas.drawArc(left + size - q, top + size - q, left + size + q, top + size + q, 180f, 90f, true, paint)
    }

    private val biteRect = RectF()

    /**
     * Esquina convexa de costa: el agua ocupa el cuadrante del tile y se recorta
     * con un arco, dejando la tierra con la esquina redondeada.
     * (cx, cy) es la esquina del tile que toca el agua; corner: 0=NO 1=NE 2=SO 3=SE.
     */
    private fun cornerBite(canvas: Canvas, cx: Float, cy: Float, r: Float, corner: Int) {
        val dx = if (corner == 0 || corner == 2) 1f else -1f   // hacia dentro del tile
        val dy = if (corner == 0 || corner == 1) 1f else -1f
        // Centro del arco: desplazado r hacia el interior del tile
        val ax = cx + dx * r
        val ay = cy + dy * r
        biteRect.set(ax - r, ay - r, ax + r, ay + r)
        val startAngle = when (corner) { 0 -> 180f; 1 -> 270f; 2 -> 90f; else -> 0f }
        path.reset()
        path.moveTo(cx, cy)
        path.lineTo(ax + (if (dx > 0) -r else r), ay)          // borde vertical hasta el arco
        path.arcTo(biteRect, startAngle + (if (corner == 0 || corner == 3) 0f else 0f), 90f)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawVignette(canvas: Canvas) {
        val w = canvas.width.toFloat(); val h = canvas.height.toFloat()
        val p = vignette ?: Paint().also { vignette = it }
        p.shader = RadialGradient(
            w / 2f, h / 2f, kotlin.math.max(w, h) * 0.75f,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(70, 0, 20, 0)),
            floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, p)
    }

    private fun resourceBitmap(kind: Kind, id: Int = 0): Bitmap = when (kind) {
        Kind.TREE -> when (kotlin.math.abs(id) % 3) { 0 -> bmpTree; 1 -> bmpTreeB; else -> bmpTreeC }
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
        val big = if (e.kind == Kind.TREE) 4.2f else 3.0f
        val span = e.radius * big * cam.zoom
        if (sx < -span || sy < -span || sx > canvas.width + span || sy > canvas.height + span) return
        // El sprite se ancla por la base: el tronco queda en el punto del recurso
        val lift = if (e.kind == Kind.TREE) span * 0.34f else span * 0.12f
        drawBitmapCentered(resourceBitmap(e.kind, e.id.hashCode()), canvas, sx, sy - lift, span / 2f, span / 2f)
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

        // Animación: bob al caminar, balanceo al trabajar/talar, embestida al atacar
        var bobY = 0f
        var rot = 0f
        var tilt = 0f
        val t = state.time
        when (e.state) {
            UnitState.MOVING, UnitState.RETURNING -> {
                bobY = abs(kotlin.math.sin(t * 10f + (e.id % 11).toFloat())) * 2.4f
                tilt = kotlin.math.sin(t * 10f + (e.id % 11).toFloat()) * 3f
            }
            UnitState.GATHERING, UnitState.BUILDING -> {
                rot = kotlin.math.sin(t * 7f + (e.id % 13).toFloat()) * 7f
                bobY = (1f - abs(kotlin.math.sin(t * 7f + (e.id % 13).toFloat()))) * 1.2f
            }
            UnitState.ATTACKING -> {
                bobY = kotlin.math.sin(t * 12f) * 1.4f
                rot = kotlin.math.sin(t * 12f) * 2f
            }
            else -> { }
        }
        val bmp = unitBitmap(e.kind, e.team)
        canvas.save()
        canvas.translate(sx, sy - r * 0.5f - bobY * cam.zoom)
        if (e.facing < 0) canvas.scale(-1f, 1f)
        canvas.rotate(tilt + rot * 0.4f)
        srcRectCache.set(0, 0, bmp.width, bmp.height)
        dstRectF.set(-span / 2f, -span / 2f, span / 2f, span / 2f)
        canvas.drawBitmap(bmp, srcRectCache, dstRectF, bitmapPaint)
        canvas.restore()

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
        val prim = state.selectedId == e.id
        if (!prim && !state.selectedIds.contains(e.id)) return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = SELECTION_COLOR
        canvas.drawOval(sx - r, sy - r * 0.55f, sx + r, sy + r * 0.55f, paint)
    }

    fun drawSelectionBox(canvas: Canvas, box: android.graphics.RectF) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(36, 58, 224, 110)
        canvas.drawRect(box, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = 0xFF3AE06A.toInt()
        canvas.drawRect(box, paint)
        paint.style = Paint.Style.FILL
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
