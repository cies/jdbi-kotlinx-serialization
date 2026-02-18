jdbi-kotlinx-serialization
==========================

A Kotlin "library" which extends [Jdbi](https://jdbi.org) to use
[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)
for deserialization of query results
instead of reflection-heavy Bean re-hydrator found in Jdbi.

Allows for a low-boilerplate and very fast (and reflection-free!) mapping between:

* `java.sql.ResultSet` objects and Kotlin data classes (both for rows *and* for JSON columns!),
* primitive types, and
* some additional Kotlin types (like `Uuid`, `kotlin.time` and `kotlinx.datetime` classes).


## Features

- Maps query results directly to Kotlin data classes using `kotlinx.serialization`
- Makes it easy to bind common Kotling types and `Iterable`s of those types
- Built-in support for Kotlin-specific types like `kotlin.time.Instant`, `kotlinx.datetime` types and `kotlin.uuid.Uuid`
- Full support for nested/complex data structures, and JSON column deserialization
- Handles Postgres arrays (e.g., `text[]`) seamlessly
- Proper handling of nullable database columns and Kotlin nullable types
- Convenient Jdbi extension functions for clean call-sites using Kotlin's type reification


## Project maturity

This is not yet a complete library (not on Maven Central) and it lacks a build script.

This project serves as a way to publish the code.
It is good for other to see it, to gather some feedback on wether or not this project is useful to a broader audience.

If you want to test it, just copy the code into you project for now.

It currently does not implement all of the Jdbi API.
The Jdbi API itself is still available when using this code: so no harm done.

For now it only implements what was needed in our project:

* Only implements binding arguments by name (i.e.: `:name`)
* Only has a replacement for `bindArray` (called `bindIterable`) as `bindList` is problematic with empty lists

Full API coverage is needed when properly releasing this project. But for now this suffices.

Obviously PRs are welcome :)

There are some tests (but without a build tool), but they are not easy to understand the usage patterns from.
Therefor below we provide some code examples that illustrate how the code in this project can be used...


### Usage examples

#### 1. Setup JDBI with Custom Arguments

```kotlin
import dropnext.lib.jdbi.createCustomizedJdbi
import javax.sql.DataSource

// Create a customized JDBI instance with Kotlin type support
val jdbi = createCustomizedJdbi(dataSource)

// For testing with automatic rollback
val testJdbi = createCustomizedJdbi(dataSource, rollbackOnlyMode = true)
```

#### 2. Define Your Data Classes

```kotlin
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid
import dropnext.lib.jdbi.rowdecoder.SqlJsonValue

@Serializable
data class User(
    val id: Long,
    val uuid: Uuid,
    val name: String,
    val email: String?,
    val createdAt: Instant,
    val tags: List<String>,  // Maps to PostgreSQL text[]
    @SqlJsonValue
    val metadata: UserMetadata  // Stored as JSON in database
)

@Serializable
data class UserMetadata(
    val preferences: Map<String, String>,
    val settings: UserSettings
)

@Serializable
data class UserSettings(
    val theme: String,
    val notifications: Boolean
)
```

#### 3. Query and Deserialize Results

```kotlin
import dropnext.lib.jdbi.rowdecoder.*

// Query single result
val user = jdbi.withHandle { handle ->
    handle.createQuery("""
        SELECT user_uuid, name, email, created_at, tags, metadata
        FROM users
        WHERE user_uuid = :uuid
    """)
    .bind("uuid", userId)
    .deserializeToOne<User>()
}

// Query multiple results
val users = jdbi.withHandle { handle ->
    handle.createQuery("SELECT * FROM users")
        .deserializeToList<User>()
}

// Using sequences for large result sets
jdbi.withHandle { handle ->
    handle.createQuery("SELECT * FROM users")
        .deserialize<User>()
        .useSequence { sequence ->
            sequence.forEach { user ->
                // Process each user
                println(user)
            }
        }
}
```

#### 4. Bind Kotlin Types as Query Parameters

