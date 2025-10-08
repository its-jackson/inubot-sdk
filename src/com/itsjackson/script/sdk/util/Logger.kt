package com.itsjackson.script.sdk.util

import org.rspeer.commons.logging.Log

class Logger(var header: String = "") {
    private val format
        get() = "[$header]"

    private fun formatMessage(message: Any?) = "$format $message"

    fun debug(message: Any?) = Log.debug(formatMessage(message))
    fun info(message: Any?) = Log.info(formatMessage(message))
    fun severe(message: Any?) = Log.severe(formatMessage(message))
    fun warn(message: Any?) = Log.warn(formatMessage(message))
}