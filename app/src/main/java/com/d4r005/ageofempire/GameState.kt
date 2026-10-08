package com.d4r005.ageofempire

import org.json.JSONArray
import org.json.JSONObject
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

class GameState {
    val camera = Camera()
    val resources = HashMap<ResourceType, Int>()
    var entities = ArrayList<Entity>()
    var tiles = IntArray(GameDef.MAP_W * GameDef.MAP_H)
    var selectedId = -1L
    val selectedIds = LinkedHashSet<Long>()
    var worldVersion = 0          // cambia cuando se (re)genera el mundo: el minimapa se refresca
    var onSound: ((String) -> Unit)? = null   // el GameView lo conecta al SoundManager
    var buildMode: Kind? = null
    var message = ""
    var messageTimer = 0f
    var result = 0          // 0 jugando, 1 victoria, 2 derrota
    var time = 0f

    val projectiles = ArrayList<Projectile>()
    private var aiTimer = 25f
    private var raids = 0
    private val rnd = Random(System.currentTimeMillis())

    // ---------------------------------------------------------------- ciclo

    fun update(dt: Float) {
        if (result != 0) return
        time += dt
        if (messageTimer > 0f) messageTimer -= dt

        val snapshot = ArrayList(entities)
        for (e in snapshot) {
            if (e.dead) continue
            if (e.isUnit) updateUnit(e, dt)
            else if (e.isBuilding) updateBuilding(e, dt)
        }
        updateProjectiles(dt)
        entities.removeAll { it.dead }
        if (selected() == null) selectedId = -1L
        if (selectedIds.isNotEmpty() && selectedUnits().isEmpty()) selectedIds.clear()

        updateEnemyAi(dt)
        checkEnd()
    }

    // ---------------------------------------------------------------- inicio

    fun startNewGame() {
        entities = ArrayList()
        projectiles.clear()
        selectedId = -1L
        selectedIds.clear()
        buildMode = null
        result = 0
        worldVersion++
        time = 0f
        aiTimer = 25f
        raids = 0
        message = ""
        messageTimer = 0f
        resources[ResourceType.WOOD] = GameDef.START_WOOD
        resources[ResourceType.FOOD] = GameDef.START_FOOD
        resources[ResourceType.GOLD] = GameDef.START_GOLD
        resources[ResourceType.STONE] = GameDef.START_STONE

        generateWorld()

        val pTc = spawnBuilding(Kind.TOWN_CENTER, Team.PLAYER, 7f, 7f)
        val eTc = spawnBuilding(Kind.TOWN_CENTER, Team.ENEMY, GameDef.MAP_W - 8f, GameDef.MAP_H - 8f)
        for (i in 0 until 3) {
            spawnUnit(Kind.VILLAGER, Team.PLAYER, pTc.x + 60f + i * 30f, pTc.y + 120f)
        }
        spawnUnit(Kind.MILITIA, Team.ENEMY, eTc.x - 80f, eTc.y - 80f)
        spawnUnit(Kind.MILITIA, Team.ENEMY, eTc.x - 120f, eTc.y - 40f)

        camera.zoom = 1f
        camera.x = pTc.x - 300f
        camera.y = pTc.y - 250f
        camera.clamp()
        showMessage("Reúne recursos, construye y destruye el centro enemigo")
    }

    // ---------------------------------------------------------------- mundo

    private fun generateWorld() {
        val w = GameDef.MAP_W
        val h = GameDef.MAP_H
        tiles = IntArray(w * h) { GameDef.GRASS }

        // Agua en los bordes
        for (y in 0 until h) for (x in 0 until w) {
            if (x < 2 || y < 2 || x >= w - 2 || y >= h - 2) tiles[y * w + x] = GameDef.WATER
        }
        // Lagos aleatorios
        repeat(3) {
            val cx = rnd.nextInt(6, w - 6)
            val cy = rnd.nextInt(6, h - 6)
            val r = rnd.nextInt(2, 5)
            for (y in cy - r..cy + r) for (x in cx - r..cx + r) {
                if (x in 2 until w - 2 && y in 2 until h - 2 &&
                    (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r
                ) tiles[y * w + x] = GameDef.WATER
            }
        }
        // Zonas de los centros urbanos siempre despejadas
        clearArea(7, 7)
        clearArea(w - 8, h - 8)
        // Arena junto al agua
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            if (tileAt(x, y) == GameDef.GRASS && isNearWater(x, y)) tiles[y * w + x] = GameDef.SAND
        }

        // Bosques
        repeat(9) {
            val cx = rnd.nextInt(5, w - 5)
            val cy = rnd.nextInt(5, h - 5)
            if (tileAt(cx, cy) == GameDef.WATER) return@repeat
            repeat(rnd.nextInt(9, 16)) {
                val tx = cx + rnd.nextInt(-3, 4)
                val ty = cy + rnd.nextInt(-3, 4)
                if (tileAt(tx, ty) == GameDef.GRASS) spawnResource(Kind.TREE, tx, ty, 100)
            }
        }
        // Oro y piedra
        repeat(4) { resourceCluster(Kind.GOLD_MINE, 4, 400) }
        repeat(3) { resourceCluster(Kind.STONE_MINE, 4, 350) }
        // Bayas cerca de cada centro urbano
        berryPatch(7, 7)
        berryPatch(w - 8, h - 8)
        // Un bosque garantizado cerca de cada centro urbano
        forestPatch(7, 7)
        forestPatch(w - 8, h - 8)
    }

