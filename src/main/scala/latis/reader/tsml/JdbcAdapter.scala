package latis.reader.tsml

import java.nio.ByteBuffer
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement
import java.util.Calendar
import java.util.TimeZone

import scala.Option.option2Iterable
import scala.collection.Map
import scala.collection.Seq
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

import com.typesafe.scalalogging.LazyLogging

import javax.naming.InitialContext
import javax.naming.NameNotFoundException
import javax.sql.DataSource
import latis.data.Data
import latis.dm.Binary
import latis.dm.Dataset
import latis.dm.Function
import latis.dm.Index
import latis.dm.Integer
import latis.dm.Real
import latis.dm.Scalar
import latis.dm.Text
import latis.dm.Variable
import latis.ops.Operation
import latis.ops.Projection
import latis.ops.RenameOperation
import latis.ops.filter.FirstFilter
import latis.ops.filter.LastFilter
import latis.ops.filter.LimitFilter
import latis.ops.filter.Selection
import latis.reader.tsml.ml.Tsml
import latis.time.Time
import latis.time.TimeFormat
import latis.time.TimeScale
import latis.util.DataUtils
import latis.util.StringUtils
import latis.dm.Tuple

/* 
 * TODO: release connection as soon as possible?
 * risky leaving it open waiting for client to iterate
 * cache eagerly?
 * at least close after first iteration is complete, data in cache
 */

/**
 * Adapter for databases that support JDBC.
 */
class JdbcAdapter(tsml: Tsml) extends IterativeAdapter[JdbcAdapter.JdbcRecord](tsml) with LazyLogging {
  //TODO: catch exceptions and close connections

  def getRecordIterator: Iterator[JdbcAdapter.JdbcRecord] = getProperty("limit") match {
    case Some(lim) if (lim.toInt == 0) => new JdbcAdapter.JdbcEmptyIterator()
    case _ => new JdbcAdapter.JdbcRecordIterator(resultSet)
  }

  /**
   * Parse the data based on the Variable type (and the database column type, for time).
   */
  def parseRecord(record: JdbcAdapter.JdbcRecord): Option[Map[String, Data]] = {
    val map = varsWithTypes.map(vt => {
      val parse = (parseTime orElse parseReal orElse parseInteger orElse parseText orElse parseBinary)
      parse(vt).asInstanceOf[(String, Data)]
    }).toMap

    val sm = Some(map)
    sm
  }

  //TODO: might be nice to pass record to the PartialFunctions so we don't have to expose the ResultSet

  /**
   * Experiment with overriding just one case using PartialFunctions.
   * Couldn't just delegate to function due to the "if' guard.
   */
  protected val parseTime: PartialFunction[(Variable, Int), (String, Data)] = {
    //Note, need dbtype for filter but can't do anything outside the case
    case (v: Time, dbtype: Int) if (dbtype == java.sql.Types.TIMESTAMP) => {
      val name = getVariableName(v)
      val gmtCalendar = Calendar.getInstance(TimeZone.getTimeZone("GMT")) //TODO: cache so we don't have to call for each sample?
      var time = resultSet.getTimestamp(name, gmtCalendar).getTime
      val s = if (resultSet.wasNull) v.getFillValue.asInstanceOf[String]
      else {
        v.getMetadata("units") match {
          case Some(format) => TimeFormat(format).format(time)
          case None => TimeFormat.ISO.format(time) //default to ISO yyyy-MM-ddTHH:mm:ss.SSS
        }
      }
      (name, Data(s))
    }
  }

  protected val parseReal: PartialFunction[(Variable, Int), (String, Data)] = {
    case (v: Real, _) => {
      val name = getVariableName(v)
      var d = resultSet.getDouble(name)
      if (resultSet.wasNull) d = v.getFillValue.asInstanceOf[Double]
      (name, Data(d))
    }
  }

  protected val parseInteger: PartialFunction[(Variable, Int), (String, Data)] = {
    case (v: Integer, _) => {
      val name = getVariableName(v)
      var l = resultSet.getLong(name)
      if (resultSet.wasNull) l = v.getFillValue.asInstanceOf[Long]
      (name, Data(l))
    }
  }

  protected val parseText: PartialFunction[(Variable, Int), (String, Data)] = {
    case (v: Text, _) => {
      val name = getVariableName(v)
      var s = resultSet.getString(name)
      if (resultSet.wasNull) s = v.getFillValue.asInstanceOf[String]
      s = StringUtils.padOrTruncate(s, v.length) //fix length as defined in tsml, default to 4
      (name, Data(s))
    }
  }

