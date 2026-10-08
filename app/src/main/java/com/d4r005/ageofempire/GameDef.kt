package com.d4r005.ageofempire

object GameDef {
    const val TILE = 64f
    const val MAP_W = 64
    const val MAP_H = 48

    // Terrenos
    const val GRASS = 0
    const val WATER = 1
    const val SAND = 2

    // Recursos iniciales del jugador
    const val START_WOOD = 200
    const val START_FOOD = 200
    const val START_GOLD = 100
    const val START_STONE = 100

    // Costes
    val HOUSE_COST: Map<ResourceType, Int> = mapOf(ResourceType.WOOD to 30)
    val BARRACKS_COST: Map<ResourceType, Int> = mapOf(ResourceType.WOOD to 100)
    val TOWER_COST: Map<ResourceType, Int> = mapOf(ResourceType.WOOD to 30, ResourceType.STONE to 80)
    val WALL_COST: Map<ResourceType, Int> = mapOf(ResourceType.STONE to 10)

    // Torres
    const val TOWER_RANGE = 240f
    const val TOWER_DAMAGE = 12
    const val TOWER_COOLDOWN = 1.2f
    val VILLAGER_COST: Map<ResourceType, Int> = mapOf(ResourceType.FOOD to 50)
    val MILITIA_COST: Map<ResourceType, Int> = mapOf(ResourceType.FOOD to 60, ResourceType.GOLD to 20)

    // Tiempos (segundos)
    const val VILLAGER_TRAIN_TIME = 8f
    const val MILITIA_TRAIN_TIME = 7f
    const val HOUSE_BUILD_TIME = 8f
    const val BARRACKS_BUILD_TIME = 15f
    const val TOWER_BUILD_TIME = 12f
    const val WALL_BUILD_TIME = 2.5f

    // Recolecta
    const val GATHER_INTERVAL = 1.5f
    const val CARRY_CAPACITY = 10

    // Población
    const val POP_PER_TC = 5
    const val POP_PER_HOUSE = 5
    const val POP_MAX = 50

    fun costOf(kind: Kind): Map<ResourceType, Int> = when (kind) {
        Kind.HOUSE -> HOUSE_COST
        Kind.BARRACKS -> BARRACKS_COST
        Kind.TOWER -> TOWER_COST
        Kind.WALL -> WALL_COST
        Kind.VILLAGER -> VILLAGER_COST
        Kind.MILITIA -> MILITIA_COST
        else -> emptyMap()
    }

    fun trainTime(kind: Kind): Float = when (kind) {
        Kind.VILLAGER -> VILLAGER_TRAIN_TIME
        Kind.MILITIA -> MILITIA_TRAIN_TIME
        else -> 10f
    }

    fun buildTime(kind: Kind): Float = when (kind) {
        Kind.HOUSE -> HOUSE_BUILD_TIME
        Kind.BARRACKS -> BARRACKS_BUILD_TIME
        Kind.TOWER -> TOWER_BUILD_TIME
        Kind.WALL -> WALL_BUILD_TIME
        else -> 20f
    }
}
