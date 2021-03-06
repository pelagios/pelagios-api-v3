package models.geo

import com.vividsolutions.jts.geom.{ Geometry, GeometryCollection, GeometryFactory }
import com.vividsolutions.jts.algorithm.{ ConvexHull => JTSConvexHull }
import index.places.IndexedPlaceNetwork
import java.io.StringWriter
import org.geotools.geojson.geom.GeometryJSON
import org.geotools.geometry.jts.{ JTS, JTSFactoryFinder }
import play.api.Logger
import play.api.libs.json.Json
import play.api.db.slick.Config.driver.simple._
import scala.collection.JavaConverters._
import com.vividsolutions.jts.simplify.TopologyPreservingSimplifier

case class Hull(geometry: Geometry) {
  
  val bounds: BoundingBox = {
    val envelope = geometry.getEnvelopeInternal()
    BoundingBox(envelope.getMinX, envelope.getMaxX, envelope.getMinY, envelope.getMaxY)
  }
  
  lazy val asGeoJSON =
    Json.parse(toString)
  
  override lazy val toString = {
    val writer = new StringWriter()
    new GeometryJSON().write(geometry, writer)
    writer.toString
  }
  
}

object Hull {
  
  /** DB mapper function **/
  implicit val hullMapper = MappedColumnType.base[Hull, String](
    { hull => hull.toString },
    { hull => Hull.fromGeoJSON(hull) })
    
  def fromGeoJSON(json: String): Hull =
    Hull(new GeometryJSON().read(json.trim))
    
  /** Shortcut to the preferred hull type **/
  def compute(geometries: Seq[Geometry]): Option[Hull] =
    try {
      // Implementation not 100% stable - fall back to convex hull in case of problems
      ConcaveHull.compute(geometries)    
    } catch {
      case t: Throwable => {
        Logger.info("Falling back to convex hull for geometry " + geometries.toString)
        ConvexHull.compute(geometries) 
      }
    }
    
  def fromPlaces(places: Seq[IndexedPlaceNetwork]): Option[Hull] =
    compute(places.flatMap(_.geometry))
  
}

private object ConvexHull {
  
  def compute(geometries: Seq[Geometry]): Option[Hull] = {
    // Make sure convex hull does not throw exceptions - we're using it as fallback in case ConcaveHull breaks
    try {
      if (geometries.size > 0) {
        val factory = JTSFactoryFinder.getGeometryFactory()
        val mergedGeometry = factory.buildGeometry(geometries.asJava).union
        val cvGeometry = new JTSConvexHull(mergedGeometry).getConvexHull()
        Some(Hull(cvGeometry))
      } else {
        None
      }
    } catch {
      case t: Throwable => {
        Logger.warn(t.getMessage)
        None
      }
    }
  }
    
}

private object ConcaveHull {
  
  private val THRESHOLD = 2.0
  
  private val POLYGON_SIMPLIFICATION_TOLERANCE = 1.0
  
  private val SMOOTHING = 0.8 
  
  private val factory = new GeometryFactory
  
  def compute(geometries: Seq[Geometry]): Option[Hull] = {
    if (geometries.size > 0) {
      val union = new GeometryCollection(geometries.toArray, factory).buffer(0)
      val smoothed = JTS.smooth(union, SMOOTHING)
      val simplified = TopologyPreservingSimplifier.simplify(smoothed, POLYGON_SIMPLIFICATION_TOLERANCE)   
      Some(Hull(simplified))
    } else {
      None
    }
  }
  
}