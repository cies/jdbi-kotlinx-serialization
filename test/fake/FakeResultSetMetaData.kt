package dropnext.lib.jdbi.fake

import java.sql.ResultSetMetaData
import java.sql.SQLException


/** Basic fake ResultSetMetaData implementation. */
open class FakeResultSetMetaData(protected val columns: List<FakeColumn>) : ResultSetMetaData {
  override fun getColumnCount(): Int = columns.size
  override fun getColumnType(column: Int): Int = columns[column - 1].sqlType

  // All other methods not used by ResultSetRowDecoder - throw UnsupportedOperationException
  override fun isAutoIncrement(column: Int): Boolean = throw UnsupportedOperationException("Not implemented in fake")
  override fun isCaseSensitive(column: Int): Boolean = throw UnsupportedOperationException("Not implemented in fake")
  override fun isSearchable(column: Int): Boolean = throw UnsupportedOperationException("Not implemented in fake")
  override fun isCurrency(column: Int): Boolean = throw UnsupportedOperationException("Not implemented in fake")
  override fun isNullable(column: Int): Int = throw UnsupportedOperationException("Not implemented in fake")
  override fun isSigned(column: Int): Boolean = throw UnsupportedOperationException("Not implemented in fake")
  override fun getColumnDisplaySize(column: Int): Int = throw UnsupportedOperationException("Not implemented in fake")
  override fun getColumnLabel(column: Int): String = throw UnsupportedOperationException("Not implemented in fake")
  override fun getColumnName(column: Int): String = throw UnsupportedOperationException("Not implemented in fake")
  override fun getSchemaName(column: Int): String = throw UnsupportedOperationException("Not implemented in fake")
  override fun getPrecision(column: Int): Int = throw UnsupportedOperationException("Not implemented in fake")
  override fun getScale(column: Int): Int = throw UnsupportedOperationException("Not implemented in fake")
  override fun getTableName(column: Int): String = throw UnsupportedOperationException("Not implemented in fake")
  override fun getCatalogName(column: Int): String = throw UnsupportedOperationException("Not implemented in fake")
  override fun getColumnTypeName(column: Int): String = throw UnsupportedOperationException("Not implemented in fake")
  override fun isReadOnly(column: Int): Boolean = throw UnsupportedOperationException("Not implemented in fake")
  override fun isWritable(column: Int): Boolean = throw UnsupportedOperationException("Not implemented in fake")
  override fun isDefinitelyWritable(column: Int): Boolean = throw UnsupportedOperationException("Not implemented in fake")
  override fun getColumnClassName(column: Int): String = throw UnsupportedOperationException("Not implemented in fake")
  override fun <T : Any?> unwrap(iface: Class<T>?): T = throw UnsupportedOperationException("Not implemented in fake")
  override fun isWrapperFor(iface: Class<*>?): Boolean = throw UnsupportedOperationException("Not implemented in fake")
}

/** Extended ResultSetMetaData with custom column metadata. */
class ExtendedFakeResultSetMetaData(private val columns: List<FakeColumnWithMetadata>) : ResultSetMetaData {
  override fun getColumnCount(): Int = columns.size
  override fun getColumnType(column: Int): Int = columns[column - 1].sqlType
  override fun getColumnLabel(column: Int): String = columns[column - 1].label
  override fun getColumnName(column: Int): String = columns[column - 1].name
  override fun getColumnTypeName(column: Int): String = "test_type"

  // Implement other required methods with defaults
  override fun isAutoIncrement(column: Int): Boolean = false
  override fun isCaseSensitive(column: Int): Boolean = true
  override fun isSearchable(column: Int): Boolean = true
  override fun isCurrency(column: Int): Boolean = false
  override fun isNullable(column: Int): Int = ResultSetMetaData.columnNullable
  override fun isSigned(column: Int): Boolean = true
  override fun getColumnDisplaySize(column: Int): Int = 10
  override fun getSchemaName(column: Int): String = "public"
  override fun getPrecision(column: Int): Int = 0
  override fun getScale(column: Int): Int = 0
  override fun getTableName(column: Int): String = "test_table"
  override fun getCatalogName(column: Int): String = "test_catalog"
  override fun isReadOnly(column: Int): Boolean = false
  override fun isWritable(column: Int): Boolean = true
  override fun isDefinitelyWritable(column: Int): Boolean = true
  override fun getColumnClassName(column: Int): String = "java.lang.Object"
  override fun <T : Any?> unwrap(iface: Class<T>?): T = throw UnsupportedOperationException()
  override fun isWrapperFor(iface: Class<*>?): Boolean = false
}

/** Broken metadata for error handling tests. */
class BrokenFakeResultSetMetaData(private val columns: List<FakeColumn>) : ResultSetMetaData {
  override fun getColumnCount(): Int = columns.size
  override fun getColumnType(column: Int): Int = columns[column - 1].sqlType
  override fun getColumnLabel(column: Int): String = throw SQLException("Metadata access failed")
  override fun getColumnName(column: Int): String = throw SQLException("Metadata access failed")
  override fun getColumnTypeName(column: Int): String = throw SQLException("Metadata access failed")

  // All other methods throw exception
  override fun isAutoIncrement(column: Int): Boolean = throw SQLException("Metadata access failed")
  override fun isCaseSensitive(column: Int): Boolean = throw SQLException("Metadata access failed")
  override fun isSearchable(column: Int): Boolean = throw SQLException("Metadata access failed")
  override fun isCurrency(column: Int): Boolean = throw SQLException("Metadata access failed")
  override fun isNullable(column: Int): Int = throw SQLException("Metadata access failed")
  override fun isSigned(column: Int): Boolean = throw SQLException("Metadata access failed")
  override fun getColumnDisplaySize(column: Int): Int = throw SQLException("Metadata access failed")
  override fun getSchemaName(column: Int): String = throw SQLException("Metadata access failed")
  override fun getPrecision(column: Int): Int = throw SQLException("Metadata access failed")
  override fun getScale(column: Int): Int = throw SQLException("Metadata access failed")
  override fun getTableName(column: Int): String = throw SQLException("Metadata access failed")
  override fun getCatalogName(column: Int): String = throw SQLException("Metadata access failed")
  override fun isReadOnly(column: Int): Boolean = throw SQLException("Metadata access failed")
  override fun isWritable(column: Int): Boolean = throw SQLException("Metadata access failed")
  override fun isDefinitelyWritable(column: Int): Boolean = throw SQLException("Metadata access failed")
  override fun getColumnClassName(column: Int): String = throw SQLException("Metadata access failed")
  override fun <T : Any?> unwrap(iface: Class<T>?): T = throw SQLException("Metadata access failed")
  override fun isWrapperFor(iface: Class<*>?): Boolean = throw SQLException("Metadata access failed")
}