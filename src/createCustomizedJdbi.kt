package dropnext.lib.jdbi

import dropnext.lib.jdbi.bind.registerCustomArguments
import javax.sql.DataSource
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.transaction.RollbackOnlyTransactionHandler


/**
 * This should be the only function we use to create [Jdbi] instances.
 * To ensure that we always have access to the same functions.
 */
fun createCustomizedJdbi(dataSource: DataSource, rollbackOnlyMode: Boolean = false): Jdbi {
  val jdbi = Jdbi.create(dataSource).registerCustomArguments()

  // In TEST mode, we configure Jdbi to roll back transactions instead of committing them.
  // This ensures test isolation and prevents tests from modifying the database
  if (rollbackOnlyMode) jdbi.transactionHandler = RollbackOnlyTransactionHandler()

  return jdbi
}
