package dropnext.lib.jdbi.rowdecoder

import java.sql.ResultSet
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import org.jdbi.v3.core.result.ResultBearing
import org.jdbi.v3.core.result.ResultIterable
import org.jdbi.v3.core.statement.Query
import org.jdbi.v3.core.statement.Update


/** [ResultSet] deserializing function that explicitly takes a serializer as an argument. */
fun <T> ResultBearing.deserialize(serializer: KSerializer<T>): ResultIterable<T> =
  this.map { rs: ResultSet, _ -> serializer.deserialize(ResultSetRowDecoder(rs)) }

/** [ResultSet] deserializing function on that infers the serializer from the type parameter. */
inline fun <reified T> ResultBearing.deserialize(): ResultIterable<T> = this.deserialize(serializer<T>())

inline fun <reified T> ResultBearing.deserializeToList(): List<T> = this.deserialize(serializer<T>()).list()

inline fun <reified T> ResultBearing.deserializeToOne(): T? = this.deserialize<T>().findFirst().orElse(null)

inline fun <reified T> Query.deserializeToOne(): T? = this.setMaxRows(1).deserialize<T>().findFirst().orElse(null)

fun Update.executeReturningLong(key: String): Long? = this.executeAndReturnGeneratedKeys(key)
  .deserializeToOne<Long>()


/**
 * Stream all the rows of the result set out with a [Sequence]. Handles closing of the underlying iterator.
 *
 *     `handle.createQuery(...).mapTo<Result>().useSequence { var firstResult = it.first() }`
 *
 * Copied from `jdbi3-kotlin` package.
 */
inline fun <O> ResultIterable<O>.useSequence(block: (Sequence<O>) -> Unit): Unit =
  this.iterator().use { block(it.asSequence()) }

