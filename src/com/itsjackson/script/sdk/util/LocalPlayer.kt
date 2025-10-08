package com.itsjackson.script.sdk.util

import org.rspeer.game.Game
import org.rspeer.game.VarComposite
import org.rspeer.game.Vars
import org.rspeer.game.adapter.scene.Player
import org.rspeer.game.component.tdi.Prayers
import org.rspeer.game.component.tdi.Skill
import org.rspeer.game.component.tdi.Skills
import org.rspeer.game.scene.Players
import kotlin.math.ceil

/**
 * Utility object providing various properties related to the local player instance.
 */
object LocalPlayer {
    private const val POISON_VARP_ID = 102
    private const val DISEASE_VARP_ID = 456
    private const val STAMINA_VARBIT_ID = 25

    /**
     * Gets the current poison setting value from the game's variables.
     */
    private val poisonSetting
        get() = Vars.get(POISON_VARP_ID)

    /**
     * Gets the current disease setting value from the game's variables.
     */
    private val diseaseSetting
        get() = Vars.get(DISEASE_VARP_ID)

    /**
     *
     */
    val self: Player?
        get() = Players.self()

    /**
     *
     */
    val index
        get() = self?.index

    /**
     * Returns the player's maximum health based on their HITPOINTS skill level.
     */
    val maxHealth
        get() = Skills.getLevel(Skill.HITPOINTS)

    /**
     * Returns the player's current health level.
     */
    val currentHealth
        get() = Skills.getCurrentLevel(Skill.HITPOINTS)

    /**
     * Returns the player's current health percentage.
     * If the player is not available, returns [Int.MIN_VALUE].
     */
    val healthPercent
        get() = self?.healthPercent ?: Int.MIN_VALUE

    val prayerPercent
        get() = Prayers.getPercent()

    /**
     * Indicates whether the local player is currently performing an animation.
     */
    val animating
        get() = self?.isAnimating ?: false

    /**
     * Indicates whether the local player is currently moving.
     */
    val moving
        get() = self?.isMoving ?: false

    /**
     * Indicates whether the local player is in a dying state.
     */
    val dying
        get() = self?.isDying ?: false

    /**
     * Returns the combat level of the local player.
     * If the player is not available, returns [Int.MIN_VALUE].
     */
    val combatLvl
        get() = self?.combatLevel ?: Int.MIN_VALUE

    /**
     * Returns the current position of the local player.
     */
    val position
        get() = self?.position

    /**
     *
     */
    val regionId
        get() = position?.regionId

    /**
     *
     */
    val instancePosition
        get() = position?.fromInstance()

    /**
     *
     */
    val instanceRegionId
        get() = instancePosition?.regionId

    /**
     * Checks if the player's stamina is active.
     *
     * @return `true` if stamina is active, otherwise `false`.
     */
    val staminaActive
        get() = Vars.get(Vars.Type.VARBIT, STAMINA_VARBIT_ID) == 1

    /**
     * Indicates whether the local player is currently diseased.
     *
     * @return `true` if diseased, otherwise `false`.
     */
    val diseased
        get() = diseaseSetting > 0

    /**
     * Indicates whether the local player is currently poisoned.
     *
     * @return `true` if poisoned, otherwise `false`.
     */
    val poisoned
        get() = poisonSetting in 1..999999

    /**
     * Indicates whether the local player is currently affected by venom.
     *
     * @return `true` if venomed, otherwise `false`.
     */
    val venomed
        get() = poisonSetting >= 1000000

    /**
     * Indicates whether the local player is immune to poison.
     *
     * @return `true` if poison immune, otherwise `false`.
     */
    val poisonImmune
        get() = poisonSetting == -1

    /**
     * Calculates the current poison damage based on the poison setting.
     *
     * @return the calculated poison damage, or 0 if not poisoned.
     */
    val poisonDamage
        get() = if (!poisoned) 0 else ceil((poisonSetting.toFloat() / 5.0f).toDouble()).toInt()

    /**
     * Calculates the current venom damage based on the poison setting.
     *
     * @return the calculated venom damage, or 0 if not venomed.
     */
    val venomDamage
        get() = if (!venomed) 0 else (poisonSetting - 999997) * 2

    /**
     * Returns the player's current run energy as a percentage.
     */
    val runEnergy
        get() = Game.getClient().energy / 100

    /**
     * Indicates whether the player's run mode is active.
     *
     * @return `true` if run is active, otherwise `false`.
     */
    val runActive
        get() = Vars.get(VarComposite.RUN_ENABLED) == 1
}
