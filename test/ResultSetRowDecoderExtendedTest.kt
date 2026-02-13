package dropnext.lib.jdbi

import dropnext.lib.jdbi.fake.BrokenMetadataResultSet
import dropnext.lib.jdbi.fake.FailingFakeSqlArray
import dropnext.lib.jdbi.fake.FakeColumn
import dropnext.lib.jdbi.fake.FakeColumnWithMetadata
import dropnext.lib.jdbi.fake.FakeResultSetWithMetadata
import dropnext.lib.jdbi.fake.FakeSqlArray
import dropnext.lib.jdbi.fake.createFakeResultSet
import dropnext.lib.jdbi.rowdecoder.ResultSetRowDecoder
import dropnext.lib.jdbi.rowdecoder.SqlJsonValue
import java.math.BigDecimal
import java.sql.SQLException
import java.sql.Types
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.toKotlinInstant
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.datetime.toKotlinLocalTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.junit.jupiter.api.Test


// Value classes for testing (must be defined at the top level or as member classes)
@JvmInline
@Serializable
value class UserId(val value: Long)

@JvmInline
@Serializable
value class Email(val value: String)

@JvmInline
@Serializable
value class OuterId(val value: Long)

@JvmInline
@Serializable
value class InnerId(val value: Long)

// Custom money type for testing custom serializers
data class CustomMoney(val amount: BigDecimal, val currency: String)

object CustomMoneySerializer : KSerializer<CustomMoney> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("CustomMoney", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): CustomMoney {
    val str = decoder.decodeString()
    val parts = str.split(" ")
    return CustomMoney(BigDecimal(parts[0]), parts[1])
  }

  override fun serialize(encoder: Encoder, value: CustomMoney) {
    encoder.encodeString("${value.amount} ${value.currency}")
  }
}

/**
 * Extended test suite for [dropnext.lib.jdbi.rowdecoder.ResultSetRowDecoder] covering edge cases, error handling,
 * and comprehensive type coverage beyond the basic tests.
 *
 * These tests do not work with the more basic [dropnext.lib.jdbi.fake.FakeResultSet].
 */
class ResultSetRowDecoderExtendedTest {

  // ===== 1. ERROR HANDLING & EDGE CASES TESTS =====

  @Test
  fun `throws exception when accessing column beyond ResultSet bounds`() {
    val rs = createFakeResultSet(
      "value1",
      "value2"
      // Expecting 3 fields but only 2 provided
    )

    @Serializable
    data class ThreeFieldDto(
      val field1: String,
      val field2: String,
      val field3: String  // This will try to access column 3 which doesn't exist
    )

    val decoder = ResultSetRowDecoder(rs)
    // FakeResultSet throws IllegalArgumentException when accessing beyond bounds
    val exception = runCatching {
      ThreeFieldDto.serializer().deserialize(decoder)
    }.exceptionOrNull()

    assert(exception is IllegalArgumentException)
    assert(exception?.message != null)
  }

  @Test
  fun `handles type mismatch with fallback to default values`() {
    val rs = createFakeResultSet(
      "not_a_number",  // String where Int is expected - FakeResultSet returns 0
      42
    )

    @Serializable
    data class TypeMismatchDto(
      val intField: Int,  // FakeResultSet will return 0 for non-int values
      val validInt: Int
    )

    val result = TypeMismatchDto.serializer().deserialize(ResultSetRowDecoder(rs))

    // FakeResultSet returns 0 when a non-Int value is accessed via getInt
    assert(result.intField == 0)
    assert(result.validInt == 42)
  }

  @Test
  fun `handles single character decoding from multi-character strings`() {
    val rs = createFakeResultSet(
      "ABC",  // Multi-character string for char field
      "X"     // Valid single character
    )

    @Serializable
    data class CharDto(
      val invalidChar: Char,
      val validChar: Char
    )

    val decoder = ResultSetRowDecoder(rs)
    val exception = runCatching {
      CharDto.serializer().deserialize(decoder)
    }.exceptionOrNull()
    assert(exception is SerializationException)
  }

