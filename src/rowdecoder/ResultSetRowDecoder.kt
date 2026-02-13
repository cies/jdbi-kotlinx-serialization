package dropnext.lib.jdbi.rowdecoder

import java.sql.ResultSet
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.time.Instant
import kotlin.time.toKotlinInstant
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual


// One instance that is shared.
private val unknownKeysIgnoringJson = Json { ignoreUnknownKeys = true }

/**
 * A decoder implementation for deserializing rows from a SQL `ResultSet` into Kotlin objects. This class leverages
 * the Kotlin serialization library to map SQL columns to object fields, handling primitive types, nullable fields,
 * complex/nested structures, and (`@SqlJsonValue`) annotated properties that deserialize from JSON received from the database.
 *
 * The default constructor parameters should be kept to their defaults: those are used when recursing over data structures.
 */
@OptIn(ExperimentalSerializationApi::class)
class ResultSetRowDecoder(
  private val rs: ResultSet,
  override val serializersModule: SerializersModule = resultSetSerializersModule,
  initialColumnIndex: Int = 1,
  private val json: Json = unknownKeysIgnoringJson,
  private val endCallback: (Int) -> Unit = {}
) : Decoder, CompositeDecoder {

  private var columnIndex = initialColumnIndex
  private var elementIndex = 0
  private val meta = rs.metaData

  // Check if a field has @SqlJsonValue annotation
  private fun isJsonFieldAnnotated(descriptor: SerialDescriptor, fieldIndex: Int): Boolean =
    descriptor.getElementAnnotations(fieldIndex).any { it is SqlJsonValue }

  // ---------------------------------------------------------
  // Structure
  // ---------------------------------------------------------
  override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder = this

  /** Update the columnIndex of the parent when the nested structure completes. */
  override fun endStructure(descriptor: SerialDescriptor) = endCallback(columnIndex)

  override fun decodeElementIndex(descriptor: SerialDescriptor): Int =
    if (elementIndex < descriptor.elementsCount) elementIndex++ else CompositeDecoder.DECODE_DONE


  // ---------------------------------------------------------
  // Null handling
  // ---------------------------------------------------------
  /**
   * Checks if the element is null. Used before calling an actual deserializer.
   * This cannot be used for nested classes. We need to check all upcoming rows to see if all of them are null:
   * only then is the parent element considered null.
   * So instead we just opt to return true and check for nullity in the parent call of [decodeNullableSerializableElement].
   */
  override fun decodeNotNullMark(): Boolean = rs.getObject(columnIndex) != null
  override fun decodeNull(): Nothing? {
    columnIndex++
    return null
  }

  /**
   * Centralized null handling with a consistent pattern inspired by terpal-sql's `validNullOrElse`.
   * If the value is null and the field is nullable, return null and advance the column index.
   * Otherwise, execute the provided decoder block.
   */
  private fun <T> handleNullableValue(descriptor: SerialDescriptor, index: Int, orElse: () -> T): T? {
    val columnName = columnNameFromMeta()
    if (rs.getObject(columnIndex) != null) return orElse()
    if (descriptor.isElementOptional(index) || descriptor.getElementDescriptor(index).isNullable) {
      columnIndex++
      return null
    }
    throw SerializationException(
      "NULL value encountered for non-nullable field '${descriptor.getElementName(index)}' " +
        "at column $columnIndex ($columnName) of type ${descriptor.getElementDescriptor(index).kind}"
    )
  }

  private fun columnNameFromMeta() = try {
    meta.getColumnLabel(columnIndex) ?: meta.getColumnName(columnIndex) ?: "column_$columnIndex"
  } catch (_: Exception) {
    "column_$columnIndex"
  }


  // ---------------------------------------------------------
  // Generic dispatch: pick decode method by SQL type
  // ---------------------------------------------------------
  private fun nextColumnType(): Int = meta.getColumnType(columnIndex)

  private fun <T> readColumn(getter: () -> T): T {
    val columnName = columnNameFromMeta()
    val columnType: String = runCatching { meta.getColumnTypeName(columnIndex) }.getOrElse { "unknown" }
    val value = getter()
    if (rs.wasNull()) {
      throw SerializationException(
        "NULL value encountered for non-nullable primitive at column $columnIndex ($columnName) of SQL type $columnType"
      )
    }
    return value
  }

  // ---------------------------------------------------------
  // Primitive decoders
  // ---------------------------------------------------------
  override fun decodeBoolean(): Boolean = readColumn { rs.getBoolean(columnIndex++) }
  override fun decodeByte(): Byte = readColumn { rs.getByte(columnIndex++) }
  override fun decodeShort(): Short = readColumn { rs.getShort(columnIndex++) }
  override fun decodeInt(): Int = readColumn { rs.getInt(columnIndex++) }
  override fun decodeLong(): Long = readColumn {
    when (val obj = rs.getObject(columnIndex++)) {
      is Number -> obj.toLong()
      is Timestamp -> obj.time
      else -> throw SerializationException("Cannot decode Long from $obj")
    }
  }

  override fun decodeFloat(): Float = readColumn { rs.getFloat(columnIndex++) }
  override fun decodeDouble(): Double = readColumn { rs.getDouble(columnIndex++) }
  override fun decodeChar(): Char = decodeString().singleOrNull()
    ?: throw SerializationException("Expected single character")

  override fun decodeString(): String = readColumn { rs.getString(columnIndex++) }
  override fun decodeInline(descriptor: SerialDescriptor): Decoder = this

  // ---------------------------------------------------------
  // Enum
  // ---------------------------------------------------------
  override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
    val value = decodeString()
    val idx = enumDescriptor.getElementIndex(value)
    if (idx == CompositeDecoder.UNKNOWN_NAME) throw SerializationException("Unknown enum value '$value'")
    return idx
  }

  // ---------------------------------------------------------
  // CompositeDecoder element dispatch
  // ---------------------------------------------------------
  override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int): Boolean = decodeBoolean()
  override fun decodeByteElement(descriptor: SerialDescriptor, index: Int): Byte = decodeByte()
  override fun decodeShortElement(descriptor: SerialDescriptor, index: Int): Short = decodeShort()
  override fun decodeIntElement(descriptor: SerialDescriptor, index: Int): Int = decodeInt()
  override fun decodeLongElement(descriptor: SerialDescriptor, index: Int): Long = decodeLong()
  override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int): Float = decodeFloat()
  override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int): Double = decodeDouble()
  override fun decodeCharElement(descriptor: SerialDescriptor, index: Int): Char = decodeChar()
  override fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String = decodeString()
  override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int): Decoder = this

  override fun <T> decodeSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    deserializer: DeserializationStrategy<T>,
    previousValue: T?
  ): T {
    val childDescriptor = descriptor.getElementDescriptor(index)

    // Handle @SqlJsonValue annotated fields
    if (isJsonFieldAnnotated(descriptor, index)) {
      val jsonString = rs.getString(columnIndex++)
        ?: throw SerializationException("JSON column cannot be null for non-nullable field at index $index")
      return json.decodeFromString(deserializer, jsonString)
    }

    // Handle SQL arrays (like text[] from Postgres)
    if (childDescriptor.kind == StructureKind.LIST) {
      val sqlArray = rs.getArray(columnIndex++)
      if (sqlArray == null || rs.wasNull()) {
        throw SerializationException("Array column cannot be null for non-nullable field at index $index")
      }
      try {
        @Suppress("UNCHECKED_CAST")
        val list = when (val arrayData = sqlArray.array) {
          is Array<*> -> arrayData.filterNotNull().map { it.toString() }
          else -> emptyList()
        }
        sqlArray.free()
        @Suppress("UNCHECKED_CAST")
        return list as T
      } catch (e: Exception) {
        throw SerializationException("Failed to deserialize array at column ${columnIndex - 1}: ${e.message}", e)
      }
    }

    // Handle kotlin.time.Instant specially to support PostgreSQL timestamp format
    if (childDescriptor.serialName == "kotlin.time.Instant") {
      return decodeToInstant(rs.getObject(columnIndex++))
    }

    // Handle nested data classes
    if (childDescriptor.kind == StructureKind.CLASS && !childDescriptor.isInline) {
      // Create a new decoder for the nested structure with a callback to update our column index
      val nestedDecoder = ResultSetRowDecoder(rs, serializersModule, columnIndex, json) { newIndex ->
        columnIndex = newIndex
      }
      return deserializer.deserialize(nestedDecoder)
    }

    // For non-nested types, use the original logic
    return deserializer.deserialize(this)
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T> decodeToInstant(value: Any?): T = when (value) {
    is Timestamp -> value.toInstant().toKotlinInstant() as T
    is OffsetDateTime -> value.toInstant().toKotlinInstant() as T
    is java.time.Instant -> value.toKotlinInstant() as T
    is String -> {
      // PostgreSQL returns timestamps with space separator: "2026-02-04 04:31:16.935337+01"
      // Kotlin expects ISO-8601 with 'T' separator: "2026-02-04T04:31:16.935337+01"
      val normalizedString = value.replaceFirst(' ', 'T')

      // If no timezone info present, assume UTC (PostgreSQL default for timestamptz)
      // Check if the string ends with timezone info (e.g., +01:00, -05:00, Z)
      val hasTimezone = normalizedString.endsWith('Z') || run {
        val maybeOffset = normalizedString.takeLast(8) // Not the '-' containing the date part.
        maybeOffset.contains('+') || maybeOffset.contains('-')
      }
      val isoString = if (hasTimezone) normalizedString else "${normalizedString}Z"
      Instant.parse(isoString) as T
    }

    else -> throw SerializationException("Cannot decode kotlin.time.Instant from $value (${value?.let { it::class} ?: "null"}) at column ${columnIndex - 1}")
  }

  override fun <T : Any> decodeNullableSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    deserializer: DeserializationStrategy<T?>,
    previousValue: T?
  ): T? {
    val childDesc = descriptor.getElementDescriptor(index)

    // Handle @SqlJsonValue annotated fields
    if (isJsonFieldAnnotated(descriptor, index)) {
      return handleNullableValue(descriptor, index) {
        rs.getString(columnIndex++)?.let { jsonString -> json.decodeFromString(deserializer, jsonString) }
      }
    }

    // Handle SQL arrays (like text[] from Postgres)
    if (childDesc.kind == StructureKind.LIST) {
      return handleNullableValue(descriptor, index) {
        val sqlArray = rs.getArray(columnIndex++)
        if (sqlArray == null || rs.wasNull()) null
        else {
          try {
            val arrayData = sqlArray.array

            @Suppress("UNCHECKED_CAST")
            val list = when (arrayData) {
              is Array<*> -> arrayData.filterNotNull().map { it.toString() }
              else -> emptyList()
            }
            sqlArray.free()
            @Suppress("UNCHECKED_CAST")
            list as T
          } catch (e: Exception) {
            throw SerializationException("Failed to deserialize array at column ${columnIndex - 1}: ${e.message}", e)
          }
        }
      }
    }

    // Handle nullable kotlin.time.Instant specially to support PostgreSQL timestamp format
    // Check both the child descriptor and the deserializer descriptor to handle nullable Instant fields
    if (childDesc.serialName == "kotlin.time.Instant" || deserializer.descriptor.serialName == "kotlin.time.Instant") {
      return handleNullableValue(descriptor, index) {
        decodeToInstant(rs.getObject(columnIndex++))
      }
    }

    // Handle nested data classes
    if (childDesc.kind == StructureKind.CLASS && !childDesc.isInline) {
      // Check if all columns for the nested structure are null
      val elementsCount = countElements(childDesc)
      val allNull = (columnIndex until (columnIndex + elementsCount)).all { colIdx ->
        // If we can't check the column (e.g., out of bounds), consider it null
        runCatching { rs.getObject(colIdx) == null }.getOrElse { true }
      }

      // If all are null and the field is nullable, return null and advance the column index
      if (allNull && (descriptor.isElementOptional(index) || childDesc.isNullable)) {
        columnIndex += elementsCount
        return null
      }

      // Otherwise, deserialize the nested structure with a callback to update our column index
      val nestedDecoder =
        ResultSetRowDecoder(rs, serializersModule, columnIndex, json) { newIndex -> columnIndex = newIndex }
      return deserializer.deserialize(nestedDecoder)
    }

    // For non-nested types, use centralized null handling
    return handleNullableValue(descriptor, index) {
      deserializer.deserialize(this)
    }
  }

  // Helper function to count total primitive elements in a structure
  private fun countElements(descriptor: SerialDescriptor): Int {
    return when (descriptor.kind) {
      StructureKind.CLASS -> {
        (0 until descriptor.elementsCount).sumOf { i ->
          val childDescriptor = descriptor.getElementDescriptor(i)
          when {
            // JSON fields count as a single column
            isJsonFieldAnnotated(descriptor, i) -> 1
            // Array fields count as a single column
            childDescriptor.kind == StructureKind.LIST -> 1
            // Nested classes are recursively counted
            childDescriptor.kind == StructureKind.CLASS && !childDescriptor.isInline -> countElements(childDescriptor)
            // Everything else is a single column
            else -> 1
          }
        }
      }

      else -> 1
    }
  }

  // User by a custom serializer
  fun decodeUUID(): UUID = readColumn {
    rs.getObject(columnIndex++)?.let { obj ->
      obj as? UUID ?: UUID.fromString(obj.toString())
    } ?: throw SerializationException("NULL for java.util.UUID at column ${columnIndex - 1}")
  }

}


// ---------------------------------------------------------
// Custom serializers used in [ResultSetRowDecoder]
// ---------------------------------------------------------

private object UUIDSerializer : KSerializer<UUID> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
  override fun deserialize(decoder: Decoder): UUID {
    return when (decoder) {
      is ResultSetRowDecoder -> decoder.decodeUUID()
      else -> UUID.fromString(decoder.decodeString())
    }
  }

  override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())
}

/** Default serializers module with Java time/UUID and Kotlin kotlinx.datetime/Uuid support. */
val resultSetSerializersModule = SerializersModule {
  contextual(UUIDSerializer) // Can be removed as we do not use it... Merely here to demonstrate this feature :)
}