    private fun clearArea(cx: Int, cy: Int) {
        for (y in cy - 5..cy + 5) for (x in cx - 5..cx + 5) {
            if (x in 2 until GameDef.MAP_W - 2 && y in 2 until GameDef.MAP_H - 2) tiles[y * GameDef.MAP_W + x] = GameDef.GRASS
        }
    }

    private fun isNearWater(x: Int, y: Int): Boolean =
        tileAt(x - 1, y) == GameDef.WATER || tileAt(x + 1, y) == GameDef.WATER ||
        tileAt(x, y - 1) == GameDef.WATER || tileAt(x, y + 1) == GameDef.WATER

    private fun resourceCluster(kind: Kind, count: Int, amount: Int) {
        val cx = rnd.nextInt(5, GameDef.MAP_W - 5)
        val cy = rnd.nextInt(5, GameDef.MAP_H - 5)
        if (tileAt(cx, cy) == GameDef.WATER) return
        repeat(count) {
            val tx = cx + rnd.nextInt(-1, 2)
            val ty = cy + rnd.nextInt(-1, 2)
            if (tileAt(tx, ty) != GameDef.WATER) spawnResource(kind, tx, ty, amount)
        }
    }

    private fun berryPatch(cx: Int, cy: Int) {
        for (i in 0 until 4) {
            val tx = cx + 4 + rnd.nextInt(-1, 2)
            val ty = cy + rnd.nextInt(-3, 4)
            if (tileAt(tx, ty) == GameDef.GRASS) spawnResource(Kind.BERRY_BUSH, tx, ty, 150)
        }
    }

    private fun forestPatch(cx: Int, cy: Int) {
        for (i in 0 until 10) {
            val tx = cx + rnd.nextInt(-6, 7)
            val ty = cy + rnd.nextInt(4, 8)
            if (tileAt(tx, ty) == GameDef.GRASS) spawnResource(Kind.TREE, tx, ty, 100)
        }
    }

    fun tileAt(tx: Int, ty: Int): Int {
        if (tx < 0 || ty < 0 || tx >= GameDef.MAP_W || ty >= GameDef.MAP_H) return GameDef.WATER
        return tiles[ty * GameDef.MAP_W + tx]
    }

    fun isWalkable(wx: Float, wy: Float): Boolean {
        val tx = (wx / GameDef.TILE).toInt()
        val ty = (wy / GameDef.TILE).toInt()
        return tileAt(tx, ty) != GameDef.WATER
    }

    // ------------------------------------------------------------ pathfinding

    /** Tiles bloqueados: agua, huella de edificios y recursos grandes. */
    private fun buildBlocked(): BooleanArray {
        val w = GameDef.MAP_W; val h = GameDef.MAP_H
        val blocked = BooleanArray(w * h)
        for (i in 0 until w * h) blocked[i] = tiles[i] == GameDef.WATER
        for (e in entities) {
            if (e.dead) continue
            if (e.isBuilding) {
                val half = e.halfSize + 4f
                tileRange(e.x, e.y, half) { tx, ty -> blocked[ty * w + tx] = true }
            } else if (e.kind == Kind.TREE || e.kind == Kind.GOLD_MINE || e.kind == Kind.STONE_MINE) {
                blocked[((e.y / GameDef.TILE).toInt().coerceIn(0, h - 1)) * w +
                        (e.x / GameDef.TILE).toInt().coerceIn(0, w - 1)] = true
            }
        }
        return blocked
    }

    private inline fun tileRange(cx: Float, cy: Float, half: Float, block: (Int, Int) -> Unit) {
        val w = GameDef.MAP_W; val h = GameDef.MAP_H
        val x0 = ((cx - half) / GameDef.TILE).toInt().coerceIn(0, w - 1)
        val x1 = ((cx + half) / GameDef.TILE).toInt().coerceIn(0, w - 1)
        val y0 = ((cy - half) / GameDef.TILE).toInt().coerceIn(0, h - 1)
        val y1 = ((cy + half) / GameDef.TILE).toInt().coerceIn(0, h - 1)
        for (ty in y0..y1) for (tx in x0..x1) block(tx, ty)
    }

