package dropnext.lib.jdbi

import dropnext.lib.jdbi.fake.FakeSqlArray
import dropnext.lib.jdbi.fake.createFakeResultSet
import dropnext.lib.jdbi.rowdecoder.ResultSetRowDecoder
import dropnext.lib.jdbi.rowdecoder.SqlJsonValue
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.junit.jupiter.api.Test


typealias CustomInstantForTests = @Serializable(with = InstantToPgTimestamptzSerializer::class) Instant
object InstantToPgTimestamptzSerializer : KSerializer<Instant> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("import java.time.Instant", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): Instant =
    OffsetDateTime.parse(decoder.decodeString().replace(' ', 'T')).toInstant()

  override fun serialize(encoder: Encoder, value: Instant) {
    encoder.encodeString(value.toString().replace(' ', 'T'))
  }
}

typealias CustomTimezoneForTests = @Serializable(with = ZoneIdToStringSerializer::class) ZoneId
object ZoneIdToStringSerializer : KSerializer<ZoneId> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("import java.time.ZoneId", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): ZoneId =
    ZoneId.of(decoder.decodeString())

  override fun serialize(encoder: Encoder, value: ZoneId) {
    encoder.encodeString(value.id)
  }
}

/**
 * Unit tests for verifying the functionality of the [dropnext.lib.jdbi.rowdecoder.ResultSetRowDecoder].
 * This test class ensures that ResultSet rows are correctly decoded into data classes or other
 * target representations using various scenarios.
 *
 * These tests only with [dropnext.lib.jdbi.fake.FakeResultSet] (test that need more than this fake can be found in
 * the [ResultSetRowDecoderExtendedTest] class.
 */
class ResultSetRowDecoderTest {

  // Test-specific data classes
  @Serializable
  data class SimplePrimitivesDto(
    val booleanField: Boolean,
    val byteField: Byte,
    val shortField: Short,
    val intField: Int,
    val longField: Long,
    val floatField: Float,
    val doubleField: Double,
    val charField: Char,
    val stringField: String
  )

  @Serializable
  data class NullableFieldsDto(
    val requiredString: String,
    val optionalString: String?,
    val requiredInt: Int,
    val optionalInt: Int?,
    val requiredBoolean: Boolean,
    val optionalBoolean: Boolean?
  )

  @Serializable
  data class CustomTypesDto(
    @Contextual val uuid: UUID,
    val instant: CustomInstantForTests
  )

  @Serializable
  data class JavaTimeTypesDto(
    @Contextual val localDate: LocalDate,
    @Contextual val localDateTime: LocalDateTime,
    @Contextual val instant: Instant
  )

  @Serializable
  enum class TestEnum {
    FIRST_VALUE,
    SECOND_VALUE,
    THIRD_VALUE
  }

  @Serializable
  data class EnumDto(
    val enumField: TestEnum,
    val optionalEnumField: TestEnum?
  )

  @Serializable
  data class SnakeCaseDto(
    @SerialName("user_id")
    val userId: Long,
    @SerialName("user_name")
    val userName: String,
    @SerialName("is_active")
    val isActive: Boolean,
    @SerialName("created_at")
    val createdAt: CustomInstantForTests
  )

  @Serializable
  data class ComplexDto(
    val id: Long,
    val name: String,
    val description: String?,
    val isActive: Boolean,
    val rating: Double,
    @Contextual val uuid: UUID,
    val createdAt: CustomInstantForTests,
    val status: TestEnum,
    val optionalStatus: TestEnum?,
    val tags: List<String>? = null  // Not tested as it requires array decoder
  )

  @Serializable
  data class TestRetailer(
    val id: Long,
    val createdAt: CustomInstantForTests,
    val updatedAt: CustomInstantForTests,
    val name: String,
    val accountHolderUserId: Uuid,
    val accountManagerUserId: Uuid? = null,
    val currency: String,
    val regionCode: String,
    val defaultTimezone: CustomTimezoneForTests,
    val defaultLocale: String
  )

  @Serializable
  data class TestUpload(
    val uploadId: Uuid,
    val createdAt: CustomInstantForTests,
    val fileName: String,
    val filePath: String,
    val fileType: String?,
    val thumbnailFilePath: String?,
    val thumbnailState: String?
  )