  protected val parseBinary: PartialFunction[(Variable, Int), (String, Data)] = {
    case (v: Binary, _) => {
      val name = getVariableName(v)
      var bytes = resultSet.getBytes(name)
      val max_length = v.getSize //will look for "length" in metadata, error if not defined
      if (bytes.length > max_length) {
        val msg = s"JdbcAdapter found ${bytes.length} bytes which is longer than the max size: ${max_length}. The data will be truncated."
        logger.warn(msg)
        bytes = bytes.take(max_length) //truncate so we don't get buffer overflow
      }
      
      //allocate a ByteBuffer for the max length
      val bb = ByteBuffer.allocate(max_length)
      //add the data
      bb.put(bytes)
      //add termination mark
      bb.put(DataUtils.nullMark)
      
      //Set the "limit" to the end of the data and rewind the position to the start.
      //Note, the capacity will remain at the max length.
      bb.flip
      
      (name, Data(bb))
    }
  }

  /**
   * Pairs of projected Variables (Scalars) and their database types.
   * Note, this will honor the order of the variables in the projection clause.
   */
  lazy val varsWithTypes = {
    //Get list of projected Scalars in projection order paired with their database type.
    //Saves us having to get the type for every sample.
    //Note, uses original variable names which are replaced for a rename operation as needed.
    lazy val v = getOrigDataset match {
      case Dataset(v) => v
      case _ => null
    }
    val vars: Seq[Variable] = if (projectedVariableNames.isEmpty) getOrigScalars
    else projectedVariableNames.flatMap(v.findVariableByName(_)) //TODO: error if not found? redundant with other (earlier?) test

    //TODO: Consider case where PI does rename. User should never see orig names so should be able to use new name.

    //Get the types of these variables in the database.
    //Note, ResultSet columns should have new names from rename.
    val md = resultSet.getMetaData
    val types = vars.map(v => md.getColumnType(resultSet.findColumn(getVariableName(v))))

    //Combine the variables with their database types in a Seq of pairs.
    vars zip types
  }

  //Handle the Projection and Selection Operation-s
  //keep seq of names instead  //private var projection = "*"
  private var projectedVariableNames = Seq[String]()
  protected def getProjectedVariableNames = if (projectedVariableNames.isEmpty) getOrigScalarNames else projectedVariableNames

  protected val selections = ArrayBuffer[String]()

  //Keep map to store Rename operations until they are needed when constructing the sql.
  private val renameMap = mutable.Map[String, String]()

  /**
   * Use this to get the name of a Variable so we can apply rename.
   */
  protected def getVariableName(v: Variable): String = renameMap.get(v.getName) match {
    case Some(newName) => newName
    case None => v.getName
  }

  //Define sorting order.
  private var order = "ASC"

  /**
   * Handle the operations if we can so we can reduce the data volume at the source
   * so the parent adapter doesn't have to do as much.
   * Return true if this adapter is taking responsibility for applying the operation
   * or false if it won't.
   */
  override def handleOperation(operation: Operation): Boolean = operation match {
    //TODO: should the handleFoo delegates be responsible for the true/false?
    case p: Projection => handleProjection(p)

    //TODO: factor out handleSelection?
    case sel @ Selection(name, op, value) => getOrigDataset match {
      case Dataset(v) => v.findVariableByName(name) match {
        //TODO: allow use of renamed variable? but sql where wants orig name
        case Some(v) if (v.isInstanceOf[Time]) => handleTimeSelection(name, op, value)
        case Some(v) if (getOrigScalarNames.contains(name)) => {
          //TODO: enable use of aliases
          //add a selection to the sql, may need to change operation
          op match {
            case "==" => v match {
              case _: Text => selections append name + "=" + quoteStringValue(value); true
              case _       => selections append name + "=" + value; true
            }
            case "=~" =>
              selections append name + " like '%" + value + "%'"; true
            case "~" => false //almost equal (e.g. nearest sample) not supported by sql
            case _ => v match {
              case _: Text => selections append name + op + quoteStringValue(value); true
              case _       => selections append name + op + value; true
            }
          }
        }
        case _ => {
          logger.warn("Dataset is empty")
          false
        }
      }
      case _ => {
        //This is not one of our variables, let latis handle it.
        logger.warn("JdbcAdapter can't process selection for unknown parameter: " + name)
        false
      }
    }

    case _: FirstFilter => {
      //make sure we are using ascending order
      order = "ASC"; 
      //add a limit property of one so we only get the first record
      setProperty("limit", "1")
      //let the caller know that we handled this operation
      true
    }
      
    case _: LastFilter => {
      //get results in descending order so the first record is the "last" one
      order = "DESC"; 
      //add a limit property of one so we only get the first (now last) record
      setProperty("limit", "1")
      //let the caller know that we handled this operation
      true 
    }

    case LimitFilter(limit) => getProperty("limit") match {
      // If it is already defined in the TSML, check if the requested
      // limit is greater than Int enforced limit)
      case Some(lim) if (lim.toInt < limit) => {
        true
      }
      // Either no limit was specified or the limit in the TSML is greater than the requested limit
      case _ if (limit >= 0)=> {
        setProperty("limit", limit.toString)
        true
      }
      case _ => throw new UnsupportedOperationException("Invalid limit specified");
    }
      
    //Rename operation: apply in projection clause of sql: 'select origName as newName'
    //These will be combined with the projected variables in the select clause with "old as new".
    case RenameOperation(origName, newName) => {
      renameMap += (origName -> newName)
      true
    }

    //TODO: handle exception, return false (not handled)?

    case _ => false //not an operation that we can handle
  }