```kotlin
import dropnext.lib.jdbi.bind.bindIterable
import kotlin.uuid.Uuid
import kotlin.time.Instant

jdbi.withHandle { handle ->
    // Bind individual Kotlin types
    handle.createUpdate("""
        INSERT INTO users (uuid, name, created_at)
        VALUES (:uuid, :name, :createdAt)
    """)
    .bind("uuid", Uuid.random())
    .bind("name", "John Doe")
    .bind("createdAt", Instant.now())
    .execute()

    // Bind collections of Kotlin types
    val userIds = listOf(Uuid.random(), Uuid.random())
    handle.createQuery("SELECT * FROM users WHERE uuid IN (<uuids>)")
        .bindIterable("uuids", userIds)
        .deserializeToList<User>()
}
```


## Components

This project is primarily a "row decoder" (deserializes a `ResultSet` object to `@Serializable` annotated Kotlin data classes).
But it also contains a convenient "argument binder extension", which reduces both boilerplate and the use of reflection when binding
common Kotlin types (or `Iterable`s of those type) to SQL queries.

### Row Ddecoder (`src/rowdecoder/`)

The core deserialization engine that converts SQL ResultSets to Kotlin objects:

- **`ResultSetRowDecoder`**: Main decoder implementation using kotlinx.serialization
- **`SqlJsonValue`**: Annotation to mark fields that should be deserialized from JSON columns
- **`jdbiExtensions.kt`**: Extension functions for Jdbi's `ResultBearing`, `Query`, and `Update` classes

### Kotlin argument binding (`src/bind/`)

Custom argument factories for Kotlin-specific types:

- **`InstantArgumentFactory`**: Converts `kotlinx.datetime.Instant` to JDBC `Timestamp`
- **`UuidArgumentFactory`**: Converts `kotlin.uuid.Uuid` to Java's `UUID`
- **`bindIterable`**: Extension function for binding collections of primitive types (or `Uuid`) to Jdbi queries without using JVM reflection

### Jdbi Factory (`src/`)

- **`createCustomizedJdbi`**: Factory function to create properly configured JDBI instances


## Advanced Features

### Nested Data Classes

The decoder supports deeply nested data structures:

```kotlin
@Serializable
data class Order(
    val id: Long,
    val customer: Customer,  // Nested object
    val items: List<String>,  // PostgreSQL array
    val shippingAddress: Address?  // Nullable nested object
)

@Serializable
data class Customer(
    val id: Long,
    val name: String,
    val email: String
)

@Serializable
data class Address(
    val street: String,
    val city: String,
    val country: String
)
```

### JSON Column Support

Use `@SqlJsonValue` to automatically deserialize JSON columns:

```kotlin
@Serializable
data class Product(
    val id: Long,
    val name: String,
    @SqlJsonValue
    val specifications: Map<String, Any>,  // Stored as JSON
    @SqlJsonValue
    val reviews: List<Review>  // Array of JSON objects
)
```

### Handling Nullable Fields

The decoder properly handles nullable database columns:

```kotlin
@Serializable
data class Article(
    val id: Long,
    val title: String,
    val subtitle: String?,  // Nullable column
    val publishedAt: Instant?,  // Nullable timestamp
    val tags: List<String>?  // Nullable array
)
```

## Error Handling

The library provides detailed error messages for common issues:

- NULL values in non-nullable fields
- Type mismatches between SQL and Kotlin types
- Malformed JSON in `@SqlJsonValue` fields
- Missing columns in ResultSet


## Future Enhancements

The `rowdecoder/README.md` file contains a roadmap of planned features inspired by `terpal-sql`:

1. **Column Verification & Metadata Validation**: Validate SQL result columns match expected structure
2. **Debug Mode**: Built-in tracing for troubleshooting deserialization
3. **Context-Rich Error Messages**: Include column names and types in errors
4. **Flexible Starting Index Support**: Support both 0-based and 1-based indexing
5. **Modular Encoder/Decoder System**: Extensible type-specific decoders
6. **Preview Functionality**: Inspect values during debugging
7. **Column Info Caching**: Performance optimization for large result sets


## License

Apache 2


## Credits

This library is inspired by:
- [terpal-sql](https://github.com/terpal-sql/terpal) - Inspiration for the ResultSet decoder pattern
- [Jdbi](https://jdbi.org) - Provides the database abstraction layer: this library replaces the reflection-heavy ResultSet mapper that comes with Jdbi
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) - Powers the deserialization: a great way of doing reflection-free deserialization on the JVM
