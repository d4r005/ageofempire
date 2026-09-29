package com.d4r005.ageofempire

import java.util.concurrent.atomic.AtomicLong

enum class Kind { VILLAGER, MILITIA, TOWN_CENTER, HOUSE, BARRACKS, TREE, GOLD_MINE, STONE_MINE, BERRY_BUSH }

enum class Team { PLAYER, ENEMY, NEUTRAL }

enum class ResourceType { WOOD, FOOD, GOLD, STONE }

enum class UnitState { IDLE, MOVING, GATHERING, RETURNING, BUILDING, ATTACKING }

class Entity(
    val id: Long,
    val kind: Kind,
    val team: Team,
    var x: Float,
    var y: Float
) {
    companion object {
        private val nextId = AtomicLong(1)
        fun newId(): Long = nextId.getAndIncrement()
    }

    // Común
    var hp = 1
    var maxHp = 1
    var radius = 12f
    var halfSize = 0f          // edificios: mitad de la huella en píxeles
    var dead = false

    // Unidades
    var speed = 0f
    var damage = 0
    var state = UnitState.IDLE
    var targetId = -1L
    var goalX = 0f
    var goalY = 0f
    var attackCd = 0f
    var carryType: ResourceType? = null
    var carryAmount = 0
    var workTimer = 0f

    // Recursos
    var amount = 0
    var resourceType: ResourceType? = null

    // Edificios
    var construction = 1f      // 0..1, <1 = en construcción
    var trainQueue = ArrayList<Kind>()
    var trainTimer = 0f

    val isUnit: Boolean
        get() = kind == Kind.VILLAGER || kind == Kind.MILITIA

    val isBuilding: Boolean
        get() = kind == Kind.TOWN_CENTER || kind == Kind.HOUSE || kind == Kind.BARRACKS

    val isResource: Boolean
        get() = kind == Kind.TREE || kind == Kind.GOLD_MINE || kind == Kind.STONE_MINE || kind == Kind.BERRY_BUSH
}
