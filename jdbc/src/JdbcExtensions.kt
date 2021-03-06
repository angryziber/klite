package klite.jdbc

import org.intellij.lang.annotations.Language
import java.io.InputStream
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.sql.Statement.RETURN_GENERATED_KEYS
import java.time.Period
import java.util.*
import javax.sql.DataSource
import kotlin.reflect.KClass

val namesToQuote = mutableSetOf("limit", "offset", "check", "table", "column", "constraint", "default", "desc", "distinct", "end", "foreign", "from", "grant", "group", "primary", "user")

fun <R> DataSource.query(table: String, id: UUID, mapper: ResultSet.() -> R): R =
  query(table, mapOf("id" to id), mapper = mapper).firstOrNull() ?: throw NoSuchElementException("$table:$id not found")

fun <R> DataSource.query(table: String, where: Map<String, Any?>, suffix: String = "", mapper: ResultSet.() -> R): List<R> =
  select("select * from $table", where, suffix, mapper)

fun <R> DataSource.select(@Language("SQL") select: String, where: Map<String, Any?>, suffix: String = "", mapper: ResultSet.() -> R): List<R> =
  withStatement("$select${whereExpr(where)} $suffix") {
    setAll(whereValues(where))
    executeQuery().map(mapper)
  }

fun DataSource.exec(@Language("SQL") expr: String, values: Sequence<Any?> = emptySequence(), callback: (Statement.() -> Unit)? = null): Int = withStatement(expr) {
  setAll(values)
  executeUpdate().also {
    if (callback != null) callback()
  }
}

fun <R> DataSource.withStatement(sql: String, block: PreparedStatement.() -> R): R = withConnection {
  try {
    prepareStatement(sql, RETURN_GENERATED_KEYS).use { it.block() }
  } catch (e: SQLException) {
    throw if (e.message?.contains("unique constraint") == true) AlreadyExistsException(e)
          else SQLException(e.message + "\nSQL: $sql", e.sqlState, e.errorCode, e)
  }
}

fun DataSource.insert(table: String, values: Map<String, *>): Int {
  val vals = values.filter { it.value !is GeneratedKey<*> }
  return exec(insertExpr(table, vals), setValues(vals)) {
    if (vals.size != values.size) processGeneratedKeys(values)
  }
}

fun DataSource.upsert(table: String, values: Map<String, *>, uniqueFields: String = "id"): Int =
  exec(insertExpr(table, values) + " on conflict ($uniqueFields) do update set ${setExpr(values)}", setValues(values) + setValues(values))

private fun insertExpr(table: String, values: Map<String, *>) = """
  insert into $table (${values.keys.joinToString { q(it) }})
    values (${values.entries.joinToString { (it.value as? SqlExpr)?.expr(it.key) ?: "?" }})""".trimIndent()

fun DataSource.update(table: String, where: Map<String, Any?>, values: Map<String, *>): Int =
  exec("update $table set ${setExpr(values)}${whereExpr(where)}", setValues(values) + whereValues(where))

fun DataSource.delete(table: String, where: Map<String, Any?>): Int =
  exec("delete from $table${whereExpr(where)}", whereValues(where))

private fun setExpr(values: Map<String, *>) = values.entries.joinToString { q(it.key) + " = " + ((it.value as? SqlExpr)?.expr(it.key) ?: "?") }

private fun whereExpr(where: Map<String, Any?>) = if (where.isEmpty()) "" else " where " +
  where.entries.joinToString(" and ") { (k, v) -> whereExpr(k, v) }

private fun whereExpr(k: String, v: Any?) = when(v) {
  null -> q(k) + " is null"
  is SqlExpr -> v.expr(k)
  is Iterable<*> -> inExpr(k, v)
  is Array<*> -> inExpr(k, v.toList())
  else -> q(k) + " = ?"
}

private fun q(name: String) = if (namesToQuote.contains(name)) "\"$name\"" else name

private fun inExpr(k: String, v: Iterable<*>) = q(k) + " in (${v.joinToString { "?" }})"

