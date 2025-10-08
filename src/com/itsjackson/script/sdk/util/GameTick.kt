package com.itsjackson.script.sdk.util

import org.rspeer.game.Game

object GameTick {
    fun now() = Game.getTickCount()
    fun since(tick: Int) = now() - tick
    fun nextAction(tick: Int, delay: Int) = tick + delay
}