    /**
     * A* por tiles. Devuelve waypoints en coordenadas de mundo (centro de tile).
     * Si el destino está bloqueado (recurso/edificio), la ruta termina en un tile
     * adyacente y el resto del approach lo hace la lógica de cada unidad.
     */
    fun findPath(fromX: Float, fromY: Float, toX: Float, toY: Float): ArrayList<Pair<Float, Float>> {
        val w = GameDef.MAP_W; val h = GameDef.MAP_H
        val blocked = buildBlocked()
        val sx = (fromX / GameDef.TILE).toInt().coerceIn(0, w - 1)
        val sy = (fromY / GameDef.TILE).toInt().coerceIn(0, h - 1)
        var gx = (toX / GameDef.TILE).toInt().coerceIn(0, w - 1)
        var gy = (toY / GameDef.TILE).toInt().coerceIn(0, h - 1)
        if (blocked[gy * w + gx]) {
            val alt = nearestOpenAround(gx, gy, blocked) ?: return ArrayList()
            gx = alt.first; gy = alt.second
        }
        if (blocked[sy * w + sx] || (sx == gx && sy == gy)) return ArrayList()

        val gScore = IntArray(w * h) { Int.MAX_VALUE }
        val parent = IntArray(w * h) { -1 }
        val open = PriorityQueue<Int>(compareBy { gScore[it] + hcost(it, gx, gy, w) })
        gScore[sy * w + sx] = 0
        open.add(sy * w + sx)

        var expanded = 0
        val goalIdx = gy * w + gx
        while (open.isNotEmpty() && expanded < 6000) {
            val cur = open.poll()
            if (cur == goalIdx) break
            expanded++
            val cx = cur % w; val cy = cur / w
            for (dir in DIRS) {
                val nx = cx + dir.first; val ny = cy + dir.second
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val ni = ny * w + nx
                if (blocked[ni]) continue
                val ng = gScore[cur] + if (dir.first != 0 && dir.second != 0) 14 else 10
                if (ng < gScore[ni]) {
                    gScore[ni] = ng
                    parent[ni] = cur
                    open.add(ni)
                }
            }
        }
        if (parent[goalIdx] == -1 && goalIdx != sy * w + sx) return ArrayList()

        // Reconstruir ruta (del goal al inicio) y suavizar quitando colineales
        val rev = ArrayList<Int>()
        var cur = goalIdx
        while (cur != -1) { rev.add(cur); cur = parent[cur] }
        rev.reverse()
        val out = ArrayList<Pair<Float, Float>>()
        var lastDx = 99; var lastDy = 99
        for (i in 1 until rev.size) {
            val p = rev[i]; val prev = rev[i - 1]
            val dx = (p % w) - (prev % w); val dy = (p / w) - (prev / w)
            if (out.isNotEmpty() && dx == lastDx && dy == lastDy) {
                out[out.size - 1] = tileCenter(p % w, p / w)
            } else {
                out.add(tileCenter(p % w, p / w))
            }
            lastDx = dx; lastDy = dy
        }
        return out
    }

    private fun hcost(idx: Int, gx: Int, gy: Int, w: Int): Int {
        val dx = abs(idx % w - gx); val dy = abs(idx / w - gy)
        return (max(dx, dy) + min(dx, dy) / 2) * 10
    }

    private fun tileCenter(tx: Int, ty: Int): Pair<Float, Float> =
        Pair((tx + 0.5f) * GameDef.TILE, (ty + 0.5f) * GameDef.TILE)

    private fun nearestOpenAround(tx: Int, ty: Int, blocked: BooleanArray): Pair<Int, Int>? {
        val w = GameDef.MAP_W; val h = GameDef.MAP_H
        for (r in 1..4) {
            for (dy in -r..r) for (dx in -r..r) {
                if (max(abs(dx), abs(dy)) != r) continue
                val nx = tx + dx; val ny = ty + dy
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                if (!blocked[ny * w + nx]) return Pair(nx, ny)
            }
        }
        return null
    }

    // ------------------------------------------------------------- spawns

    private fun spawnUnit(kind: Kind, team: Team, x: Float, y: Float): Entity {
        val u = Entity(Entity.newId(), kind, team, x, y)
        when (kind) {
            Kind.VILLAGER -> { u.maxHp = 25; u.speed = 65f; u.radius = 9f; u.damage = 3 }
            Kind.MILITIA -> { u.maxHp = 40; u.speed = 75f; u.radius = 10f; u.damage = 8 }
            else -> {}
        }
        u.hp = u.maxHp
        entities.add(u)
        return u
    }

    private fun spawnBuilding(kind: Kind, team: Team, tileX: Float, tileY: Float, construction: Float = 1f): Entity {
        val b = Entity(
            Entity.newId(), kind, team,
            (tileX + 0.5f) * GameDef.TILE, (tileY + 0.5f) * GameDef.TILE
        )
        when (kind) {
            Kind.TOWN_CENTER -> { b.maxHp = 1000; b.halfSize = 48f }
            Kind.HOUSE -> { b.maxHp = 250; b.halfSize = 26f }
            Kind.BARRACKS -> { b.maxHp = 500; b.halfSize = 34f }
            Kind.TOWER -> { b.maxHp = 400; b.halfSize = 22f }
            Kind.WALL -> { b.maxHp = 300; b.halfSize = 28f }
            else -> {}
        }
        b.construction = construction
        b.hp = if (construction >= 1f) b.maxHp else (b.maxHp * construction).toInt().coerceAtLeast(1)
        entities.add(b)
        return b
    }