private fun setValues(values: Map<String, Any?>) = values.values.asSequence().flatMap { it.flatExpr() }
private fun Any?.flatExpr(): Iterable<Any?> = if (this is SqlExpr) values else listOf(this)

private fun whereValues(where: Map<String, Any?>) = where.values.asSequence().filterNotNull().flatMap { it.toIterable() }
private fun Any?.toIterable(): Iterable<Any?> = when (this) {
  is Array<*> -> toList()
  is Iterable<*> -> this
  else -> flatExpr()
}

operator fun PreparedStatement.set(i: Int, value: Any?) {
  if (value is InputStream) setBinaryStream(i, value)
  else setObject(i, JdbcConverter.to(value, connection))
}
fun PreparedStatement.setAll(values: Sequence<Any?>) = values.forEachIndexed { i, v -> this[i + 1] = v }

private fun <R> ResultSet.map(mapper: ResultSet.() -> R): List<R> = mutableListOf<R>().also {
  while (next()) it += mapper()
}

fun ResultSet.getId(column: String = "id") = getString(column).toId()
fun ResultSet.getIdOrNull(column: String) = getString(column)?.toId()
fun ResultSet.getInstant(column: String) = getTimestamp(column).toInstant()
fun ResultSet.getInstantOrNull(column: String) = getTimestamp(column)?.toInstant()
fun ResultSet.getLocalDate(column: String) = getDate(column).toLocalDate()
fun ResultSet.getLocalDateOrNull(column: String) = getDate(column)?.toLocalDate()
fun ResultSet.getPeriod(column: String) = Period.parse(getString(column))
fun ResultSet.getPeriodOrNull(column: String) = getString(column)?.let { Period.parse(it) }
fun ResultSet.getIntOrNull(column: String) = getObject(column)?.let { (it as Number).toInt() }

fun String.toId(): UUID = UUID.fromString(this)

inline fun <reified T: Enum<T>> ResultSet.getEnum(column: String) = enumValueOf<T>(getString(column))
inline fun <reified T: Enum<T>> ResultSet.getEnumOrNull(column: String) = getString(column)?.let { enumValueOf<T>(it) }

open class SqlExpr(@Language("SQL") protected val expr: String, val values: Collection<*> = emptyList<Any>()) {
  constructor(@Language("SQL") expr: String, vararg values: Any?): this(expr, values.toList())
  open fun expr(key: String) = expr
}

class SqlComputed(@Language("SQL") expr: String): SqlExpr(expr) {
  override fun expr(key: String) = q(key) + " = " + expr
}

open class SqlOp(val operator: String, value: Any? = null): SqlExpr(operator, if (value != null) listOf(value) else emptyList()) {
  override fun expr(key: String) = q(key) + " $operator" + ("?".takeIf { values.isNotEmpty() } ?: "")
}

val notNull = SqlOp("is not null")

class Between(from: Any, to: Any): SqlExpr("", from, to) {
  override fun expr(key: String) = q(key) + " between ? and ?"
}

class BetweenExcl(from: Any, to: Any): SqlExpr("", from, to) {
  override fun expr(key: String) = "${q(key)} >= ? and ${q(key)} < ?"
}

class NullOrOp(operator: String, value: Any?): SqlOp(operator, value) {
  override fun expr(key: String) = "(${q(key)} is null or $key $operator ?)"
}

class NotIn(values: Collection<*>): SqlExpr("", values) {
  constructor(vararg values: Any?): this(values.toList())
  override fun expr(key: String) = inExpr(key, values).replace(" in ", " not in ")
}

class GeneratedKey<T: Any>(val convertTo: KClass<T>? = null) {
  lateinit var value: T
}

private fun Statement.processGeneratedKeys(values: Map<String, *>) {
  generatedKeys.map {
    values.forEach { e ->
      @Suppress("UNCHECKED_CAST") (e.value as? GeneratedKey<Any>)?.let {
        val value = if (it.convertTo != null) getString(e.key) else getObject(e.key)
        it.value = JdbcConverter.from(value, it.convertTo) as Any
      }
    }
  }
}
