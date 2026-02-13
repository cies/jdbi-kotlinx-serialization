package dropnext.lib.jdbi.bind

import java.lang.reflect.Type
import java.sql.Timestamp
import java.util.Optional
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.argument.Argument
import org.jdbi.v3.core.argument.ArgumentFactory
import org.jdbi.v3.core.config.ConfigRegistry


/**
 * Jdbi [ArgumentFactory] for [kotlinx.datetime.Instant].
 * Converts [kotlinx.datetime.Instant] to JDBC-compatible [Timestamp].
 *
 * To be used in Jdbi `bind*` functions.
 */
class InstantArgumentFactory : ArgumentFactory {
  override fun build(type: Type, value: Any?, config: ConfigRegistry): Optional<Argument> {
    if (value !is Instant) return Optional.empty()
    return Optional.of(
      Argument { position, statement, _ ->
        statement.setTimestamp(position, Timestamp.from(value.toJavaInstant()))
      }
    )
  }
}

// /**
//  * Jdbi [ArgumentFactory] for [kotlinx.datetime.Instant].
//  * Converts [kotlinx.datetime.Instant] to JDBC-compatible [Timestamp].
//  *
//  * To be used in Jdbi `bind*` functions.
//  */
// class LongArgumentFactory : ArgumentFactory {
//   override fun build(type: Type, value: Any?, config: ConfigRegistry): Optional<Argument> {
//     if (value !is Long) return Optional.empty()
//     return Optional.of(Argument { position, statement, _ -> statement.setLong(position, value) })
//   }
// }

/**
 * Jdbi [ArgumentFactory] for [kotlinx.datetime.Instant].
 * Converts [kotlinx.datetime.Instant] to JDBC-compatible [Timestamp].
 *
 * To be used in Jdbi `bind*` functions.
 */
class UuidArgumentFactory : ArgumentFactory {
  override fun build(type: Type, value: Any?, config: ConfigRegistry): Optional<Argument> {
    if (value !is Uuid) return Optional.empty()
    return Optional.of(Argument { position, statement, _ -> statement.setObject(position, value.toJavaUuid()) })
  }
}

/** Register custom argument factories for kotlinx types. */
fun Jdbi.registerCustomArguments() = apply {
  registerArgument(InstantArgumentFactory())
  registerArgument(UuidArgumentFactory())
}
