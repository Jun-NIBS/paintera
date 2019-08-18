package org.janelia.saalfeldlab.paintera.serialization

import com.google.gson.JsonElement

class GsonExtensions {

	companion object {

		fun JsonElement?.getProperty(key: String) = this
				?.takeIf { it.isJsonObject }
				?.asJsonObject
				?.takeIf { it.has(key) }
				?.get(key)

		fun JsonElement?.getJsonPrimitiveProperty(key: String) = this
				?.getProperty(key)
				?.takeIf { it.isJsonPrimitive }
				?.asJsonPrimitive

		fun JsonElement?.getBooleanProperty(key: String) = this
				?.getJsonPrimitiveProperty(key)
				?.takeIf { it.isBoolean }
				?.asBoolean

		fun JsonElement?.getStringProperty(key: String) = this
				?.getJsonPrimitiveProperty(key)
				?.takeIf { it.isString }
				?.asString


		fun JsonElement?.getNumberProperty(key: String) = this
				?.getJsonPrimitiveProperty(key)
				?.takeIf { it.isNumber }
				?.asNumber

		fun JsonElement?.getDoubleProperty(key: String) = this
				?.getJsonPrimitiveProperty(key)
				?.takeIf { it.isNumber }
				?.asDouble

		fun JsonElement?.getIntProperty(key: String) = this
				?.getJsonPrimitiveProperty(key)
				?.takeIf { it.isNumber }
				?.asInt

	}
}
