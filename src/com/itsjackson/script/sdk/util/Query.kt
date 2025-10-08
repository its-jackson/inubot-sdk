package com.itsjackson.script.sdk.util

import org.rspeer.game.adapter.scene.Npc
import org.rspeer.game.adapter.scene.Projectile
import org.rspeer.game.adapter.scene.SceneObject
import org.rspeer.game.component.Inventories
import org.rspeer.game.component.Item
import org.rspeer.game.query.Query
import org.rspeer.game.query.component.ItemQuery
import org.rspeer.game.query.scene.NpcQuery
import org.rspeer.game.query.scene.ProjectileQuery
import org.rspeer.game.query.scene.SceneObjectQuery
import org.rspeer.game.scene.Npcs
import org.rspeer.game.scene.Projectiles
import org.rspeer.game.scene.SceneObjects
import kotlin.apply
import kotlin.collections.firstOrNull
import kotlin.collections.isNotEmpty

fun <T, Q : Query<T, *, *>> Q.any(): Boolean = results().isNotEmpty()
fun <T, Q : Query<T, *, *>> Q.firstOrNull(): T? = results().firstOrNull()

object Query {
    fun backpack(): ItemQuery = Inventories.backpack().query()
    fun equipment(): ItemQuery = Inventories.equipment().query()
    fun bank(): ItemQuery = Inventories.bank().query()
    fun npcs(): NpcQuery = Npcs.query()
    fun sceneObjects(): SceneObjectQuery = SceneObjects.query()
    fun projectiles(): ProjectileQuery = Projectiles.query()

    fun npc(block: NpcQuery.() -> Unit): NpcQuery = npcs().apply(block)
    fun firstNpc(block: NpcQuery.() -> Unit): Npc? = npc(block).firstOrNull()
    fun nearestNpc(block: NpcQuery.() -> Unit): Npc? = npc(block).results().nearest()
    fun randomNpc(block: NpcQuery.() -> Unit): Npc? = npc(block).results().random()
    fun allNpcs(block: NpcQuery.() -> Unit = {}): List<Npc> = npc(block).results().results
    fun anyNpc(block: NpcQuery.() -> Unit): Boolean = npc(block).any()

    fun obj(block: SceneObjectQuery.() -> Unit): SceneObjectQuery = sceneObjects().apply(block)
    fun firstObj(block: SceneObjectQuery.() -> Unit): SceneObject? = obj(block).firstOrNull()
    fun nearestObj(block: SceneObjectQuery.() -> Unit): SceneObject? = obj(block).results().nearest()
    fun randomObj(block: SceneObjectQuery.() -> Unit): SceneObject? = obj(block).results().random()
    fun allObjs(block: SceneObjectQuery.() -> Unit = {}): List<SceneObject> = obj(block).results().results
    fun anyObj(block: SceneObjectQuery.() -> Unit): Boolean = obj(block).any()

    fun item(block: ItemQuery.() -> Unit): ItemQuery = backpack().apply(block)
    fun equipmentItem(block: ItemQuery.() -> Unit): ItemQuery = equipment().apply(block)
    fun bankItem(block: ItemQuery.() -> Unit): ItemQuery = bank().apply(block)

    fun firstItem(block: ItemQuery.() -> Unit): Item? = item(block).results().firstOrNull()
    fun allItems(block: ItemQuery.() -> Unit = {}): List<Item> = item(block).results().results

    fun firstEquipmentItem(block: ItemQuery.() -> Unit): Item? = equipmentItem(block).results().firstOrNull()
    fun allEquipmentItem(block: ItemQuery.() -> Unit = {}): List<Item> = equipmentItem(block).results().results

    fun firstBankItem(block: ItemQuery.() -> Unit): Item? = bankItem(block).results().firstOrNull()
    fun allBankItems(block: ItemQuery.() -> Unit = {}): List<Item> = bankItem(block).results().results

    fun anyItem(block: ItemQuery.() -> Unit): Boolean = item(block).any()
    fun anyEquipmentItem(block: ItemQuery.() -> Unit): Boolean = equipmentItem(block).any()
    fun anyBankItem(block: ItemQuery.() -> Unit): Boolean = bankItem(block).any()

    fun projectile(block: ProjectileQuery.() -> Unit): ProjectileQuery = projectiles().apply(block)
    fun firstProjectile(block: ProjectileQuery.() -> Unit): Projectile? = projectile(block).firstOrNull()
    fun nearestProjectile(block: ProjectileQuery.() -> Unit): Projectile? = projectile(block).results().nearest()
    fun allProjectiles(block: ProjectileQuery.() -> Unit = {}): List<Projectile> = projectile(block).results().results
}
