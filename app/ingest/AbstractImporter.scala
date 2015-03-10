package ingest

import global.Global
import index.places.IndexedPlace
import java.math.BigInteger
import java.security.MessageDigest
import models.Associations
import models.core._
import org.pelagios.Scalagios
import play.api.db.slick._
import play.api.Logger
import models.MasterHeatmap

/** One 'ingest record' **/
case class IngestRecord(
    
    /** The annotated thing **/
    thing: AnnotatedThing, 
    
    /** Images related to the annotated thing **/
    images: Seq[Image], 
    
    /** Annotations on the annotated thing **/
    annotations: Seq[Annotation],
    
    /** Places associated with the annotated thing, with place count **/
    places: Seq[(IndexedPlace, Int)])

abstract class AbstractImporter {
  
  private val SHA256 = "SHA-256"
    
  private val UTF8 = "UTF-8"
   
  protected def ingest(ingestBatch: Seq[IngestRecord], dataset: Dataset)(implicit s: Session) {
    // Insert data into DB
    val allThings = ingestBatch.map(_.thing)
    AnnotatedThings.insertAll(allThings)

    val allImages = ingestBatch.flatMap(_.images)
    Images.insertAll(allImages)

    val allAnnotations = ingestBatch.flatMap(_.annotations)
    Annotations.insertAll(allAnnotations)
            
    // Update aggregation table stats
    Associations.insert(ingestBatch.map(record => (record.thing, record.places)))
    
    // Update the parent dataset with new temporal profile and convex hull
    val affectedDatasets = Datasets.recomputeSpaceTimeBounds(dataset)
    
    // Update object index
    Logger.info("Updating Index") 
    val topLevelThings = ingestBatch.map(record => (record.thing, record.places.map(_._1))).filter(_._1.isPartOf.isEmpty)
    val datasetHierarchy = dataset +: Datasets.getParentHierarchyWithDatasets(dataset)
    Global.index.addAnnotatedThings(topLevelThings, datasetHierarchy)
    Global.index.updateDatasets(affectedDatasets)
    
    // Update suggestion index
    val titlesAndDescriptions = topLevelThings.flatMap(t => Seq(Some(t._1.title), t._1.description).flatten)
    Global.index.suggester.addTerms(titlesAndDescriptions.toSet)
    Global.index.refresh()
    
    // Update the master heatmap
    Logger.info("Updating master heatmap") 
    MasterHeatmap.rebuild()
  }
    
  /** Utility method that returns the RDF format corresponding to a particular file extension **/
  protected def getFormat(filename: String): String = filename match {
    case f if f.endsWith("rdf") => Scalagios.RDFXML
    case f if f.endsWith("ttl") => Scalagios.TURTLE
    case f if f.endsWith("n3") => Scalagios.N3
    case _ => throw new IllegalArgumentException("Format not supported")
  }
  
  /** Utility method that produces a SHA256 hash from a string **/
  protected def sha256(str: String): String = {
    val md = MessageDigest.getInstance(SHA256).digest(str.getBytes(UTF8))
    (new BigInteger(1, md)).toString(16)
  }

}