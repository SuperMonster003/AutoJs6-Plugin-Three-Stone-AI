package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * Gemini accepts only its OpenAPI subset in `functionDeclarations[].parameters` and `responseSchema`; any other
 * JSON Schema keyword makes the whole request fail with HTTP 400 ("Unknown name \"additionalProperties\" ...").
 * Callers keep validating tool arguments and structured replies against their full schema, so dropping the
 * unsupported constraints here changes nothing about what is accepted afterwards.
 */
internal object GeminiSchemas {
    private val KEPT = setOf(
        "type", "format", "title", "description", "nullable", "enum", "maxItems", "minItems", "properties", "required",
        "minProperties", "maxProperties", "minLength", "maxLength", "pattern", "example", "anyOf", "propertyOrdering",
        "default", "items", "minimum", "maximum",
    )

    fun sanitize(schema: JsonObject): JsonObject = JsonObject().apply {
        schema.entrySet().forEach { (key, value) ->
            when {
                key !in KEPT -> Unit
                key == "properties" && value.isJsonObject -> add(key, JsonObject().apply {
                    value.asJsonObject.entrySet().forEach { (name, property) ->
                        add(name, if (property.isJsonObject) sanitize(property.asJsonObject) else property.deepCopy())
                    }
                })
                key == "items" && value.isJsonObject -> add(key, sanitize(value.asJsonObject))
                key == "anyOf" && value.isJsonArray -> add(key, JsonArray().apply {
                    value.asJsonArray.forEach { add(if (it.isJsonObject) sanitize(it.asJsonObject) else it.deepCopy()) }
                })
                else -> add(key, value.deepCopy())
            }
        }
    }
}