    private fun spawnResource(kind: Kind, tileX: Int, tileY: Int, amount: Int): Entity {
        val r = Entity(
            Entity.newId(), kind, Team.NEUTRAL,
            (tileX + 0.5f) * GameDef.TILE, (tileY + 0.5f) * GameDef.TILE
        )
        r.amount = amount
        when (kind) {
            Kind.TREE -> { r.radius = 16f; r.resourceType = ResourceType.WOOD }
            Kind.GOLD_MINE -> { r.radius = 14f; r.resourceType = ResourceType.GOLD }
            Kind.STONE_MINE -> { r.radius = 14f; r.resourceType = ResourceType.STONE }
            Kind.BERRY_BUSH -> { r.radius = 11f; r.resourceType = ResourceType.FOOD }
            else -> {}
        }
        entities.add(r)
        return r
    }

    // ------------------------------------------------------------ consultas

    fun selected(): Entity? = entityById(selectedId)

    /** Todas las unidades del jugador seleccionadas (para órdenes en grupo). */
    fun selectedUnits(): List<Entity> =
        selectedIds.mapNotNull { id -> entityById(id) }.filter { it.isUnit && it.team == Team.PLAYER }

    fun selectSingle(e: Entity?) {
        selectedIds.clear()
        if (e != null && !e.dead) selectedIds.add(e.id)
        selectedId = e?.id ?: -1L
    }

    /** Selección por caja: solo unidades propias. */
    fun boxSelect(wx0: Float, wy0: Float, wx1: Float, wy1: Float) {
        val x0 = min(wx0, wx1); val x1 = max(wx0, wx1)
        val y0 = min(wy0, wy1); val y1 = max(wy0, wy1)
        val inside = entities.filter {
            !it.dead && it.isUnit && it.team == Team.PLAYER &&
            it.x >= x0 && it.x <= x1 && it.y >= y0 && it.y <= y1
        }
        selectedIds.clear()
        selectedIds.addAll(inside.map { it.id })
        selectedId = inside.firstOrNull()?.id ?: -1L
    }

    fun entityById(id: Long): Entity? = entities.firstOrNull { it.id == id && !it.dead }

    fun popCount(team: Team): Int {
        var n = 0
        for (e in entities) if (e.isUnit && e.team == team && !e.dead) n++
        return n
    }

    fun popCap(team: Team): Int {
        var cap = 0
        for (e in entities) {
            if (e.dead || e.team != team || e.construction < 1f) continue
            when (e.kind) {
                Kind.TOWN_CENTER -> cap += GameDef.POP_PER_TC
                Kind.HOUSE -> cap += GameDef.POP_PER_HOUSE
                else -> {}
            }
        }
        return min(cap, GameDef.POP_MAX)
    }

    fun findAt(wx: Float, wy: Float): Entity? {
        var best: Entity? = null
        var bestD = Float.MAX_VALUE
        for (e in entities) {
            if (e.dead || !e.isUnit) continue
            val d = sqrt((e.x - wx) * (e.x - wx) + (e.y - wy) * (e.y - wy))
            if (d <= e.radius + 16f && d < bestD) { best = e; bestD = d }
        }
        if (best != null) return best
        for (e in entities) {
            if (e.dead || !e.isBuilding) continue
            if (abs(wx - e.x) <= e.halfSize + 8f && abs(wy - e.y) <= e.halfSize + 8f) return e
        }
        for (e in entities) {
            if (e.dead || !e.isResource) continue
            val d = sqrt((e.x - wx) * (e.x - wx) + (e.y - wy) * (e.y - wy))
            if (d <= e.radius + 10f) return e
        }
        return null
    }

    fun canAfford(cost: Map<ResourceType, Int>): Boolean {
        for ((type, n) in cost) {
            if ((resources[type] ?: 0) < n) return false
        }
        return true
    }

    private fun pay(cost: Map<ResourceType, Int>) {
        for ((type, n) in cost) resources[type] = (resources[type] ?: 0) - n
    }

    fun canPlace(kind: Kind, wx: Float, wy: Float): Boolean {
        val half = when (kind) {
            Kind.HOUSE -> 26f
            Kind.BARRACKS -> 34f
            Kind.TOWER -> 22f
            Kind.WALL -> 28f
            else -> 26f
        }
        val d = half * 0.9f
        if (!isWalkable(wx - d, wy - d) || !isWalkable(wx + d, wy - d) ||
            !isWalkable(wx - d, wy + d) || !isWalkable(wx + d, wy + d)) return false
        for (e in entities) {
            if (e.dead) continue
            if (e.isBuilding && abs(e.x - wx) < e.halfSize + half + 6f && abs(e.y - wy) < e.halfSize + half + 6f) return false
            if (e.isResource && abs(e.x - wx) < e.radius + half + 4f && abs(e.y - wy) < e.radius + half + 4f) return false
        }
        return true
    }

