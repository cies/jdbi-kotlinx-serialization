package dropnext.lib.jdbi.fake

import java.sql.Array as SqlArray
import java.sql.ResultSetMetaData


/** ResultSet with custom metadata for testing error messages. */
class FakeResultSetWithMetadata(
  private val columnsWithMetadata: List<FakeColumnWithMetadata>
) : BaseFakeResultSet() {

  private fun fakeColumnValueAt(index: Int): Any? {
    require(index in 1..columnsWithMetadata.size) { "Invalid column index: $index" }
    val value = columnsWithMetadata[index - 1].value
    lastWasNull = value == null
    return value
  }

  override fun getObject(columnIndex: Int): Any? = fakeColumnValueAt(columnIndex)

  override fun <T> getObject(columnIndex: Int, type: Class<T>): T? {
    val value = fakeColumnValueAt(columnIndex)
    @Suppress("UNCHECKED_CAST")
    return value as T?
  }

  override fun getBoolean(columnIndex: Int): Boolean = fakeColumnValueAt(columnIndex) as? Boolean ?: false
  override fun getByte(columnIndex: Int): Byte = fakeColumnValueAt(columnIndex) as? Byte ?: 0
  override fun getShort(columnIndex: Int): Short = fakeColumnValueAt(columnIndex) as? Short ?: 0
  override fun getInt(columnIndex: Int): Int = fakeColumnValueAt(columnIndex) as? Int ?: 0
  override fun getLong(columnIndex: Int): Long = fakeColumnValueAt(columnIndex) as? Long ?: 0L
  override fun getFloat(columnIndex: Int): Float = fakeColumnValueAt(columnIndex) as? Float ?: 0f
  override fun getDouble(columnIndex: Int): Double = fakeColumnValueAt(columnIndex) as? Double ?: 0.0
  override fun getString(columnIndex: Int): String? = fakeColumnValueAt(columnIndex)?.toString()

  override fun getArray(columnIndex: Int): SqlArray {
    val value = fakeColumnValueAt(columnIndex)
    return when (value) {
      is SqlArray -> value
      is Array<*> -> FakeSqlArray(value)
      null -> throw UnsupportedOperationException("NULL array")
      else -> throw UnsupportedOperationException("Cannot convert $value to SqlArray")
    }
  }

  override fun getTimestamp(columnIndex: Int) = throw UnsupportedOperationException("Not implemented in fake")

  override fun getMetaData(): ResultSetMetaData = ExtendedFakeResultSetMetaData(columnsWithMetadata)
}

/** ResultSet with broken metadata for error handling tests. */
class BrokenMetadataResultSet(private val columns: List<FakeColumn>) : BaseFakeResultSet() {

  private fun fakeColumnValueAt(index: Int): Any? {
    require(index in 1..columns.size) { "Invalid column index: $index" }
    val value = columns[index - 1].value
    lastWasNull = value == null
    return value
  }

  override fun getObject(columnIndex: Int): Any? = fakeColumnValueAt(columnIndex)
  override fun <T> getObject(columnIndex: Int, type: Class<T>): T? {
    val value = fakeColumnValueAt(columnIndex)
    @Suppress("UNCHECKED_CAST")
    return value as T?
  }

  override fun getBoolean(columnIndex: Int): Boolean = fakeColumnValueAt(columnIndex) as? Boolean ?: false
  override fun getByte(columnIndex: Int): Byte = fakeColumnValueAt(columnIndex) as? Byte ?: 0
  override fun getShort(columnIndex: Int): Short = fakeColumnValueAt(columnIndex) as? Short ?: 0
  override fun getInt(columnIndex: Int): Int = fakeColumnValueAt(columnIndex) as? Int ?: 0
  override fun getLong(columnIndex: Int): Long = fakeColumnValueAt(columnIndex) as? Long ?: 0L
  override fun getFloat(columnIndex: Int): Float = fakeColumnValueAt(columnIndex) as? Float ?: 0f
  override fun getDouble(columnIndex: Int): Double = fakeColumnValueAt(columnIndex) as? Double ?: 0.0
  override fun getString(columnIndex: Int): String? = fakeColumnValueAt(columnIndex)?.toString()
  override fun getArray(columnIndex: Int): SqlArray = throw UnsupportedOperationException("Not implemented")

  override fun getTimestamp(columnIndex: Int) = throw UnsupportedOperationException("Not implemented")

  override fun getMetaData(): ResultSetMetaData = BrokenFakeResultSetMetaData(columns)
}