  /**
   * Make sure the given string is surrounded in quotes.
   */
  private def quoteStringValue(s: String): String = {
    //don't add if it is already quoted
    "'" + s.replaceAll("^['\"]","").replaceAll("['\"]$","") + "'"
  }
  
  /**
   * Handle a Projection clause.
   */
  def handleProjection(projection: Projection): Boolean = projection match {
    case p @ Projection(names) => {
      //only names that are found in the original dataset are included in the search query
      projectedVariableNames = names.filterNot(getOrigDataset.findVariableByName(_) == None) 
      false //this way the default Projection Operation will also be applied after derived fields are created.
    }
  }
  
  /**
   * Special handling for a time selection since there are various formatting issues.
   */
  def handleTimeSelection(vname: String, op: String, value: String): Boolean = {
    //support ISO time string as value

    //Get the Time variable with the given name
    val v = getOrigDataset match {
      case Dataset(v) => v
      case _ => null
    }
    //TODO: apply later, e.g. so projection and rename can be applied first
    val tvar = v.findVariableByName(vname) match {
      case Some(t: Time) => t
      case _ => throw new RuntimeException("Time variable not found in dataset.")
    }
    val tvname = getVariableName(tvar)
    
    //get time type from tsml
    val vtype = tsml.dataset.findVariableMl(tvname) match {
      case Some(ml) => ml.getAttribute("type") match {
        case Some(t) => t
        case None => "real"
      }
      case None => throw new Exception(s"Could not find variable with name $tvname in tsml.")
    }
    
    vtype match {
      case "text" => {
        //A JDBC dataset with time defined as text implies the times are represented as a Timestamp.
        //JDBC doesn't generally like the 'T' in the iso time. (e.g. Derby)
        //Parse value into a Time then format consistent with java.sql.Timestamp.toString: yyyy-mm-dd hh:mm:ss.fffffffff
        //This should also treat the time as GMT. (Timestamp has internal Gregorian$Date which has the local time zone.)
        val time = tvar.getMetadata("units") match {
          case Some(format) => Time.fromIso(value).format(format)
          case None => Time.fromIso(value).format("yyyy-MM-dd HH:mm:ss.SSS") //Default that seems to work for most databases, not Oracle
          //TODO: too late, Time will add units if none are defined, defaulting to the ISO that doesn't generally work
          //  require tsml to define other units
        }
        selections += tvname + op + "'" + time + "'" //sql wants quotes around time value
        true
      }
      case _ => {
        //So, we have a numeric time variable but need to figure out if the selection value is
        //  a numeric time (in native units) or an ISO time that needs to be converted.
        if (StringUtils.isNumeric(value)) {
          this.selections += tvname + op + value
          true
        } else tvar.getMetadata("units") match {
          //Assumes selection value is an ISO 8601 formatted string
          case None => throw new RuntimeException("The dataset does not have time units defined for: " + tvname)
          case Some(units) => {
            //convert ISO time selection value to dataset units
            //TODO: generalize for all unit conversions
            try {
              val t = Time.fromIso(value).convert(TimeScale(units)).getValue
              this.selections += tvname + op + t
              true
            } catch {
              case iae: IllegalArgumentException => throw new RuntimeException("The time value is not in a supported ISO format: " + value)
              case e: Exception => throw new RuntimeException("Unable to parse time selection: " + value, e)
            }
          }
        }
      }
    }
  }

