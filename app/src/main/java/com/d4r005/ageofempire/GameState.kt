package com.d4r005.ageofempire

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

class GameState {
    val camera = Camera()
    val resources = HashMap<ResourceType, Int>()
    var entities = ArrayList<Entity>()
    var tiles = IntArray(GameDef.MAP_W * GameDef.MAP_H)
    var selectedId = -1L
    var buildMode: Kind? = null
    var message = ""
    var messageTimer = 0f
    var result = 0          // 0 jugando, 1 victoria, 2 derrota
    var time = 0f

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
        entities.removeAll { it.dead }
        if (selected() == null) selectedId = -1L

        updateEnemyAi(dt)
        checkEnd()
    }

    // ---------------------------------------------------------------- inicio

    fun startNewGame() {
        entities = ArrayList()
        selectedId = -1L
        buildMode = null
        result = 0
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
        val half = if (kind == Kind.HOUSE) 26f else 34f
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
        val sel = selected()
        val mode = buildMode
        if (mode != null) {
            if (sel == null || sel.kind != Kind.VILLAGER) {
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
            sel.state = UnitState.MOVING
            sel.targetId = b.id
            sel.goalX = b.x
            sel.goalY = b.y
            showMessage("Construyendo...")
            return
        }

        val hit = findAt(wx, wy)
        if (sel == null || !sel.isUnit) {
            selectedId = hit?.id ?: -1L
            return
        }
        if (hit == null) {
            sel.state = UnitState.MOVING
            sel.targetId = -1L
            sel.goalX = wx
            sel.goalY = wy
            return
        }
        when {
            hit.id == sel.id -> { }
            hit.isResource && sel.kind == Kind.VILLAGER -> {
                sel.state = UnitState.MOVING
                sel.targetId = hit.id
                sel.goalX = hit.x
                sel.goalY = hit.y
            }
            hit.team != Team.NEUTRAL && hit.team != sel.team -> {
                sel.state = UnitState.ATTACKING
                sel.targetId = hit.id
                sel.goalX = hit.x
                sel.goalY = hit.y
            }
            else -> selectedId = hit.id
        }
    }

    fun startBuildMode(kind: Kind) {
        val sel = selected()
        if (sel == null || sel.kind != Kind.VILLAGER) { showMessage("Selecciona un aldeano"); return }
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
        val sel = selected() ?: return
        if (sel.isUnit) {
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
        if (moveTo(u, gx, gy, dt, maxOf(stop, stopB))) {
            when {
                t == null -> u.state = UnitState.IDLE
                t.isResource -> { u.workTimer = 0f; u.state = UnitState.GATHERING }
                t.isBuilding && t.construction < 1f -> u.state = UnitState.BUILDING
                else -> u.state = UnitState.ATTACKING
            }
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
        if (b.trainQueue.isEmpty()) return
        b.trainTimer += dt
        val kind = b.trainQueue.first()
        if (b.trainTimer >= GameDef.trainTime(kind)) {
            b.trainTimer = 0f
            b.trainQueue.removeAt(0)
            spawnUnit(kind, b.team, b.x + b.halfSize + 24f, b.y + b.halfSize + 10f)
            if (b.team == Team.PLAYER) {
                showMessage(if (kind == Kind.VILLAGER) "Aldeano listo" else "Milicia lista")
            }
        }
    }

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
        showMessage("¡Se acerca una incursión enemiga!")
    }

    private fun checkEnd() {
        val playerHas = entities.any { !it.dead && it.team == Team.PLAYER && it.isBuilding }
        val enemyHas = entities.any { !it.dead && it.team == Team.ENEMY && it.isBuilding }
        if (!enemyHas) result = 1
        else if (!playerHas) result = 2
    }
}
