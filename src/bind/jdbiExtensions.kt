package dropnext.lib.jdbi.bind

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.UUID
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import org.jdbi.v3.core.statement.SqlStatement


/**
 * We use this to avoid using Jdbi's argument mappers on arrays that internally use reflection.
 *
 * Local function in `inline` functions are not yet supported, and since [bindIterable] is `public`, this has to be too.
 */
inline fun <reified T : Any> iterableTypeOf(): ParameterizedType = object : ParameterizedType {
  override fun getActualTypeArguments(): Array<Type> = arrayOf(T::class.javaObjectType)
  override fun getRawType(): Type = Iterable::class.java
  override fun getOwnerType(): Type? = null
}

/**
 * Use this instead of Jdbi's `bindArray` and `bindList` functions.
 *
 * We may need to extend this for additional types (like Kotlin's time types), because this does not use
 * `kotlinx.serialization` under the hood like our [dropnext.lib.jdbi.rowdecoder.ResultSetRowDecoder].
 * Converting to the Java types that Jdbi understands is straight forward, and good enough for now.
 */
inline fun <reified I : Any, T : SqlStatement<T>> SqlStatement<T>.bindIterable(name: String, values: Iterable<I>): T {
  val mapped: Iterable<*> = when (I::class) {
    Uuid::class -> values.map { (it as Uuid).toJavaUuid() }
    else -> values
  }
  val type = when (I::class) {
    Uuid::class -> iterableTypeOf<UUID>()
    else -> iterableTypeOf<I>()
  }
  return this.bindByType(name, mapped, type)
}