  /**
   * Override to apply projection. Exclude Variables not listed in the projection.
   * Only works for Scalars, for now.
   * If the rename operation needs to be applied, a new temporary Scalar will be created
   * with a copy of the original's metadata with the 'name' changed.
   */
  override def makeScalar(s: Scalar): Option[Scalar] = {
    //TODO: deal with composite names for nested vars
    getProjectedVariableNames.find(s.hasName(_)) match { //account for aliases
      case Some(_) => { //projected, see if it needs to be renamed
        val tmpScalar = renameMap.get(s.getName) match {
          case Some(newName) => s.getMetadata("alias") match { //keep the old name as an alias to allow later projection. 
            case Some(a) => s.updatedMetadata("alias" -> (a + "," + s.getName)).updatedMetadata("name" -> newName)
            case None => s.updatedMetadata("alias" -> s.getName).updatedMetadata("name" -> newName)
          }
          case None => s
        }
        super.makeScalar(tmpScalar)
      }
      case None => None //not projected
    }
  }

  //---- Database Stuff -------------------------------------------------------

  /**
   * Execute the SQL query.
   */
  private def executeQuery: ResultSet = {
    val sql = makeQuery
    logger.debug("Executing sql query: " + sql)

    //Apply optional limit to the number of rows
    //TODO: Figure out how to warn the user if the limit is exceeded
    getProperty("limit") match {
      case Some(limit) => statement.setMaxRows(limit.toInt)
      case _ =>
    }

    //Allow specification of number of rows to fetch at a time.
    getProperty("fetchSize") match {
      case Some(fetchSize) => statement.setFetchSize(fetchSize.toInt)
      case _ =>
    }

    statement.executeQuery(sql)
  }

  /**
   * Get the name of the database table from the adapter tsml attributes.
   */
  def getTable: String = getProperty("table") match {
    case Some(s) => s
    case None => throw new RuntimeException("JdbcAdapter needs to have a 'table' defined.")
  }

  /**
   * Build the select clause.
   * If no projection operation was provided, include all
   * since the tsml might expose only some database columns.
   * Apply rename operations.
   */
  protected def makeProjectionClause: String = {
    getProjectedVariableNames.map(name => {
      //If renamed, replace 'name' with 'name as "name2"'.
      //Use quotes so we can use reserved words like "min" (needed by Sybase).
      renameMap.get(name) match {
        case Some(name2) => name + " as \"" + name2 + "\""
        case None => name
      }
    }).mkString(",")
  }

  /**
   * Allow tsml to specify a "hint" property to add to the SQL between the "select" 
   * and projection clause. Appropriate for Oracle, at least.
   * For example: 
   *   select /*+INDEX (TMDISCRETE TMDISCRETE_ALL_IDX)*/ * from TMDISCRETE ...
   */
  protected def makeHint: String = getProperty("hint") match {
    case Some(hint) => hint + " " //add white space so it plays nice in makeQuery
    case None => ""
  }
  
  /**
   * Construct the SQL query.
   * Look for "sql" defined in the tsml, otherwise construct it.
   */
  protected def makeQuery: String = getProperty("sql") match {
    case Some(sql) => sql
    case None => {
      //build query
      val sb = new StringBuffer("select ")
      sb append makeHint //will include trailing space if not empty
      sb append makeProjectionClause
      sb append " from " + getTable

      val p = makePredicate
      if (p.nonEmpty) sb append " where " + p
      

      //Sort by domain variable.
      //assume domain is scalar, for now
      //Note 'dataset' should be the original before ops
      getOrigDataset match {
        case Dataset(f: Function) => f.getDomain match {
          case i: Index => //implicit placeholder, use natural order
          case v: Variable => v match {
            //Note, shouldn't matter if we sort on original name
            case _: Scalar => sb append " ORDER BY " + v.getName + " " + order
            case Tuple(vars) => {
              //assume all are scalars, reasonable for a domain variable
              val names = vars.map(_.getName).mkString(", ")
              sb append " ORDER BY " + names + " " + order
            }
          }
        }
        case _ => //no function so no domain variable to sort by
      }
      sb.toString
    }
  }

