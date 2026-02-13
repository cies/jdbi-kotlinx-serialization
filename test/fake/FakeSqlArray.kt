package dropnext.lib.jdbi.fake

import java.sql.Array as SqlArray
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types


/** Base fake SQL array implementation. */
open class FakeSqlArray(protected val elements: Array<*>) : SqlArray {
  override fun getBaseTypeName(): String = "text"
  override fun getBaseType(): Int = Types.VARCHAR
  override fun getArray(): Any = elements
  override fun getArray(map: MutableMap<String, Class<*>>?): Any = elements
  override fun getArray(index: Long, count: Int): Any =
    elements.sliceArray((index.toInt() - 1) until (index.toInt() - 1 + count))

  override fun getArray(index: Long, count: Int, map: MutableMap<String, Class<*>>?): Any =
    getArray(index, count)

  override fun getResultSet(): ResultSet = throw UnsupportedOperationException("Not implemented in fake")
  override fun getResultSet(map: MutableMap<String, Class<*>>?): ResultSet =
    throw UnsupportedOperationException("Not implemented in fake")

  override fun getResultSet(index: Long, count: Int): ResultSet =
    throw UnsupportedOperationException("Not implemented in fake")

  override fun getResultSet(index: Long, count: Int, map: MutableMap<String, Class<*>>?): ResultSet =
    throw UnsupportedOperationException("Not implemented in fake")

  override fun free() = Unit
}

/** Failing SQL array for error testing. */
class FailingFakeSqlArray : SqlArray {
  override fun getBaseTypeName(): String = "text"
  override fun getBaseType(): Int = Types.VARCHAR

  override fun getArray(): Any =
    throw SQLException("Simulated array access failure")

  override fun getArray(map: MutableMap<String, Class<*>>?): Any =
    throw SQLException("Simulated array access failure")

  override fun getArray(index: Long, count: Int): Any =
    throw SQLException("Simulated array access failure")

  override fun getArray(index: Long, count: Int, map: MutableMap<String, Class<*>>?): Any =
    throw SQLException("Simulated array access failure")

  override fun getResultSet(): ResultSet =
    throw UnsupportedOperationException()

  override fun getResultSet(map: MutableMap<String, Class<*>>?): ResultSet =
    throw UnsupportedOperationException()

  override fun getResultSet(index: Long, count: Int): ResultSet =
    throw UnsupportedOperationException()

  override fun getResultSet(index: Long, count: Int, map: MutableMap<String, Class<*>>?): ResultSet =
    throw UnsupportedOperationException()

  override fun free() = Unit
}