  @Test
  fun `decode simple primitive types`() {
    val rs = createFakeResultSet(
      true, // boolean
      42.toByte(),    // byte
      123.toShort(),  // short
      456,            // int
      789L,           // long
      3.14f,          // float
      2.718,          // double
      "X",            // char (as string)
      "test string"   // string
    )
    val result = SimplePrimitivesDto.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.booleanField)
    assert(result.byteField == 42.toByte())
    assert(result.shortField == 123.toShort())
    assert(result.intField == 456)
    assert(result.longField == 789L)
    assert(result.floatField == 3.14f)
    assert(result.doubleField == 2.718)
    assert(result.charField == 'X')
    assert(result.stringField == "test string")
  }

  @Test
  fun `decode nullable fields with non-null values`() {
    val rs = createFakeResultSet(
      "required",     // requiredString
      "optional",     // optionalString
      42,             // requiredInt
      84,             // optionalInt
      true,           // requiredBoolean
      false           // optionalBoolean
    )
    val result = NullableFieldsDto.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.requiredString == "required")
    assert(result.optionalString == "optional")
    assert(result.requiredInt == 42)
    assert(result.optionalInt == 84)
    assert(result.requiredBoolean)
    assert(result.optionalBoolean == false)
  }

  @Test
  fun `decode nullable fields with null values`() {
    val rs = createFakeResultSet(
      "required",     // requiredString
      null,           // optionalString (null)
      42,             // requiredInt
      null,           // optionalInt (null)
      true,           // requiredBoolean
      null            // optionalBoolean (null)
    )
    val result = NullableFieldsDto.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.requiredString == "required")
    assert(result.optionalString == null)
    assert(result.requiredInt == 42)
    assert(result.optionalInt == null)
    assert(result.requiredBoolean)
    assert(result.optionalBoolean == null)
  }

  @Test
  fun `throws exception when null value for non-nullable field`() {
    val rs = createFakeResultSet(
      null, // requiredString (should throw)
      "optional",     // optionalString
      42,             // requiredInt
      84,             // optionalInt
      true,           // requiredBoolean
      false           // optionalBoolean
    )
    val decoder = ResultSetRowDecoder(rs)

    val exception = runCatching { NullableFieldsDto.serializer().deserialize(decoder) }.exceptionOrNull()
    assert(exception is SerializationException)
    assert(exception?.message?.contains("NULL value encountered for non-nullable") == true)
  }

  @Test
  fun `decode custom types`() {
    val uuid = UUID.randomUUID()

    val rs = createFakeResultSet(
      uuid,
      "2024-01-15 10:30:00+00",  // CustomInstant expects a timezone-aware format
    )
    val result = CustomTypesDto.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.uuid == uuid)
    assert(result.instant.toString().contains("2024-01-15")) // CustomInstant would parse the string
  }

  @Test
  fun `decode enum values`() {
    val rs = createFakeResultSet(
      "SECOND_VALUE",
      "THIRD_VALUE"
    )
    val result = EnumDto.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.enumField == TestEnum.SECOND_VALUE)
    assert(result.optionalEnumField == TestEnum.THIRD_VALUE)
  }

  @Test
  fun `decode enum with null optional value`() {
    val rs = createFakeResultSet(
      "FIRST_VALUE",
      null
    )
    val result = EnumDto.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.enumField == TestEnum.FIRST_VALUE)
    assert(result.optionalEnumField == null)
  }

  @Test
  fun `throws exception for unknown enum value`() {
    val rs = createFakeResultSet(
      "UNKNOWN_VALUE",  // Invalid enum value
      null
    )
    val decoder = ResultSetRowDecoder(rs)

    val exception = runCatching { EnumDto.serializer().deserialize(decoder) }.exceptionOrNull()
    assert(exception is IllegalArgumentException)
  }

  @Test
  fun `decode with SerialName annotations for snake_case columns`() {
    val rs = createFakeResultSet(
      123L,            // user_id
      "john_doe",                // user_name
      true,                      // is_active
      "2024-01-15 10:30:00+00"   // created_at
    )
    val result = SnakeCaseDto.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.userId == 123L)
    assert(result.userName == "john_doe")
    assert(result.isActive)
    assert(result.createdAt.toString().contains("2024-01-15"))
  }

  @Test
  fun `decode complex DTO with mixed types`() {
    val uuid = UUID.randomUUID()
    val rs = createFakeResultSet(
      42L,             // id
      "Test Entity",             // name
      "A description",           // description
      true,                      // isActive
      4.5,                       // rating
      uuid,                      // uuid
      "2024-01-15 10:30:00+00",  // createdAt
      "FIRST_VALUE",             // status
      null,                      // optionalStatus (null)
      null                       // tags (not tested, would need array decoder)
    )
    val result = ComplexDto.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 42L)
    assert(result.name == "Test Entity")
    assert(result.description == "A description")
    assert(result.isActive)
    assert(result.rating == 4.5)
    assert(result.uuid == uuid)
    assert(result.createdAt.toString().contains("2024-01-15"))
    assert(result.status == TestEnum.FIRST_VALUE)
    assert(result.optionalStatus == null)
    assert(result.tags == null)
  }

  @Test
  fun `decode real Retailer DTO`() {
    val accountHolderJavaUuid = UUID.randomUUID()
    val accountManagerJavaUuid = UUID.randomUUID()
    val accountHolderUuid = accountHolderJavaUuid.toKotlinUuid()
    val accountManagerUuid = accountManagerJavaUuid.toKotlinUuid()

    val rs = createFakeResultSet(
      123L,            // retailer_id
      "2024-01-01 09:00:00+00",  // created_at
      "2024-01-15 10:30:00+00",  // updated_at
      "Test Retailer",           // name
      accountHolderJavaUuid,     // account_holder_user_id
      accountManagerJavaUuid,    // account_manager_user_id
      "USD",                     // currency
      "US",                      // region_code
      "America/New_York",        // default_timezone
      "en-US"                    // default_locale
    )
    val result = TestRetailer.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 123L)
    assert(result.name == "Test Retailer")
    assert(result.accountHolderUserId == accountHolderUuid)
    assert(result.accountManagerUserId == accountManagerUuid)
    assert(result.currency == "USD")
    assert(result.regionCode == "US")
    assert(result.defaultTimezone.id == "America/New_York")
    assert(result.defaultLocale == "en-US")
  }

  @Test
  fun `decode real Upload DTO with nullable fields`() {
    val uploadJavaId = UUID.randomUUID()
    val uploadId = uploadJavaId.toKotlinUuid()
    val rs = createFakeResultSet(
      uploadJavaId,
      "2024-01-15 10:30:00+00",
      "document.png",
      "organizations/1002/1c1194bb-fb81-4bf6-b1c1-c5b6f589648f-document.png",
      "image/png",
      "organizations/1002/1c1194bb-fb81-4bf6-b1c1-c5b6f589648f_thumbnail.jpeg",
      "Success"
    )
    val result = TestUpload.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.uploadId == uploadId)
    assert(result.fileName == "document.png")
    assert(result.filePath == "organizations/1002/1c1194bb-fb81-4bf6-b1c1-c5b6f589648f-document.png")
    assert(result.fileType == "image/png")
    assert(result.thumbnailFilePath == "organizations/1002/1c1194bb-fb81-4bf6-b1c1-c5b6f589648f_thumbnail.jpeg")
    assert(result.thumbnailState == "Success")
  }

  @Test
  fun `decode Upload DTO with null optional fields`() {
    val uploadJavaId = UUID.randomUUID()
    val uploadId = uploadJavaId.toKotlinUuid()
    val rs = createFakeResultSet(
      uploadJavaId,
      "2024-01-15 10:30:00+00",
      "document.png",
      "organizations/1002/1c1194bb-fb81-4bf6-b1c1-c5b6f589648f-document.png",
      null,
      null,
      null
    )
    val result = TestUpload.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.uploadId == uploadId)
    assert(result.fileName == "document.png")
    assert(result.filePath == "organizations/1002/1c1194bb-fb81-4bf6-b1c1-c5b6f589648f-document.png")
    assert(result.fileType == null)
    assert(result.thumbnailFilePath == null)
    assert(result.thumbnailState == null)
  }

  @Test
  fun `decode UUID with string fallback`() {
    val uuid = UUID.randomUUID()

    // Test direct UUID object
    val rs1 = createFakeResultSet(uuid)

    @Serializable
    data class UuidDto(@Contextual val id: UUID)

    val result1 = UuidDto.serializer().deserialize(ResultSetRowDecoder(rs1))

    assert(result1.id == uuid)

    // Test string fallback (when getObject returns string instead of UUID)
    val rs2 = createFakeResultSet(uuid.toString())  // Pass string instead of UUID
    val result2 = UuidDto.serializer().deserialize(ResultSetRowDecoder(rs2))

    assert(result2.id == uuid)
  }

  @Test
  fun `decode long from Timestamp for backward compatibility`() {
    val timestamp = Timestamp(1705315800000L)  // 2024-01-15 10:30:00 UTC
    val rs = createFakeResultSet(timestamp)

    @Serializable
    data class LongDto(val timestampMillis: Long)

    val result = LongDto.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.timestampMillis == 1705315800000L)
  }

  @Test
  fun `column index increments correctly`() {
    // This test verifies that the column index management works correctly
    // by ensuring fields are read in the correct order
    val rs = createFakeResultSet(
      "first",
      "second",
      "third",
      "fourth"
    )

    @Serializable
    data class OrderedFieldsDto(
      val field1: String,
      val field2: String,
      val field3: String,
      val field4: String
    )

    val result = OrderedFieldsDto.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.field1 == "first")
    assert(result.field2 == "second")
    assert(result.field3 == "third")
    assert(result.field4 == "fourth")
  }

  @Test
  fun `handles empty string correctly`() {
    val rs = createFakeResultSet(
      "",
      "   ",
      "normal"
    )

    @Serializable
    data class StringsDto(
      val empty: String,
      val whitespace: String,
      val normal: String
    )

    val result = StringsDto.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.empty == "")
    assert(result.whitespace == "   ")
    assert(result.normal == "normal")
  }

  @Test
  fun `handles extreme numeric values`() {
    val rs = createFakeResultSet(
      Byte.MIN_VALUE,
      Byte.MAX_VALUE,
      Short.MIN_VALUE,
      Short.MAX_VALUE,
      Int.MIN_VALUE,
      Int.MAX_VALUE,
      Long.MIN_VALUE,
      Long.MAX_VALUE,
      Float.MIN_VALUE,
      Float.MAX_VALUE,
      Double.MIN_VALUE,
      Double.MAX_VALUE
    )

    @Serializable
    data class ExtremesDto(
      val byteMin: Byte,
      val byteMax: Byte,
      val shortMin: Short,
      val shortMax: Short,
      val intMin: Int,
      val intMax: Int,
      val longMin: Long,
      val longMax: Long,
      val floatMin: Float,
      val floatMax: Float,
      val doubleMin: Double,
      val doubleMax: Double
    )

    val result = ExtremesDto.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.byteMin == Byte.MIN_VALUE)
    assert(result.byteMax == Byte.MAX_VALUE)
    assert(result.shortMin == Short.MIN_VALUE)
    assert(result.shortMax == Short.MAX_VALUE)
    assert(result.intMin == Int.MIN_VALUE)
    assert(result.intMax == Int.MAX_VALUE)
    assert(result.longMin == Long.MIN_VALUE)
    assert(result.longMax == Long.MAX_VALUE)
    assert(result.floatMin == Float.MIN_VALUE)
    assert(result.floatMax == Float.MAX_VALUE)
    assert(result.doubleMin == Double.MIN_VALUE)
    assert(result.doubleMax == Double.MAX_VALUE)
  }

  @Test
  fun `handles nested data classes positionally`() {
    val rs = createFakeResultSet(
      1L,
      "Goodstreet 23",
      "Rotterdam, The Netherlands",
      "Badstreet 42",
      "Amsterdam, The Netherlands"
    )

    @Serializable
    data class BillingAddress(
      val addressLine1: String,
      val addressLine2: String?
    )

    @Serializable
    data class InvoiceDetail(
      val id: Long,
      val senderAddress: BillingAddress,
      val recipientAddress: BillingAddress
    )

    val result = InvoiceDetail.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 1L)
    assert(result.senderAddress.addressLine1 == "Goodstreet 23")
    assert(result.senderAddress.addressLine2 == "Rotterdam, The Netherlands")
    assert(result.recipientAddress.addressLine1 == "Badstreet 42")
    assert(result.recipientAddress.addressLine2 == "Amsterdam, The Netherlands")
  }

  @Test
  fun `decode kotlin time Instant with PostgreSQL timestamp format as string`() {
    // This test demonstrates the handling of PostgreSQL timestamp format
    // PostgreSQL returns: "2026-02-04 04:31:16.935337+01" (space separator)
    // The decoder should handle this and convert to kotlin.time.Instant
    val postgresTimestamp = "2026-02-04 04:31:16.935337+01:00"

    val rs = createFakeResultSet(
      1L,
      postgresTimestamp  // String format as returned by some JDBC drivers
    )

    @Serializable
    data class MinimalRetailerDto(
      val id: Long,
      val createdAt: kotlin.time.Instant
    )

    // This test verifies that kotlin.time.Instant can be decoded from Postgres' timestamp string.
    val result = MinimalRetailerDto.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 1L)
    assert(result.createdAt.toString().contains("2026-02-04"))
  }

  @Test
  fun `decode kotlin time Instant with OffsetDateTime object`() {
    // This test verifies the normal case where JDBC returns an OffsetDateTime object
    val offsetDateTime = OffsetDateTime.parse("2026-02-04T04:31:16.935337+01:00")

    val rs = createFakeResultSet(
      1L,
      offsetDateTime
    )

    @Serializable
    data class MinimalRetailerDto(
      val id: Long,
      val createdAt: kotlin.time.Instant
    )

    val result = MinimalRetailerDto.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 1L)
    assert(result.createdAt.toString().contains("2026-02-04"))
  }

  @Test
  fun `decode kotlin time Instant with PostgreSQL timestamp without timezone`() {
    // This test reproduces the error where PostgreSQL returns a timestamp string without timezone information
    // Error case: "2026-02-04 03:31:16.935337" (no timezone)
    // This happens when timestamptz columns return their value as a string in the session timezone
    val javaSqlTimestamp = Timestamp.from(Instant.parse("2026-02-04T03:31:16.935337Z"))

    val rs = createFakeResultSet(
      1L,
      javaSqlTimestamp
    )

    @Serializable
    data class MinimalRetailerDto(
      val id: Long,
      val createdAt: kotlin.time.Instant
    )

    // This should work by assuming UTC when no timezone is present
    val result = MinimalRetailerDto.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 1L)
    assert(result.createdAt.toString() == "2026-02-04T03:31:16.935337Z")
  }