  /**
   * Build a list of constraints for the "where" clause.
   */
  protected def makePredicate: String = predicate
  private lazy val predicate: String = {
    //Get selection clauses (e.g. from requested operations)
    //Prepend any tsml defined predicate.
    val clauses = getProperty("predicate") match {
      case Some(s) => s +=: selections
      case None => selections
    }

    //insert "AND" between the clauses
    clauses.filter(_.nonEmpty).mkString(" AND ")
  }

  //---------------------------------------------------------------------------

  /**
   * The JDBC ResultSet from the query. This will lazily execute the query
   * when this result is requested.
   */
  protected lazy val resultSet: ResultSet = executeQuery
  private lazy val statement: Statement = connection.createStatement()
  //Keep database resources global so we can close them.

  /**
   * Allow subclasses to use the connection. They should not close it.
   */
  protected def getConnection: Connection = connection

  /*
   * Used so we don't end up getting the lazy connection when we are testing if we have one to close.
   */
  private var hasConnection = false

  /*
   * Database connection from JNDI or JDBC properties.
   */
  private lazy val connection: Connection = {
    
    val startsWithJavaRegex = "^(java:.+)".r
    val startsWithJdbcRegex = "^jdbc:(.+)".r
    
    // location is the current standard for both jndi and jdbc connections,
    // but we still support the jndi attribute for historical reasons.
    // See LATIS-30 for more details
    val con = getProperty("location") match {
      
      // if 'location' exists and starts with "java:", use jndi
      case Some(startsWithJavaRegex(location)) => getConnectionViaJndi(location)
      
      // if 'location' exists and starts with 'jdbc:'
      case Some(startsWithJdbcRegex(_)) => getConnectionViaJdbc
      
      // If we get here, we probably have a malformed tsml file. No conforming location attr was found.
      case _ => throw new RuntimeException(
        "Unable to find or parse tsml location attribute: location must exist and start with 'java:' (for JNDI) or 'jdbc:' (for JDBC)"
      )
    }

    hasConnection = true //will still be false if getting connection fails
    con
  }

  private def getConnectionViaJndi(jndiName: String): Connection = {
    val initCtx = new InitialContext()
    var ds: DataSource = null

    try {
      ds = initCtx.lookup(jndiName).asInstanceOf[DataSource]
    } catch {
      case e: NameNotFoundException => throw new RuntimeException("JdbcAdapter failed to locate JNDI resource: " + jndiName)
    }

    ds.getConnection()
  }

  private def getConnectionViaJdbc: Connection = {
    val driver = getProperty("driver") match {
      case Some(s) => s
      case None => throw new RuntimeException("JdbcAdapter needs to have a JDBC 'driver' defined.")
    }
    val url = getProperty("location") match {
      case Some(s) => s
      case None => throw new RuntimeException("JdbcAdapter needs to have a JDBC 'url' defined.")
    }
    val user = getProperty("user") match {
      case Some(s) => s
      case None => throw new RuntimeException("JdbcAdapter needs to have a 'user' defined.")
    }
    val passwd = getProperty("password") match {
      case Some(s) => s
      case None => throw new RuntimeException("JdbcAdapter needs to have a 'password' defined.")
    }

    //Load the JDBC driver 
    Class.forName(driver)

    //Make database connection
    DriverManager.getConnection(url, user, passwd)
  }

  /**
   * Release the database resources.
   */
  override def close(): Unit = {
    //TODO: http://stackoverflow.com/questions/4507440/must-jdbc-resultsets-and-statements-be-closed-separately-although-the-connection
    if (hasConnection) {
      try { resultSet.close } catch { case e: Exception => }
      try { statement.close } catch { case e: Exception => }
      try { connection.close } catch { case e: Exception => }
    }
  }
}

//=============================================================================  

/**
 * Define some inner classes to provide us with Record semantics for JDBC ResultSets.
 */
object JdbcAdapter {

  case class JdbcRecord(resultSet: ResultSet)

  class JdbcEmptyIterator() extends Iterator[JdbcAdapter.JdbcRecord] {
    private var _hasNext = false
    
    def next(): JdbcRecord = null
    def hasNext(): Boolean = _hasNext
  }

  class JdbcRecordIterator(resultSet: ResultSet) extends Iterator[JdbcAdapter.JdbcRecord] {
    private var _didNext = false
    private var _hasNext = false

    def next(): JdbcRecord = {
      if (!_didNext) resultSet.next
      _didNext = false
      JdbcRecord(resultSet)
    }

    def hasNext(): Boolean = {
      if (!_didNext) {
        _hasNext = resultSet.next
        _didNext = true
      }
      _hasNext
    }
  }
}