    private fun dist(a: Entity, b: Entity): Float =
        sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))

    fun nearestDropPoint(u: Entity): Entity? {
        var best: Entity? = null
        var bestD = Float.MAX_VALUE
        for (e in entities) {
            if (e.dead || e.kind != Kind.TOWN_CENTER || e.team != u.team || e.construction < 1f) continue
            val d = dist(u, e)
            if (d < bestD) { best = e; bestD = d }
        }
        return best
    }

    fun nearestResource(u: Entity, type: ResourceType, maxDist: Float): Entity? {
        var best: Entity? = null
        var bestD = Float.MAX_VALUE
        for (e in entities) {
            if (e.dead || !e.isResource || e.resourceType != type || e.amount <= 0) continue
            val d = dist(u, e)
            if (d <= maxDist && d < bestD) { best = e; bestD = d }
        }
        return best
    }

    fun nearestEnemy(u: Entity, maxDist: Float): Entity? {
        var best: Entity? = null
        var bestD = Float.MAX_VALUE
        for (e in entities) {
            if (e.dead || e.team == Team.NEUTRAL || e.team == u.team) continue
            val d = dist(u, e)
            if (d <= maxDist && d < bestD) { best = e; bestD = d }
        }
        return best
    }

    fun showMessage(text: String) {
        message = text
        messageTimer = 2.5f
    }

    // ------------------------------------------------------------- comandos

    fun tapWorld(wx: Float, wy: Float) {
        if (result != 0) return
        val mode = buildMode
        if (mode != null) {
            val builder = selected()
            if (builder == null || builder.kind != Kind.VILLAGER || builder.team != Team.PLAYER) {
                buildMode = null
                showMessage("Selecciona un aldeano para construir")
                return
            }
            val cost = GameDef.costOf(mode)
            if (!canPlace(mode, wx, wy)) { showMessage("No se puede construir ahí"); return }
            if (!canAfford(cost)) { buildMode = null; showMessage("Recursos insuficientes"); return }
            pay(cost)
            val b = spawnBuilding(mode, Team.PLAYER, wx / GameDef.TILE - 0.5f, wy / GameDef.TILE - 0.5f, 0f)
            buildMode = null
            orderUnit(builder, wx, wy, b)
            showMessage("Construyendo...")
            return
        }

        val hit = findAt(wx, wy)
        val units = selectedUnits()
        if (units.isEmpty()) {
            selectSingle(hit)
            return
        }

        if (hit == null) {
            // Mover en formación alrededor del punto
            val offsets = formationOffsets(units.size)
            for (i in units.indices) {
                orderMove(units[i], wx + offsets[i].first, wy + offsets[i].second)
            }
            return
        }

        if (hit.team == Team.PLAYER) {
            // Tocar algo propio lo selecciona
            selectSingle(hit)
            return
        }
        // Orden a todo el grupo según el objetivo
        for (u in units) {
            when {
                hit.isResource && u.kind == Kind.VILLAGER -> orderUnit(u, hit.x, hit.y, hit)
                hit.team != Team.PLAYER -> {
                    u.state = UnitState.ATTACKING
                    u.targetId = hit.id
                    u.path.clear(); u.pathFailed = false
                }
                hit.isBuilding && hit.construction < 1f && u.kind == Kind.VILLAGER -> orderUnit(u, hit.x, hit.y, hit)
                else -> orderMove(u, wx, wy)
            }
        }
    }

    /** Orden de movimiento/ataque/colecta a un objetivo concreto. */
    private fun orderUnit(u: Entity, wx: Float, wy: Float, target: Entity) {
        u.state = UnitState.MOVING
        u.targetId = target.id
        u.goalX = wx; u.goalY = wy
        u.path.clear(); u.pathFailed = false
    }

    private fun orderMove(u: Entity, wx: Float, wy: Float) {
        u.state = UnitState.MOVING
        u.targetId = -1L
        u.goalX = wx; u.goalY = wy
        u.path.clear(); u.pathFailed = false
    }

    /** Posiciones en anillo alrededor del punto para no apilar N unidades. */
    private fun formationOffsets(n: Int): ArrayList<Pair<Float, Float>> {
        val out = ArrayList<Pair<Float, Float>>()
        out.add(Pair(0f, 0f))
        var ring = 1
        while (out.size < n) {
            val r = ring
            val step = 34f
            for (i in 0 until r * 6) {
                val a = i * (Math.PI * 2 / (r * 6))
                out.add(Pair((r * step * Math.cos(a)).toFloat(), (r * step * Math.sin(a)).toFloat()))
                if (out.size >= n) break
            }
            ring++
        }
        return out
    }

    fun startBuildMode(kind: Kind) {
        val sel = selected()
        if (sel == null || sel.kind != Kind.VILLAGER || sel.team != Team.PLAYER) {
            showMessage("Selecciona un aldeano"); return
        }
        if (!canAfford(GameDef.costOf(kind))) { showMessage("Recursos insuficientes"); return }
        buildMode = kind
    }

    fun tryQueueUnit(building: Entity, kind: Kind) {
        if (result != 0) return
        if (building.team != Team.PLAYER) { showMessage("Ese edificio no es tuyo"); return }
        if (building.construction < 1f) { showMessage("El edificio está en construcción"); return }
        val inQueue = building.trainQueue.size
        if (popCount(Team.PLAYER) + inQueue >= popCap(Team.PLAYER)) {
            showMessage("Población al máxima: construye casas")
            return
        }
        val cost = GameDef.costOf(kind)
        if (!canAfford(cost)) { showMessage("Recursos insuficientes"); return }
        pay(cost)
        building.trainQueue.add(kind)
    }

    fun stopSelected() {
        for (u in selectedUnits()) {
            u.state = UnitState.IDLE
            u.targetId = -1L
            u.path.clear()
        }
        val sel = selected()
        if (sel != null && sel.isUnit) {
            sel.state = UnitState.IDLE
            sel.targetId = -1L
        }
    }

    // ------------------------------------------------------------ simulación

    private fun updateUnit(u: Entity, dt: Float) {
        when (u.state) {
            UnitState.IDLE -> { }
            UnitState.MOVING -> updateMoving(u, dt)
            UnitState.GATHERING -> updateGathering(u, dt)
            UnitState.RETURNING -> updateReturning(u, dt)
            UnitState.BUILDING -> updateBuildingWork(u, dt)
            UnitState.ATTACKING -> updateAttacking(u, dt)
        }
    }

    private fun moveTo(u: Entity, gx: Float, gy: Float, dt: Float, stopDist: Float): Boolean {
        val dx = gx - u.x
        val dy = gy - u.y
        val d = sqrt(dx * dx + dy * dy)
        if (d <= stopDist) return true
        if (abs(dx) > 1f) u.facing = if (dx > 0) 1 else -1
        val step = min(d - stopDist, u.speed * dt)
        val nx = u.x + dx / d * step
        val ny = u.y + dy / d * step
        if (isWalkable(nx, ny)) { u.x = nx; u.y = ny }
        else if (isWalkable(nx, u.y)) { u.x = nx }
        else if (isWalkable(u.x, ny)) { u.y = ny }
        return false
    }

    private fun updateMoving(u: Entity, dt: Float) {
        val t = if (u.targetId != -1L) entityById(u.targetId) else null
        val gx = t?.x ?: u.goalX
        val gy = t?.y ?: u.goalY
        val stop = if (t != null) t.radius + u.radius + 4f else 6f
        val stopB = if (t != null && t.isBuilding) t.halfSize + u.radius + 6f else stop
        val stopDist = maxOf(stop, stopB)
        if (sqrt((gx - u.x) * (gx - u.x) + (gy - u.y) * (gy - u.y)) <= stopDist) {
            // llegó
            when {
                t == null -> u.state = UnitState.IDLE
                t.isResource -> { u.workTimer = 0f; u.state = UnitState.GATHERING }
                t.isBuilding && t.construction < 1f -> u.state = UnitState.BUILDING
                else -> u.state = UnitState.ATTACKING
            }
            return
        }
        // sigue la ruta calculada
        if (u.path.isNotEmpty()) {
            val wp = u.path.first()
            if (moveTo(u, wp.first, wp.second, dt, 8f)) u.path.removeAt(0)
            return
        }
        if (u.pathFailed) {
            moveTo(u, gx, gy, dt, stopDist)   // sin ruta posible: línea recta como antes
            return
        }
        // primera vez: calcular A*
        val route = findPath(u.x, u.y, gx, gy)
        if (route.isEmpty()) {
            u.pathFailed = true
            moveTo(u, gx, gy, dt, stopDist)
        } else {
            u.path.addAll(route)
        }
    }

    private fun startReturning(u: Entity) {
        val tc = nearestDropPoint(u)
        if (tc == null) { u.state = UnitState.IDLE; u.targetId = -1L; return }
        u.state = UnitState.RETURNING
        u.targetId = tc.id
    }

    private fun updateGathering(u: Entity, dt: Float) {
        val t = entityById(u.targetId)
        if (u.carryAmount >= GameDef.CARRY_CAPACITY) {
            startReturning(u)
            return
        }
        if (t == null || t.dead || t.amount <= 0) {
            val type = t?.resourceType ?: u.carryType
            if (u.carryAmount > 0) { startReturning(u); return }
            val node = if (type != null) nearestResource(u, type, 700f) else null
            if (node != null) {
                u.state = UnitState.MOVING
                u.targetId = node.id
                u.path.clear(); u.pathFailed = false
            } else {
                u.state = UnitState.IDLE
                u.targetId = -1L
            }
            return
        }
        u.workTimer += dt
        if (u.workTimer >= GameDef.GATHER_INTERVAL) {
            u.workTimer = 0f
            val take = min(1, t.amount)
            t.amount -= take
            u.carryType = t.resourceType
            u.carryAmount += take
            if (u.team == Team.PLAYER) sfx("chop")
            if (t.amount <= 0) t.dead = true
        }
    }

    private fun updateReturning(u: Entity, dt: Float) {
        val t = entityById(u.targetId)
        if (t == null) { u.state = UnitState.IDLE; u.targetId = -1L; return }
        if (moveTo(u, t.x, t.y, dt, t.halfSize + u.radius + 6f)) {
            val type = u.carryType
            if (type != null && u.carryAmount > 0) {
                resources[type] = (resources[type] ?: 0) + u.carryAmount
            }
            u.carryAmount = 0
            val node = if (type != null) nearestResource(u, type, 700f) else null
            if (node != null) {
                u.state = UnitState.MOVING
                u.targetId = node.id
                u.path.clear(); u.pathFailed = false
            } else {
                u.state = UnitState.IDLE
                u.targetId = -1L
            }
        }
    }

    private fun updateBuildingWork(u: Entity, dt: Float) {
        val t = entityById(u.targetId)
        if (t == null || t.dead || t.construction >= 1f) {
            u.state = UnitState.IDLE
            u.targetId = -1L
            return
        }
        if (moveTo(u, t.x, t.y, dt, t.halfSize + u.radius + 8f)) {
            t.construction += dt / GameDef.buildTime(t.kind)
            if (t.construction >= 1f) {
                t.construction = 1f
                t.hp = t.maxHp
                if (t.team == Team.PLAYER) sfx("build_done")
                showMessage("Construcción terminada")
            } else {
                t.hp = (t.maxHp * t.construction).toInt().coerceAtLeast(1)
            }
        }
    }

    private fun updateAttacking(u: Entity, dt: Float) {
        val t = entityById(u.targetId)
        if (t == null || t.dead) {
            // Adquirir nuevo objetivo cercano (los enemigos acosan)
            val next = nearestEnemy(u, if (u.team == Team.ENEMY) 900f else 220f)
            if (next != null) {
                u.targetId = next.id
            } else {
                u.state = UnitState.IDLE
                u.targetId = -1L
            }
            return
        }
        val range = u.radius + (if (t.isBuilding) t.halfSize else t.radius) + 12f
        val d = sqrt((t.x - u.x) * (t.x - u.x) + (t.y - u.y) * (t.y - u.y))
        if (d > range) {
            moveTo(u, t.x, t.y, dt, range)
        } else {
            u.attackCd -= dt
            if (u.attackCd <= 0f) {
                u.attackCd = 1f
                t.hp -= u.damage
                if (u.team == Team.PLAYER) sfx("hit")
                if (t.hp <= 0) {
                    t.hp = 0
                    t.dead = true
                    if (t.isBuilding && t.kind == Kind.TOWN_CENTER && t.team == Team.PLAYER) {
                        showMessage("¡Tu centro urbano ha caído!")
                    }
                }
            }
        }
    }

    private fun updateBuilding(b: Entity, dt: Float) {
        if (b.construction < 1f) return
        if (b.kind == Kind.TOWER) { updateTower(b, dt); return }
        if (b.trainQueue.isEmpty()) return
        b.trainTimer += dt
        val kind = b.trainQueue.first()
        if (b.trainTimer >= GameDef.trainTime(kind)) {
            b.trainTimer = 0f
            b.trainQueue.removeAt(0)
            spawnUnit(kind, b.team, b.x + b.halfSize + 24f, b.y + b.halfSize + 10f)
            if (b.team == Team.PLAYER) {
                sfx("unit_ready")
                showMessage(if (kind == Kind.VILLAGER) "Aldeano listo" else "Milicia lista")
            }
        }
    }

    /** La torre dispara flechas a enemigos en rango (defensa pasiva). */
    private fun updateTower(b: Entity, dt: Float) {
        b.attackCd -= dt
        if (b.attackCd > 0f) return
        val target = nearestEnemy(b, GameDef.TOWER_RANGE)
        if (target == null) { b.attackCd = 0.15f; return }
        b.attackCd = GameDef.TOWER_COOLDOWN
        projectiles.add(Projectile(b.x, b.y - b.halfSize * 1.2f, target.id, target.x, target.y, GameDef.TOWER_DAMAGE))
        if (b.team == Team.PLAYER) sfx("arrow")
    }

    private fun updateProjectiles(dt: Float) {
        val it = projectiles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            val t = entityById(p.targetId)
            if (t != null) { p.tx = t.x; p.ty = t.y }
            val dx = p.tx - p.x
            val dy = p.ty - p.y
            val d = sqrt(dx * dx + dy * dy)
            if (d <= 14f) {
                if (t != null && !t.dead) {
                    t.hp -= p.damage
                    if (t.hp <= 0) { t.hp = 0; t.dead = true }
                    if (t.team == Team.PLAYER) sfx("hit")
                }
                it.remove()
                continue
            }
            p.x += dx / d * p.speed * dt
            p.y += dy / d * p.speed * dt
        }
    }

    private fun sfx(name: String) { onSound?.invoke(name) }

    // ------------------------------------------------------------ IA enemiga

    private fun updateEnemyAi(dt: Float) {
        aiTimer -= dt
        if (aiTimer > 0f) return
        raids++
        aiTimer = (45f - raids * 2f).coerceIn(20f, 45f)

        val eTc = entities.firstOrNull {
            !it.dead && it.team == Team.ENEMY && it.kind == Kind.TOWN_CENTER
        } ?: return

        val count = min(1 + raids, 6)
        for (i in 0 until count) {
            val m = spawnUnit(
                Kind.MILITIA, Team.ENEMY,
                eTc.x - 80f - i * 30f, eTc.y - 60f + i * 20f
            )
            val target = nearestEnemy(m, 100000f)
            if (target != null) {
                m.state = UnitState.ATTACKING
                m.targetId = target.id
            }
        }
        sfx("raid")
        showMessage("¡Se acerca una incursión enemiga!")
    }

    private fun checkEnd() {
        val playerHas = entities.any { !it.dead && it.team == Team.PLAYER && it.isBuilding }
        val enemyHas = entities.any { !it.dead && it.team == Team.ENEMY && it.isBuilding }
        if (!enemyHas) { if (result == 0) sfx("win"); result = 1 }
        else if (!playerHas) { if (result == 0) sfx("lose"); result = 2 }
    }


    // ------------------------------------------------------------- guardado

    fun toJsonString(): String {
        val root = JSONObject()
        root.put("v", 1)
        root.put("time", time.toDouble())
        root.put("raids", raids)
        root.put("aiTimer", aiTimer.toDouble())
        root.put("sel", selectedId)
        root.put("camX", camera.x.toDouble())
        root.put("camY", camera.y.toDouble())
        root.put("zoom", camera.zoom.toDouble())
        val res = JSONObject()
        for ((k, v) in resources) res.put(k.name, v)
        root.put("res", res)
        val tl = JSONArray()
        for (t in tiles) tl.put(t)
        root.put("tiles", tl)
        val es = JSONArray()
        for (e in entities) {
            val o = JSONObject()
            o.put("id", e.id)
            o.put("k", e.kind.name); o.put("t", e.team.name)
            o.put("x", e.x.toDouble()); o.put("y", e.y.toDouble())
            o.put("hp", e.hp); o.put("mhp", e.maxHp)
            o.put("r", e.radius.toDouble()); o.put("hs", e.halfSize.toDouble())
            o.put("st", e.state.name); o.put("tid", e.targetId)
            o.put("gx", e.goalX.toDouble()); o.put("gy", e.goalY.toDouble())
            o.put("cd", e.attackCd.toDouble())
            o.put("ct", e.carryType?.name ?: "")
            o.put("ca", e.carryAmount)
            o.put("wt", e.workTimer.toDouble())
            o.put("am", e.amount)
            o.put("rt", e.resourceType?.name ?: "")
            o.put("con", e.construction.toDouble())
            o.put("tt", e.trainTimer.toDouble())
            val q = JSONArray()
            for (k in e.trainQueue) q.put(k.name)
            o.put("tq", q)
            es.put(o)
        }
        root.put("ent", es)
        return root.toString()
    }

    fun loadFromString(json: String): Boolean {
        try {
            val root = JSONObject(json)
            if (root.optInt("v") != 1) return false
            time = root.getDouble("time").toFloat()
            raids = root.getInt("raids")
            aiTimer = root.getDouble("aiTimer").toFloat()
            val res = root.getJSONObject("res")
            resources.clear()
            for (key in res.keys()) resources[ResourceType.valueOf(key)] = res.getInt(key)

            val tl = root.getJSONArray("tiles")
            require(tl.length() == tiles.size)
            for (i in 0 until tl.length()) tiles[i] = tl.getInt(i)

            entities = ArrayList()
            val es = root.getJSONArray("ent")
            var maxId = 0L
            for (i in 0 until es.length()) {
                val o = es.getJSONObject(i)
                val e = Entity(
                    o.getLong("id"),
                    Kind.valueOf(o.getString("k")),
                    Team.valueOf(o.getString("t")),
                    o.getDouble("x").toFloat(),
                    o.getDouble("y").toFloat()
                )
                maxId = max(maxId, e.id)
                e.hp = o.getInt("hp"); e.maxHp = o.getInt("mhp")
                e.radius = o.getDouble("r").toFloat()
                e.halfSize = o.getDouble("hs").toFloat()
                e.state = UnitState.valueOf(o.getString("st"))
                e.targetId = o.getLong("tid")
                e.goalX = o.getDouble("gx").toFloat(); e.goalY = o.getDouble("gy").toFloat()
                e.attackCd = o.getDouble("cd").toFloat()
                val ct = o.getString("ct"); e.carryType = if (ct.isEmpty()) null else ResourceType.valueOf(ct)
                e.carryAmount = o.getInt("ca")
                e.workTimer = o.getDouble("wt").toFloat()
                e.amount = o.getInt("am")
                val rt = o.getString("rt"); e.resourceType = if (rt.isEmpty()) null else ResourceType.valueOf(rt)
                e.construction = o.getDouble("con").toFloat()
                e.trainTimer = o.getDouble("tt").toFloat()
                val q = o.getJSONArray("tq")
                for (j in 0 until q.length()) e.trainQueue.add(Kind.valueOf(q.getString(j)))
                e.speed = if (e.kind == Kind.VILLAGER) 65f else if (e.kind == Kind.MILITIA) 75f else 0f
                e.damage = when (e.kind) { Kind.VILLAGER -> 3; Kind.MILITIA -> 8; else -> 0 }
                e.dead = false
                entities.add(e)
            }
            Entity.syncNextId(maxId)
            selectedId = root.getLong("sel")
            selectedIds.clear()
            if (selectedId != -1L) selectedIds.add(selectedId)
            camera.x = root.getDouble("camX").toFloat()
            camera.y = root.getDouble("camY").toFloat()
            camera.zoom = root.getDouble("zoom").toFloat()
            camera.clamp()
            buildMode = null
            result = 0
            message = ""; messageTimer = 0f
            worldVersion++
            return true
        } catch (e: Exception) {
            return false
        }
    }

    companion object {
        val DIRS = listOf(
            Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1),
            Pair(1, 1), Pair(1, -1), Pair(-1, 1), Pair(-1, -1)
        )
    }
}
