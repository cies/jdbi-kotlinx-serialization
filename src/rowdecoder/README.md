Kotlinx.serialization based row mapper for Jdbi
===============================================

With this ResultSetRowDecoder, we can use `kotlinx.serialization` to deserialize our SQL results into Kotlin data classes;
similar to what `terpal-sql` delivers wrt ResultSet mapping.

It is currently only for Postgres. One way to make it work for other DBs can be found in `terpal-sql`.

----------------------------


Here some other features that `terpal-sql` has, and we do not have (can easily let LLMs port them over for us):


1. Column Verification & Metadata Validation ⭐⭐⭐

terpal-sql feature: Validates that SQL result columns match expected structure before decoding (see verifyColumns function at RowDecoder.kt:39-75)

Current gap: Your decoder doesn't verify column count or types match the DTO structure

Impact: HIGH - Prevents runtime errors and provides clear error messages when SQL doesn't match DTOs
Effort: MEDIUM - Need to extract column metadata and implement validation logic
Implementation: Add verification in decoder constructor that compares ResultSetMetaData with SerialDescriptor structure

  ---
2. Debug Mode with Detailed Tracing ⭐⭐⭐

terpal-sql feature: Built-in debug mode that logs each column access with preview values (RowDecoder.kt:99, 129-145)

Current gap: No debug/trace capabilities for troubleshooting deserialization issues

Impact: HIGH - Dramatically improves debugging experience when things go wrong
Effort: LOW - Add debug flag and logging statements
Implementation: Add debugMode parameter and log column access with preview values

  ---
3. Flexible Starting Index Support ⭐⭐

terpal-sql feature: Supports both 0-based and 1-based indexing via StartingIndex sealed interface (RowDecoder.kt:77-83)

Current gap: Hard-coded to 1-based indexing

Impact: MEDIUM - Better compatibility with different database systems
Effort: LOW - Simple abstraction to add
Implementation: Create StartingIndex enum and use throughout decoder

  ---
4. Context-Rich Error Messages ⭐⭐⭐

terpal-sql feature: Error messages include column names, types, and indices (RowDecoder.kt:69-74)

Current gap: Generic error messages like "NULL encountered for non-null at column X"

Impact: HIGH - Saves significant debugging time
Effort: LOW - Enhance existing error messages
Implementation: Include column names from metadata in all error messages

  ---
5. Modular Encoder/Decoder System ⭐⭐

terpal-sql feature: Extensible sets of encoders/decoders that can be composed (Encoding.kt:15-43)

Current gap: Monolithic decoder with all type handling built-in

Impact: MEDIUM - Better extensibility for custom types
Effort: HIGH - Significant refactoring required
Implementation: Extract type-specific decoders into separate classes

  ---
6. Preview Functionality for Debugging ⭐⭐

terpal-sql feature: preview method shows column values without full deserialization (ApiDecoders.kt:72)

Current gap: No way to inspect values during debugging

Impact: MEDIUM - Helpful for troubleshooting
Effort: MEDIUM - Need to implement safe preview logic
Implementation: Add preview method that safely converts column values to strings

  ---

  ---
8. Support for Contextual Serializers ⭐

terpal-sql feature: Better support for contextual serializers via SerializersModule (RowDecoder.kt:318-329)

Current gap: Limited contextual serializer support

Impact: LOW - Only needed for advanced use cases
Effort: MEDIUM - Need to understand kotlinx.serialization contextual system
Implementation: Add contextual serializer resolution logic

  ---
9. Inline Class Support ⭐

terpal-sql feature: Explicit handling of inline/value classes (RowDecoder.kt:387-391)

Current gap: Inline classes might not work correctly

Impact: LOW - Kotlin inline classes are less common
Effort: LOW - Add inline class detection and handling
Implementation: Check descriptor.isInline and handle appropriately

  ---
10. Column Info Caching ⭐⭐

terpal-sql feature: Column information extracted once and reused (DecodingContext carries columnInfos)

Current gap: Metadata accessed repeatedly during decoding

Impact: MEDIUM - Performance improvement for large result sets
Effort: LOW - Cache metadata on first access
Implementation: Store column info array in decoder instance
s


