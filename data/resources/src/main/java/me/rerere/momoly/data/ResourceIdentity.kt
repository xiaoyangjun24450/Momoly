package me.rerere.momoly.data

import org.json.JSONObject
import java.util.UUID

const val RESOURCE_ID_FIELD = "momoly_id"

fun newResourceId(): String = UUID.randomUUID().toString()

fun JSONObject.resourceId(): String = optString(RESOURCE_ID_FIELD, "").trim()

fun JSONObject.ensureResourceId(): String = resourceId().ifBlank {
    newResourceId().also { put(RESOURCE_ID_FIELD, it) }
}
