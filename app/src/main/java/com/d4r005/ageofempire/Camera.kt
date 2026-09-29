package com.d4r005.ageofempire

import kotlin.math.max

class Camera {
    var x = 0f          // esquina superior izquierda en coordenadas de mundo
    var y = 0f
    var zoom = 1f
    var viewW = 0
    var viewH = 0

    fun screenToWorldX(sx: Float): Float = sx / zoom + x
    fun screenToWorldY(sy: Float): Float = sy / zoom + y
    fun worldToScreenX(wx: Float): Float = (wx - x) * zoom
    fun worldToScreenY(wy: Float): Float = (wy - y) * zoom

    fun clamp() {
        val worldW = GameDef.MAP_W * GameDef.TILE
        val worldH = GameDef.MAP_H * GameDef.TILE
        val maxX = max(0f, worldW - viewW / zoom)
        val maxY = max(0f, worldH - viewH / zoom)
        x = x.coerceIn(0f, maxX)
        y = y.coerceIn(0f, maxY)
    }
}