//  @Test
//  fun `decode nullable kotlin time Instant with PostgreSQL timestamp without timezone`() {
//    // Test the nullable case as well
//    val postgresTimestampWithoutTZ = "2026-02-04 03:31:16.935337"
//
//    val rs = createFakeResultSet(
//      2L,
//      postgresTimestampWithoutTZ,
//      null  // Also test null case
//    )
//
//    @Serializable
//    data class DtoWithNullableInstant(
//      val id: Long,
//      val createdAt: kotlin.time.Instant?,
//      val deletedAt: kotlin.time.Instant?
//    )
//
//    val result = DtoWithNullableInstant.serializer().deserialize(ResultSetRowDecoder(rs))
//
//    assert(result.id == 2L)
//    assert(result.createdAt != null)
//    assert(result.createdAt.toString() == "2026-02-04T03:31:16.935337Z")
//    assertNull(result.deletedAt)
//  }

  @Test
  fun `decode nullable kotlin time Instant from java sql Timestamp`() {
    // Test with both nullable and non-nullable Instants from java.sql.Timestamp
    val timestamp1 = Timestamp.from(Instant.parse("2026-02-04T10:15:30.123Z"))
    val timestamp2 = Timestamp.from(Instant.parse("2026-02-05T14:30:45.456Z"))

    val rs = createFakeResultSet(
      1L,
      timestamp1,  // java.sql.Timestamp for nullable field
      timestamp2,  // java.sql.Timestamp for non-nullable field
      null        // null for another nullable field
    )

    @Serializable
    data class DtoWithMixedInstants(
      val id: Long,
      val createdAt: kotlin.time.Instant?,     // nullable Instant from Timestamp
      val updatedAt: kotlin.time.Instant,      // non-nullable Instant from Timestamp
      val deletedAt: kotlin.time.Instant?      // nullable Instant (null value)
    )

    val result = DtoWithMixedInstants.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 1L)
    assert(result.createdAt != null)
    assert(result.createdAt.toString() == "2026-02-04T10:15:30.123Z")
    assert(result.updatedAt.toString() == "2026-02-05T14:30:45.456Z")
    assert(result.deletedAt == null)
  }

  @Test
  fun `decode nullable kotlin time Instant from String without timezone`() {
    // This test reproduces the CommissionDeal error where nullable Instant gets a string without timezone
    val timestampStringNoTz = "2026-07-04 02:31:16.935337"  // No timezone info, as returned by some JDBC queries

    val rs = createFakeResultSet(
      1L,
      "Test Deal",
      timestampStringNoTz,  // String without timezone for nullable Instant field
      null                   // Another nullable field
    )

    @Serializable
    data class TestCommissionDeal(
      val id: Long,
      val name: String,
      val effectiveFrom: kotlin.time.Instant?,  // nullable Instant from String
      val effectiveTo: kotlin.time.Instant?      // nullable Instant (null)
    )

    val result = TestCommissionDeal.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 1L)
    assert(result.name == "Test Deal")
    assert(result.effectiveFrom != null)
    assert(result.effectiveFrom.toString() == "2026-07-04T02:31:16.935337Z")
    assert(result.effectiveTo == null)
  }

  @Test
  fun `handles deserialization of @SqlJsonValue annotated properties`() {
    // Create a JSON string that would come from a JSONB column in Postgres (just a string).
    val permissionsJson = """[{"id":1,"name":"READ"},{"id":2,"name":"WRITE"},{"id":3,"name":"DELETE"}]"""

    val rs = createFakeResultSet(
      123L,
      permissionsJson
    )

    @Serializable
    data class UserPermission(
      val id: Long,
      val name: String
    )

    @Serializable
    data class User(
      val id: Long,
      @SqlJsonValue val permissions: List<UserPermission>
    )

    val result = User.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 123L)
    assert(result.permissions.size == 3)
    assert(result.permissions[0].id == 1L)
    assert(result.permissions[0].name == "READ")
    assert(result.permissions[1].id == 2L)
    assert(result.permissions[1].name == "WRITE")
    assert(result.permissions[2].id == 3L)
    assert(result.permissions[2].name == "DELETE")
  }

  @Test
  fun `handles null @SqlJsonValue annotated properties`() {
    val rs = createFakeResultSet(
      456L,
      null
    )

    @Serializable
    data class UserPermission(
      val id: Long,
      val name: String
    )

    @Serializable
    data class User(
      val id: Long,
      @SqlJsonValue val permissions: List<UserPermission>?
    )

    val result = User.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 456L)
    assert(result.permissions == null)
  }

  @Test
  fun `handles complex nested JSON structures`() {
    // Simulating a JSONB result from something like:
    // jsonb_build_object('count', 2, 'items', jsonb_agg(jsonb_build_object('productId', p.id, 'productName', p.name)))
    val nestedJson = """
      {
        "count": 2,
        "items": [
          {"productId": 10, "productName": "Widget"},
          {"productId": 20, "productName": "Gadget"}
        ]
      }
    """.trimIndent()

    val rs = createFakeResultSet(
      789L,
      nestedJson
    )

    @Serializable
    data class ProductInfo(
      val productId: Long,
      val productName: String
    )

    @Serializable
    data class OrderSummary(
      val count: Int,
      val items: List<ProductInfo>
    )

    @Serializable
    data class Order(
      val orderId: Long,
      @SqlJsonValue val summary: OrderSummary
    )

    val result = Order.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.orderId == 789L)
    assert(result.summary.count == 2)
    assert(result.summary.items.size == 2)
    assert(result.summary.items[0].productId == 10L)
    assert(result.summary.items[0].productName == "Widget")
    assert(result.summary.items[1].productId == 20L)
    assert(result.summary.items[1].productName == "Gadget")
  }

  @Test
  fun `decode postgres text array to List of String`() {
    // This test simulates a Postgres text[] array coming from the database
    // In real scenarios this would be something like: SELECT ARRAY['url1', 'url2']::text[]
    val postgresArray = arrayOf(
      "https://www.aliexpress.com/item/1005004318922481.html",
      "https://www.amazon.com/dp/B08N5WRWNW",
      "https://www.example.com/product/123"
    )

    val rs = createFakeResultSet(
      1L,
      "Product with URLs",
      postgresArray  // This simulates the text[] from Postgres
    )

    @Serializable
    data class ProductWithUrls(
      val id: Long,
      val name: String,
      val urls: List<String>
    )

    val result = ProductWithUrls.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 1L)
    assert(result.name == "Product with URLs")
    assert(result.urls.size == 3)
    assert(result.urls[0] == "https://www.aliexpress.com/item/1005004318922481.html")
    assert(result.urls[1] == "https://www.amazon.com/dp/B08N5WRWNW")
    assert(result.urls[2] == "https://www.example.com/product/123")
  }

  @Test
  fun `decode empty postgres text array`() {
    val emptyArray = emptyArray<String>()

    val rs = createFakeResultSet(
      2L,
      "Product without URLs",
      emptyArray
    )

    @Serializable
    data class ProductWithUrls(
      val id: Long,
      val name: String,
      val urls: List<String>
    )

    val result = ProductWithUrls.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 2L)
    assert(result.name == "Product without URLs")
    assert(result.urls.isEmpty())
  }

  @Test
  fun `decode postgres text array as SqlArray (PgArray simulation)`() {
    // This test simulates how Postgres actually returns arrays - as java.sql.Array objects
    // In production, Postgres returns a PgArray which implements java.sql.Array
    val stringArray = arrayOf("US-48", "CA", "FR")
    val sqlArray = FakeSqlArray(stringArray)

    val rs = createFakeResultSet(
      1002L,
      "Test Store",
      "example",
      "test-api-key",
      sqlArray  // This is what actually comes from Postgres (as PgArray)
    )

    @Serializable
    data class StoreWithRegions(
      val retailerId: Long,
      val name: String,
      val shopifySubdomain: String,
      val apiKey: String,
      val shippingRegionCodes: List<String>
    )

    val result = StoreWithRegions.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.retailerId == 1002L)
    assert(result.name == "Test Store")
    assert(result.shopifySubdomain == "example")
    assert(result.apiKey == "test-api-key")
    assert(result.shippingRegionCodes.size == 3) { "Expected 3 regions but got ${result.shippingRegionCodes.size}" }
    assert(result.shippingRegionCodes == listOf("US-48", "CA", "FR")) {
      "Expected [US-48, CA, FR] but got ${result.shippingRegionCodes}"
    }
  }

  @Test
  fun `decode nullable postgres text array`() {
    val rs = createFakeResultSet(
      3L,
      "Product with null URLs",
      null  // null array
    )

    @Serializable
    data class ProductWithOptionalUrls(
      val id: Long,
      val name: String,
      val urls: List<String>?
    )

    val result = ProductWithOptionalUrls.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 3L)
    assert(result.name == "Product with null URLs")
    assert(result.urls == null)
  }

  // ===== New comprehensive test coverage patterns =====

  @Test
  fun `decode deeply nested structures with three levels`() {
    val rs = createFakeResultSet(
      1L,                // id
      "Level 1 Name",              // level1.name
      100,                         // level1.level2.value
      "Level 3 Data",              // level1.level2.level3.data
      true                         // level1.level2.level3.flag
    )

    @Serializable
    data class Level3(
      val data: String,
      val flag: Boolean
    )

    @Serializable
    data class Level2(
      val value: Int,
      val level3: Level3
    )

    @Serializable
    data class Level1(
      val name: String,
      val level2: Level2
    )

    @Serializable
    data class DeeplyNestedDto(
      val id: Long,
      val level1: Level1
    )

    val result = DeeplyNestedDto.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 1L)
    assert(result.level1.name == "Level 1 Name")
    assert(result.level1.level2.value == 100)
    assert(result.level1.level2.level3.data == "Level 3 Data")
    assert(result.level1.level2.level3.flag == true)
  }

  @Test
  fun `decode nullable nested structure with all null values`() {
    val rs = createFakeResultSet(
      2L,                // id
      "Parent Name",               // name
      null,                        // nested.field1 (null)
      null,                        // nested.field2 (null)
      null                         // nested.field3 (null)
    )

    @Serializable
    data class NestedData(
      val field1: String,
      val field2: Int,
      val field3: Boolean
    )

    @Serializable
    data class ParentWithNullableNested(
      val id: Long,
      val name: String,
      val nested: NestedData?
    )

    val result = ParentWithNullableNested.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 2L)
    assert(result.name == "Parent Name")
    assert(result.nested == null)
  }

  @Test
  fun `decode partially null nested structure with mixed values`() {
    val rs = createFakeResultSet(
      3L,                // id
      "Mixed Parent",              // name
      "Value1",                    // nested1.field1
      42,                          // nested1.field2
      null,                        // nested2.field1 (null)
      null                         // nested2.field2 (null)
    )

    @Serializable
    data class SimpleNested(
      val field1: String,
      val field2: Int
    )

    @Serializable
    data class MixedNullableParent(
      val id: Long,
      val name: String,
      val nested1: SimpleNested,
      val nested2: SimpleNested?
    )

    val result = MixedNullableParent.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 3L)
    assert(result.name == "Mixed Parent")
    assert(result.nested1.field1 == "Value1")
    assert(result.nested1.field2 == 42)
    assert(result.nested2 == null)
  }

  @Test
  fun `decode multiple levels of nullable nesting`() {
    val rs = createFakeResultSet(
      4L,                // id
      "Root",                      // name
      "Inner1",                    // inner.name
      null,                        // inner.deeplyNested.value (null)
      null,                        // inner.deeplyNested.description (null)
      "Extra"                      // extraField
    )

    @Serializable
    data class DeeplyNested(
      val value: Int,
      val description: String
    )

    @Serializable
    data class InnerLevel(
      val name: String,
      val deeplyNested: DeeplyNested?
    )

    @Serializable
    data class RootLevel(
      val id: Long,
      val name: String,
      val inner: InnerLevel,
      val extraField: String
    )

    val result = RootLevel.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 4L)
    assert(result.name == "Root")
    assert(result.inner.name == "Inner1")
    assert(result.inner.deeplyNested == null)
    assert(result.extraField == "Extra")
  }

  @Test
  fun `throws detailed error for null in non-nullable nested field`() {
    val rs = createFakeResultSet(
      5L,                // id
      "Parent",                    // name
      null,                        // required.field1 (should throw)
      100                          // required.field2
    )

    @Serializable
    data class RequiredNested(
      val field1: String,          // Non-nullable
      val field2: Int
    )

    @Serializable
    data class ParentWithRequired(
      val id: Long,
      val name: String,
      val required: RequiredNested  // Non-nullable nested
    )

    val decoder = ResultSetRowDecoder(rs)
    val exception = runCatching {
      ParentWithRequired.serializer().deserialize(decoder)
    }.exceptionOrNull()

    // Check that error message contains useful debugging info
    assert(exception is SerializationException)
    assert(exception?.message?.contains("NULL value encountered") == true)
    assert(exception?.message?.contains("non-nullable") == true)
  }

  @Test
  fun `decode complex real-world scenario with multiple nested levels and nullable fields`() {
    val accountId = UUID.randomUUID()
    val managerId = UUID.randomUUID()

    val rs = createFakeResultSet(
      100L,              // order.id
      "2024-01-15 10:30:00+00",    // order.createdAt
      "PENDING",                   // order.status
      200L,                        // order.customer.id
      "John Doe",                  // order.customer.name
      "john@example.com",          // order.customer.email
      accountId,                   // order.customer.accountId
      managerId,                   // order.customer.managerId (nullable but present)
      "123 Main St",               // order.shippingAddress.street
      "New York",                  // order.shippingAddress.city
      "NY",                        // order.shippingAddress.state
      "10001",                     // order.shippingAddress.zipCode
      null,                        // order.billingAddress.street (all null for nullable nested)
      null,                        // order.billingAddress.city
      null,                        // order.billingAddress.state
      null,                        // order.billingAddress.zipCode
      99.99,                       // order.totalAmount
      "Special instructions"       // order.notes (nullable but present)
    )

    @Serializable
    data class Address(
      val street: String,
      val city: String,
      val state: String,
      val zipCode: String
    )

    @Serializable
    data class Customer(
      val id: Long,
      val name: String,
      val email: String,
      @Contextual val accountId: UUID,
      @Contextual val managerId: UUID?
    )

    @Serializable
    data class Order(
      val id: Long,
      val createdAt: CustomInstantForTests,
      val status: String,
      val customer: Customer,
      val shippingAddress: Address,
      val billingAddress: Address?,
      val totalAmount: Double,
      val notes: String?
    )

    val result = Order.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 100L)
    assert(result.status == "PENDING")
    assert(result.customer.id == 200L)
    assert(result.customer.name == "John Doe")
    assert(result.customer.email == "john@example.com")
    assert(result.customer.accountId == accountId)
    assert(result.customer.managerId == managerId)
    assert(result.shippingAddress.street == "123 Main St")
    assert(result.shippingAddress.city == "New York")
    assert(result.shippingAddress.state == "NY")
    assert(result.shippingAddress.zipCode == "10001")
    assert(result.billingAddress == null)
    assert(result.totalAmount == 99.99)
    assert(result.notes == "Special instructions")
  }

  @Test
  fun `decode sibling nested structures with different null patterns`() {
    val rs = createFakeResultSet(
      1L,                // id
      "First",                     // nested1.name
      100,                         // nested1.value
      null,                        // nested2.name (nullable nested, but only first field null)
      200,                         // nested2.value (non-null despite first field being null - should still deserialize)
      "Third",                     // nested3.name
      null                         // nested3.value (nullable field within non-nullable nested)
    )

    @Serializable
    data class NestedStruct(
      val name: String?,
      val value: Int?
    )

    @Serializable
    data class SiblingStructures(
      val id: Long,
      val nested1: NestedStruct,
      val nested2: NestedStruct,
      val nested3: NestedStruct
    )

    val result = SiblingStructures.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 1L)
    assert(result.nested1.name == "First")
    assert(result.nested1.value == 100)
    assert(result.nested2.name == null)
    assert(result.nested2.value == 200)
    assert(result.nested3.name == "Third")
    assert(result.nested3.value == null)
  }

  @Test
  fun `decode with @SqlJsonValue in deeply nested structure`() {
    val nestedJson = """{"metadata": {"version": "1.0", "tags": ["tag1", "tag2"]}}"""

    val rs = createFakeResultSet(
      1L,                // id
      "Container",                 // container.name
      42,                          // container.nested.value
      nestedJson,                  // container.nested.jsonData (@SqlJsonValue)
      "Footer"                     // footer
    )

    @Serializable
    data class JsonMetadata(
      val metadata: Map<String, kotlinx.serialization.json.JsonElement>
    )

    @Serializable
    data class NestedWithJson(
      val value: Int,
      @SqlJsonValue val jsonData: JsonMetadata
    )

    @Serializable
    data class Container(
      val name: String,
      val nested: NestedWithJson
    )

    @Serializable
    data class ComplexWithJson(
      val id: Long,
      val container: Container,
      val footer: String
    )

    val result = ComplexWithJson.serializer().deserialize(ResultSetRowDecoder(rs))

    assert(result.id == 1L)
    assert(result.container.name == "Container")
    assert(result.container.nested.value == 42)
    assert(result.container.nested.jsonData.metadata["version"].toString().contains("1.0"))
    assert(result.footer == "Footer")
  }
}
