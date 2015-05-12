package index

import index.objects.IndexedObjectTypes
import models.geo.BoundingBox
import com.vividsolutions.jts.geom.Coordinate

/** A wrapper around a full complement of search arguments **/
case class SearchParameters(
    
  /** Keyword/ phrase query **/
  query:             Option[String],
  
  /** Object type restriction **/
  objectType:        Option[IndexedObjectTypes.Value],
  
  /** Dataset filter **/
  datasets:          Seq[String],
  
  /** Alternative dataset filter, which excludes specific sets **/
  excludeDatasets:   Seq[String],
  
  /** Gazetteer filter **/
  gazetteers:        Seq[String],
  
  /** Alternative gazetteer filter, which excludes specific gazetteers **/
  excludeGazetteers: Seq[String],
  
  /** Date filter (start year) **/
  from:              Option[Int],
  
  /** Date filter (end year) **/
  to:                Option[Int],
  
  /** Restriction to specific place **/  
  places:            Seq[String],
  
  /** Geo search filter: objects overlapping bounding box **/
  bbox:              Option[BoundingBox],
  
  /** Geo search filter: objects around a coordinate **/
  coord:             Option[Coordinate],
  
  /** Geo search filter: radius around coordinate **/
  radius:            Option[Double],
  
  /** Pagination limit (i.e. max. number of items returned **/
  limit:             Int,
  
  /** Pagination offset (i.e. number of items discarded **/
  offset:            Int) {
    
    /** Query is valid if at least one param is set **/
    def isValid: Boolean =
      Seq(query, objectType, from, to, bbox, coord, radius).filter(_.isDefined).size > 0 ||
      (places ++ datasets ++ excludeDatasets ++ gazetteers ++ excludeGazetteers).size > 0

}