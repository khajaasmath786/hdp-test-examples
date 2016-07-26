package com.jj.scala.hbase

import java.io.Closeable

import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client._
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.HBaseConfiguration

case class HBaseTableEntry(tableName: String, rowKey: String, columnFamily: ColumnFamily)

case class HBaseTableEntries(tableName: String, rowKey: String, columnFamilies: Seq[ColumnFamily])

case class ColumnFamily(name: String, columns: Map[String, String])

class ConnectionGetter (private val conf: Configuration) {
  val connection = ConnectionFactory.createConnection(HBaseConfiguration.create(conf))
}

class HBaseClient(private val connection: Connection) {

  private def asPuts(rowKey: String, cf: ColumnFamily): Put = cf match {
    case ColumnFamily(name, columns) =>
      val family = Bytes.toBytes(name)
      val p = new Put(Bytes.toBytes(rowKey))
      columns.foreach {
        case (column, value) => p.addColumn(family, Bytes.toBytes(column), Bytes.toBytes(value))
      }
      p
  }

  def insert(e: HBaseTableEntry): Unit = e match {
    case HBaseTableEntry(tableName, rowKey, columns) =>
      TryWith(connection.getTable(TableName.valueOf(tableName))) { t =>
        val puts = asPuts(rowKey, columns)
        t.put(puts)
      }
  }

  def insert(e: HBaseTableEntries): Unit = e match {
    case HBaseTableEntries(tableName, rowKey, columnFamilies) =>
      TryWith(connection.getTable(TableName.valueOf(tableName))) { t =>
        import scala.collection.JavaConverters._

        val puts = columnFamilies.map(c => asPuts(rowKey, c))
        t.put(puts.asJava)
      }
  }

  def find(table: String, rowKey: String, familyName: String, columns: Seq[String]): Result = {
    TryWith(connection.getTable(TableName.valueOf(table))) { t =>
      val bytes = Bytes.toBytes(familyName)
      val g = new Get(Bytes.toBytes(rowKey))
      columns.foreach(c => g.addColumn(bytes, Bytes.toBytes(c)))
      t.get(g)
    }
  }

  def delete(tableName: String, rowKey: String, columnFamilies: Seq[String]): Unit = TryWith(connection.getTable(TableName.valueOf(tableName))) { t =>
    val d = new Delete(Bytes.toBytes(rowKey))
    columnFamilies.foreach { family =>
      d.addFamily(Bytes.toBytes(family))
    }
    t.delete(d)
  }

  def delete(tableName: String, rowKey: String): Unit = {
    TryWith(connection.getTable(TableName.valueOf(tableName))) { t =>
      t.delete(new Delete(Bytes.toBytes(rowKey)))
    }
  }
}

object TryWith {
  def apply[T <: Closeable, Q](c: T)(f: (T) => Q): Q = {
    try {
      f(c)
    } finally {
      c.close()
    }
  }
}

object Main extends App {
  private val configuration: Configuration = new Configuration()
  configuration.addResource("src/main/resources/hive-site.xml")
  configuration.addResource("src/main/resources/core-site.xml")

  TryWith(new ConnectionGetter(configuration).connection) { c =>
    val client = new HBaseClient(c)
    val entry = HBaseTableEntry(
      "test",
      "test-1",
      ColumnFamily(
        "test-family",
        Map(
          "col1" -> "val1"
        )
      )
    )
    client.insert(entry)
  }
}