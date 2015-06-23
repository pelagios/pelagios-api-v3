package models.geo

import scala.collection.JavaConverters._
import com.vividsolutions.jts.geom.Geometry
import com.vividsolutions.jts.algorithm.{ ConvexHull => JTSConvexHull }
import index.places.IndexedPlace
import java.io.StringWriter
import org.geotools.geojson.geom.GeometryJSON
import org.geotools.geometry.jts.JTSFactoryFinder
import play.api.libs.json.Json
import play.api.db.slick.Config.driver.simple._
import scala.collection.JavaConverters._
import com.vividsolutions.jts.geom.GeometryCollection
import org.geotools.geometry.jts.JTS

case class Hull(geometry: Geometry) {
  
  lazy val bounds: BoundingBox = {
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
    ConcaveHull.compute(geometries)
    
  def fromPlaces(places: Seq[IndexedPlace]): Option[Hull] =
    compute(places.flatMap(_.geometry))
  
}

private object ConvexHull {
  
  def compute(geometries: Seq[Geometry]): Option[Hull] = {
    if (geometries.size > 0) {
      val factory = JTSFactoryFinder.getGeometryFactory()
      val mergedGeometry = factory.buildGeometry(geometries.asJava).union
      val cvGeometry = new JTSConvexHull(mergedGeometry).getConvexHull()
      Some(Hull(cvGeometry))
    } else {
      None
    }
  }
    
}

private object ConcaveHull {
  
  private val THRESHOLD = 2.0
  
  def compute(geometries: Seq[Geometry]): Option[Hull] = {
    if (geometries.size > 0) {
      val factory = JTSFactoryFinder.getGeometryFactory()
      val geomCollection = new GeometryCollection(geometries.toArray, factory)
      val concaveHull = new org.opensphere.geometry.algorithm.ConcaveHull(geomCollection, THRESHOLD)
      Some(Hull(JTS.smooth(concaveHull.getConcaveHull(), 0.25)))
    } else {
      None
    }
  }
  
}