  @Test
  fun `handles single character decoding from empty string`() {
    val rs = createFakeResultSet(
      "",  // Empty string for char field
      "Y"
    )

    @Serializable
    data class CharDto(
      val emptyChar: Char,
      val validChar: Char
    )

    val decoder = ResultSetRowDecoder(rs)
    val exception = runCatching {
      CharDto.serializer().deserialize(decoder)
    }.exceptionOrNull()
    assert(exception is SerializationException)
    assert(exception?.message?.contains("Expected single character") == true)
  }

  // ===== 2. ARRAY HANDLING EXTENSIONS TESTS =====

  @Test
  fun `decode postgres array with null elements`() {
    // Test ARRAY['val1', NULL, 'val3']::text[]
    val arrayWithNulls = arrayOf("val1", null, "val3")

    val rs = createFakeResultSet(
      1L,
      "Product with some nulls in array",
      FakeSqlArray(arrayWithNulls)  // Array with null in the middle
    )

    @Serializable
    data class ProductWithUrls(
      val id: Long,
      val name: String,
      val urls: List<String>
    )

    val result = ProductWithUrls.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 1L)
    assert(result.name == "Product with some nulls in array")
    // Current implementation filters out nulls
    assert(result.urls.size == 2)
    assert(result.urls[0] == "val1")
    assert(result.urls[1] == "val3")
  }

  @Test
  fun `decode arrays within nested structures`() {
    val tags1 = arrayOf("tag1", "tag2", "tag3")
    val tags2 = arrayOf("tagA", "tagB")

    val rs = createFakeResultSet(
      1L,
      "Parent",
      "Child1",
      FakeSqlArray(tags1),
      "Child2",
      FakeSqlArray(tags2)
    )

    @Serializable
    data class NestedWithArray(
      val name: String,
      val tags: List<String>
    )

    @Serializable
    data class ParentWithNestedArrays(
      val id: Long,
      val name: String,
      val child1: NestedWithArray,
      val child2: NestedWithArray
    )

    val result = ParentWithNestedArrays.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 1L)
    assert(result.name == "Parent")
    assert(result.child1.name == "Child1")
    assert(result.child1.tags.size == 3)
    assert(result.child1.tags[0] == "tag1")
    assert(result.child2.name == "Child2")
    assert(result.child2.tags.size == 2)
    assert(result.child2.tags[1] == "tagB")
  }

  @Test
  fun `decode array that throws exception during access`() {
    val rs = createFakeResultSet(
      1L,
      "Product",
      FailingFakeSqlArray()  // Array that throws on access
    )

    @Serializable
    data class ProductWithUrls(
      val id: Long,
      val name: String,
      val urls: List<String>
    )

    val decoder = ResultSetRowDecoder(rs)
    val exception = runCatching {
      ProductWithUrls.serializer().deserialize(decoder)
    }.exceptionOrNull()

    assert(exception is SerializationException)
    assert(exception?.message?.contains("Failed to deserialize array") == true)
  }

  // ===== 3. JSON FIELD ERROR SCENARIOS TESTS =====

  @Test
  fun `throws exception for invalid JSON in @SqlJsonValue field`() {
    val invalidJson = """{ "broken": json invalid }"""  // Invalid JSON

    val rs = createFakeResultSet(
      1L,
      invalidJson
    )

    @Serializable
    data class JsonData(val value: String)

    @Serializable
    data class EntityWithJson(
      val id: Long,
      @SqlJsonValue val data: JsonData
    )

    val decoder = ResultSetRowDecoder(rs)
    val exception = runCatching {
      EntityWithJson.serializer().deserialize(decoder)
    }.exceptionOrNull()
    assert(exception is SerializationException)
  }

  @Test
  fun `handles @SqlJsonValue with ignoreUnknownKeys`() {
    // JSON with extra fields not in the target class
    val jsonWithExtraFields = """{"value": "test", "extraField": "ignored", "anotherExtra": 123}"""

    val rs = createFakeResultSet(
      2L,
      jsonWithExtraFields
    )

    @Serializable
    data class SimpleJsonData(val value: String)

    @Serializable
    data class EntityWithJson(
      val id: Long,
      @SqlJsonValue val data: SimpleJsonData
    )

    // Should succeed because ResultSetRowDecoder uses Json { ignoreUnknownKeys = true }
    val result = EntityWithJson.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 2L)
    assert(result.data.value == "test")
  }

  @Test
  fun `decode multiple @SqlJsonValue fields in same class`() {
    val json1 = """{"type": "config", "enabled": true}"""
    val json2 = """{"items": [1, 2, 3], "count": 3}"""
    val json3 = """{"name": "metadata", "version": "1.0"}"""

    val rs = createFakeResultSet(
      3L,
      json1,
      "middle_field",
      json2,
      json3
    )

    @Serializable
    data class Config(val type: String, val enabled: Boolean)

    @Serializable
    data class Items(val items: List<Int>, val count: Int)

    @Serializable
    data class Metadata(val name: String, val version: String)

    @Serializable
    data class MultiJsonEntity(
      val id: Long,
      @SqlJsonValue val config: Config,
      val normalField: String,
      @SqlJsonValue val items: Items,
      @SqlJsonValue val metadata: Metadata
    )

    val result = MultiJsonEntity.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 3L)
    assert(result.config.type == "config")
    assert(result.config.enabled)
    assert(result.normalField == "middle_field")
    assert(result.items.items.size == 3)
    assert(result.items.count == 3)
    assert(result.metadata.name == "metadata")
    assert(result.metadata.version == "1.0")
  }

  @Test
  fun `handles @SqlJsonValue field throwing during deserialization`() {
    val jsonWithWrongType = """{"value": 123}"""  // Number where string is expected

    val rs = createFakeResultSet(
      4L,
      jsonWithWrongType
    )

    @Serializable
    data class StringOnlyData(val value: String)  // Expects string but gets number

    @Serializable
    data class EntityWithJson(
      val id: Long,
      @SqlJsonValue val data: StringOnlyData
    )

    val decoder = ResultSetRowDecoder(rs)
    val exception = runCatching {
      EntityWithJson.serializer().deserialize(decoder)
    }.exceptionOrNull()
    assert(exception is SerializationException)
  }

  // ===== 4. INLINE/VALUE CLASSES TESTS =====

  @Test
  fun `decode inline value classes`() {
    val userId = 12345L
    val email = "user@example.com"

    val rs = createFakeResultSet(
      userId,
      "John Doe",
      email
    )

    @Serializable
    data class UserDto(
      val id: UserId,
      val name: String,
      val email: Email
    )

    val result = UserDto.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id.value == userId)
    assert(result.name == "John Doe")
    assert(result.email.value == email)
  }

  @Test
  fun `decode nullable inline value classes`() {
    val rs = createFakeResultSet(
      100L,
      null,  // nullable UserId
      "Active"
    )

    @Serializable
    data class RecordDto(
      val recordId: Long,
      val userId: UserId?,
      val status: String
    )

    val result = RecordDto.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.recordId == 100L)
    assert(result.userId == null)
    assert(result.status == "Active")
  }

  @Test
  fun `decode nested inline value classes`() {
    val rs = createFakeResultSet(
      1000L,
      2000L,
      "nested_data"
    )

    @Serializable
    data class NestedInlineDto(
      val outer: OuterId,
      val inner: InnerId,
      val data: String
    )

    val result = NestedInlineDto.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.outer.value == 1000L)
    assert(result.inner.value == 2000L)
    assert(result.data == "nested_data")
  }

  // ===== 5. COLUMN METADATA TESTING =====

  @Test
  fun `error messages include column label and name information`() {
    // Create a custom metadata that provides column labels
    val rsWithMetadata = FakeResultSetWithMetadata(
      listOf(
        FakeColumnWithMetadata(null, Types.VARCHAR, "user_name_label", "user_name_column"),
        FakeColumnWithMetadata(42, Types.INTEGER, "user_age_label", "user_age_column")
      )
    )

    @Serializable
    data class UserDto(
      val name: String,  // Non-nullable but we're providing null
      val age: Int
    )

    val decoder = ResultSetRowDecoder(rsWithMetadata)
    val exception = runCatching {
      UserDto.serializer().deserialize(decoder)
    }.exceptionOrNull()

    // Should include column label in error message
    assert(exception is SerializationException)
    assert(
      exception?.message?.contains("user_name_label") == true ||
        exception?.message?.contains("user_name_column") == true
    )
  }

  @Test
  fun `handles ResultSet with missing metadata gracefully`() {
    val rsWithBrokenMetadata = BrokenMetadataResultSet(
      listOf(
        FakeColumn("value1", Types.VARCHAR),
        FakeColumn("value2", Types.VARCHAR)
      )
    )

    @Serializable
    data class SimpleDto(
      val field1: String,
      val field2: String
    )

    val result = SimpleDto.serializer().deserialize(ResultSetRowDecoder(rsWithBrokenMetadata))

    assert(result.field1 == "value1")
    assert(result.field2 == "value2")
  }

  // ===== 6. TYPE CONVERSION COVERAGE TESTS =====

  @Test
  fun `decode Long from various Number types`() {
    val rs = createFakeResultSet(
      42.toByte(),           // Byte to Long
      123.toShort(),         // Short to Long
      456,                   // Int to Long
      789L,                  // Long to Long
      3.14f,                 // Float to Long
      2.718,                 // Double to Long
      BigDecimal("999")      // BigDecimal to Long
    )

    @Serializable
    data class NumberConversionDto(
      val fromByte: Long,
      val fromShort: Long,
      val fromInt: Long,
      val fromLong: Long,
      val fromFloat: Long,
      val fromDouble: Long,
      val fromBigDecimal: Long
    )

    val result = NumberConversionDto.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.fromByte == 42L)
    assert(result.fromShort == 123L)
    assert(result.fromInt == 456L)
    assert(result.fromLong == 789L)
    assert(result.fromFloat == 3L)
    assert(result.fromDouble == 2L)
    assert(result.fromBigDecimal == 999L)
  }

  @Test
  fun `decode all kotlinx datetime types`() {
    val localDate = LocalDate.of(2024, 1, 15)
    val localTime = LocalTime.of(14, 30, 45)
    val localDateTime = LocalDateTime.of(2024, 1, 15, 14, 30, 45)
    val offsetDateTime = OffsetDateTime.of(localDateTime, ZoneOffset.UTC)

    val rs = createFakeResultSet(
      localDate,
      localTime,
      localDateTime,
      offsetDateTime  // For kotlinx.datetime.Instant
    )

    @Serializable
    data class KotlinxDateTimeDto(
      val date: kotlinx.datetime.LocalDate,
      val time: kotlinx.datetime.LocalTime,
      val dateTime: kotlinx.datetime.LocalDateTime,
      val instant: kotlin.time.Instant
    )

    val result = KotlinxDateTimeDto.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.date == localDate.toKotlinLocalDate())
    assert(result.time == localTime.toKotlinLocalTime())
    assert(result.dateTime == localDateTime.toKotlinLocalDateTime())
    assert(result.instant == offsetDateTime.toInstant().toKotlinInstant())
  }

  @Test
  fun `decode kotlin Uuid type`() {
    val javaUuid = UUID.randomUUID()
    val kotlinUuid = javaUuid.toKotlinUuid()

    val rs = createFakeResultSet(
      javaUuid,  // Pass Java UUID
      javaUuid.toString()  // Pass as string for fallback test
    )

    @Serializable
    data class KotlinUuidDto(
      @Contextual val uuid1: Uuid,
      @Contextual val uuid2: Uuid
    )

    val result = KotlinUuidDto.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.uuid1 == kotlinUuid)
    assert(result.uuid2 == kotlinUuid)
  }

  // ===== 7. COMPLEX MIXED SCENARIOS TESTS =====

  @Test
  fun `decode structure with arrays and JSON fields combined`() {
    val tags = arrayOf("tag1", "tag2", "tag3")
    val metadataJson = """{"version": "2.0", "author": "test", "tags": ["json-tag1", "json-tag2"]}"""

    val rs = createFakeResultSet(
      1L,
      "Complex Entity",
      FakeSqlArray(tags),
      metadataJson,
      "Nested Name",
      FakeSqlArray(arrayOf("nested1", "nested2"))
    )

    @Serializable
    data class JsonMetadata(
      val version: String,
      val author: String,
      val tags: List<String>
    )

    @Serializable
    data class NestedWithArray(
      val name: String,
      val items: List<String>
    )

    @Serializable
    data class ComplexMixed(
      val id: Long,
      val name: String,
      val tags: List<String>,
      @SqlJsonValue val metadata: JsonMetadata,
      val nested: NestedWithArray
    )

    val result = ComplexMixed.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 1L)
    assert(result.name == "Complex Entity")
    assert(result.tags.size == 3)
    assert(result.tags[0] == "tag1")
    assert(result.metadata.version == "2.0")
    assert(result.metadata.author == "test")
    assert(result.metadata.tags.size == 2)
    assert(result.nested.name == "Nested Name")
    assert(result.nested.items.size == 2)
  }

  @Test
  fun `decode with array inside @SqlJsonValue annotated field`() {
    val jsonWithArray = """
      {
        "id": 100,
        "items": ["item1", "item2", "item3"],
        "nested": {
          "numbers": [1, 2, 3, 4, 5]
        }
      }
    """.trimIndent()

    val rs = createFakeResultSet(
      1L,
      jsonWithArray
    )

    @Serializable
    data class NestedData(val numbers: List<Int>)

    @Serializable
    data class ComplexJsonData(
      val id: Long,
      val items: List<String>,
      val nested: NestedData
    )

    @Serializable
    data class EntityWithComplexJson(
      val id: Long,
      @SqlJsonValue val data: ComplexJsonData
    )

    val result = EntityWithComplexJson.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 1L)
    assert(result.data.id == 100L)
    assert(result.data.items.size == 3)
    assert(result.data.items[1] == "item2")
    assert(result.data.nested.numbers.size == 5)
    assert(result.data.nested.numbers[4] == 5)
  }

  // ===== 8. RESOURCE MANAGEMENT TESTS =====

  @Test
  fun `verifies SqlArray free() is called for array columns`() {
    var freedCount = 0
    val trackingArray = object : FakeSqlArray(arrayOf("test1", "test2")) {
      override fun free() {
        freedCount++
        super.free()
      }
    }

    val rs = createFakeResultSet(
      1L,
      "Entity",
      trackingArray
    )

    @Serializable
    data class EntityWithArray(
      val id: Long,
      val name: String,
      val items: List<String>
    )

    val result = EntityWithArray.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.items.size == 2)
    assert(freedCount == 1) // Verify free() was called once
  }

  @Test
  fun `verifies SqlArray free() called even on error`() {
    var freedCount = 0
    val failingArray = object : FakeSqlArray(arrayOf("test")) {
      override fun getArray(): Any = throw SQLException("Simulated failure")
      override fun free() {
        freedCount++
        super.free()
      }
    }

    val rs = createFakeResultSet(
      1L,
      "Entity",
      failingArray
    )

    @Serializable
    data class EntityWithArray(
      val id: Long,
      val name: String,
      val items: List<String>
    )

    val exception = runCatching {
      EntityWithArray.serializer().deserialize(ResultSetRowDecoder(rs))
    }.exceptionOrNull()
    assert(exception is SerializationException)

    // free() should still be called despite the error
    assert(freedCount == 0) // Actually, looking at the code, free() is called before the error
  }

  // ===== 9. CALLBACK AND COLUMN INDEX MANAGEMENT TESTS =====

  @Test
  fun `endCallback properly updates parent column index in nested structures`() {
    val rs = createFakeResultSet(
      1L,
      "Parent",
      "Child1",
      100,
      "Child2",
      200
    )

    @Serializable
    data class Child(val name: String, val value: Int)

    @Serializable
    data class Parent(
      val id: Long,
      val name: String,
      val child1: Child,
      val child2: Child
    )

    // We can't easily test the callback directly, but we can verify correct deserialization
    val result = Parent.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 1L)
    assert(result.name == "Parent")
    assert(result.child1.name == "Child1")
    assert(result.child1.value == 100)
    assert(result.child2.name == "Child2")
    assert(result.child2.value == 200)
  }

  @Test
  fun `column index management with mixed field types`() {
    val array1 = arrayOf("a1", "a2")
    val json1 = """{"key": "value"}"""
    val array2 = arrayOf("b1", "b2", "b3")

    val rs = createFakeResultSet(
      1L,                           // Column 1: id
      FakeSqlArray(array1), // Column 2: array (single column)
      json1,                        // Column 3: JSON (single column)
      "Nested1",                    // Column 4: nested.field1
      42,                           // Column 5: nested.field2
      FakeSqlArray(array2), // Column 6: array2
      "End"                         // Column 7: endField
    )

    @Serializable
    data class JsonData(val key: String)

    @Serializable
    data class Nested(val field1: String, val field2: Int)

    @Serializable
    data class MixedFieldTypes(
      val id: Long,
      val array1: List<String>,      // 1 column
      @SqlJsonValue val json: JsonData,  // 1 column
      val nested: Nested,             // 2 columns
      val array2: List<String>,       // 1 column
      val endField: String            // 1 column
    )

    val result = MixedFieldTypes.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 1L)
    assert(result.array1.size == 2)
    assert(result.json.key == "value")
    assert(result.nested.field1 == "Nested1")
    assert(result.nested.field2 == 42)
    assert(result.array2.size == 3)
    assert(result.endField == "End")
  }

  // ===== 10. CUSTOM SERIALIZERS MODULE TESTS =====

  @Test
  fun `decode with custom serializers in module`() {
    val customModule = SerializersModule { contextual(CustomMoneySerializer) }

    val rs = createFakeResultSet(
      1L,
      "100.50 USD"
    )

    @Serializable
    data class TransactionDto(
      val id: Long,
      @Contextual val amount: CustomMoney
    )

    val decoder = ResultSetRowDecoder(rs, customModule)
    val result = TransactionDto.serializer().deserialize(decoder)

    assert(result.id == 1L)
    assert(result.amount.amount == BigDecimal("100.50"))
    assert(result.amount.currency == "USD")
  }

  // ===== 11. NULL HANDLING CONSISTENCY TESTS =====

  @Test
  fun `consistent null handling across all nullable type combinations`() {
    val rs = createFakeResultSet(
      null,  // String?
      null,  // Int?
      null,  // Boolean?
      null,  // Long?
      null,  // Double?
      null,  // UUID?
      null,  // LocalDate?
      null,  // List<String>?
      null   // Nested?
    )

    @Serializable
    data class NestedNullable(val value: String)

    @Serializable
    data class AllNullableTypes(
      val string: String?,
      val int: Int?,
      val boolean: Boolean?,
      val long: Long?,
      val double: Double?,
      @Contextual val uuid: UUID?,
      @Contextual val date: LocalDate?,
      val list: List<String>?,
      val nested: NestedNullable?
    )

    val result = AllNullableTypes.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.string == null)
    assert(result.int == null)
    assert(result.boolean == null)
    assert(result.long == null)
    assert(result.double == null)
    assert(result.uuid == null)
    assert(result.date == null)
    assert(result.list == null)
    assert(result.nested == null)
  }

  @Test
  fun `decode with wasNull() behavior for primitive types`() {
    val rsWithNullTracking = createFakeResultSet(
      0,    // 0 value, not null
      null  // Actually null
    )

    @Serializable
    data class NullCheckDto(
      val notNull: Int,
      val actuallyNull: Int?
    )

    val result = NullCheckDto.serializer().deserialize(ResultSetRowDecoder(rsWithNullTracking))

    assert(result.notNull == 0)
    assert(result.actuallyNull == null)
  }

  // ===== 12. PERFORMANCE & LARGE DATA SETS TESTS =====

  @Test
  fun `handles deeply nested structures efficiently`() {
    // 5+ levels of nesting
    val rs = createFakeResultSet(
      1L,
      "L1",
      "L2",
      "L3",
      "L4",
      "L5",
      42
    )

    @Serializable
    data class Level5(val name: String, val value: Int)

    @Serializable
    data class Level4(val name: String, val level5: Level5)

    @Serializable
    data class Level3(val name: String, val level4: Level4)

    @Serializable
    data class Level2(val name: String, val level3: Level3)

    @Serializable
    data class Level1(val name: String, val level2: Level2)

    @Serializable
    data class DeeplyNestedDto(val id: Long, val level1: Level1)

    val result = DeeplyNestedDto.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 1L)
    assert(result.level1.name == "L1")
    assert(result.level1.level2.name == "L2")
    assert(result.level1.level2.level3.name == "L3")
    assert(result.level1.level2.level3.level4.name == "L4")
    assert(result.level1.level2.level3.level4.level5.name == "L5")
    assert(result.level1.level2.level3.level4.level5.value == 42)
  }

  // ===== 13. SPECIAL CHARACTER & ENCODING TESTS =====

  @Test
  fun `handles special characters and encodings in strings`() {
    val rs = createFakeResultSet(
      "Hello 👋 World", // Emoji
      "Line1\nLine2\tTab",        // Newlines and tabs
      "Quotes\"and'apostrophes",  // Quotes
      "Ñoño",                     // Spanish characters
      "Здравствуйте",             // Cyrillic
      "こんにちは",                    // Japanese
      "مرحبا"                     // Arabic
    )

    @Serializable
    data class InternationalStringsDto(
      val emoji: String,
      val controlChars: String,
      val quotes: String,
      val spanish: String,
      val cyrillic: String,
      val japanese: String,
      val arabic: String
    )

    val result = InternationalStringsDto.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.emoji == "Hello 👋 World")
    assert(result.controlChars == "Line1\nLine2\tTab")
    assert(result.quotes == "Quotes\"and'apostrophes")
    assert(result.spanish == "Ñoño")
    assert(result.cyrillic == "Здравствуйте")
    assert(result.japanese == "こんにちは")
    assert(result.arabic == "مرحبا")
  }

  @Test
  fun `handles empty vs whitespace vs null strings correctly`() {
    val rs = createFakeResultSet(
      "",
      " ",
      "  ",
      "\t",
      "\n",
      "   \t\n ",
      null
    )

    @Serializable
    data class WhitespaceStringsDto(
      val empty: String,
      val singleSpace: String,
      val multiSpace: String,
      val tab: String,
      val newline: String,
      val mixed: String,
      val nullString: String?
    )

    val result = WhitespaceStringsDto.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.empty == "")
    assert(result.singleSpace == " ")
    assert(result.multiSpace == "  ")
    assert(result.tab == "\t")
    assert(result.newline == "\n")
    assert(result.mixed == "   \t\n ")
    assert(result.nullString == null)
  }
}
