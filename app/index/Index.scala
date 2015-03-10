package index

import index.objects._
import index.places._
import java.io.File
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.facet.FacetsConfig
import org.apache.lucene.facet.taxonomy.{ TaxonomyWriter, SearcherTaxonomyManager }
import org.apache.lucene.facet.taxonomy.directory.{ DirectoryTaxonomyReader, DirectoryTaxonomyWriter }
import org.apache.lucene.index.{ DirectoryReader, IndexWriter, IndexWriterConfig, MultiReader }
import org.apache.lucene.search.{ IndexSearcher, SearcherFactory }
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.Version
import org.apache.lucene.search.spell.{ SpellChecker, LuceneDictionary }
import play.api.Logger
import com.spatial4j.core.context.jts.JtsSpatialContext
import org.apache.lucene.spatial.prefix.RecursivePrefixTreeStrategy
import org.apache.lucene.spatial.prefix.tree.GeohashPrefixTree
import org.apache.lucene.search.SearcherManager
import index.suggest.SuggestIndex

private[index] class IndexBase(placeIndexDir: File, objectIndexDir: File, taxonomyDir: File, spellcheckDir: File) {
  
  /** Spatial indexing settings **/
  protected val spatialCtx = JtsSpatialContext.GEO
  
  private val maxLevels = 11 
  
  protected val spatialStrategy =
    new RecursivePrefixTreeStrategy(new GeohashPrefixTree(spatialCtx, maxLevels), IndexFields.GEOMETRY)
  
  
  /** Object index facets **/
  protected val facetsConfig = new FacetsConfig()
  facetsConfig.setHierarchical(IndexFields.OBJECT_TYPE, false)
  facetsConfig.setHierarchical(IndexFields.ITEM_DATASET, true)

  
  /** Indices **/
  private val placeIndex = FSDirectory.open(placeIndexDir)
  
  private val objectIndex = FSDirectory.open(objectIndexDir)
  
  private val taxonomyIndex = FSDirectory.open(taxonomyDir)
  

  /** Index searcher managers **/
  protected val placeSearcherManager = new SearcherManager(placeIndex, new SearcherFactory())
  
  protected val objectSearcherManager = new SearcherTaxonomyManager(objectIndex, taxonomyIndex, new SearcherFactory())
  
  
  /** We're using our own 3-word phrase analyzer **/
  protected val analyzer = new NGramAnalyzer(3)
  
  
  /** Suggestion engine **/
  val suggester = new SuggestIndex(spellcheckDir, placeSearcherManager, objectSearcherManager, analyzer)  
  
  
  /** Index writers **/
  protected lazy val objectWriter: (IndexWriter, TaxonomyWriter) =
    (new IndexWriter(objectIndex, new IndexWriterConfig(Version.LATEST, analyzer)), new DirectoryTaxonomyWriter(taxonomyIndex))
    
  protected lazy val placeWriter: IndexWriter = 
    new IndexWriter(placeIndex, new IndexWriterConfig(Version.LATEST, analyzer))

  
  /** Returns the number of objects in the object index **/
  def numObjects: Int = {
    val objectSearcher = objectSearcherManager.acquire()
    val numObjects = objectSearcher.searcher.getIndexReader().numDocs()
    objectSearcherManager.release(objectSearcher)
    numObjects
  }
  
  /** Returns the number of places in the gazetteer index **/
  def numPlaceNetworks: Int = {
    val searcher = placeSearcherManager.acquire()
    try {
      searcher.getIndexReader().numDocs()
    } finally {
      placeSearcherManager.release(searcher)
    }
  }
  
  /** Commits all writes and refreshes the readers **/
  def refresh() = {
    Logger.info("Committing index writes and refreshing readers")
    
    objectWriter._1.commit()
    objectWriter._2.commit()
    objectSearcherManager.maybeRefresh()
    
    placeWriter.commit()
    placeSearcherManager.maybeRefresh()
  }
  
  /** Closes all indices **/
  def close() = {
    analyzer.close()
    
    objectWriter._1.close()
    objectWriter._2.close()
    placeWriter.close()
    
    objectSearcherManager.close()
    placeSearcherManager.close()
    
    objectIndex.close()
    taxonomyIndex.close()
    placeIndex.close()
    
    suggester.close()
  }
      
}

class Index private(placeIndexDir: File, objectIndexDir: File, taxonomyDir: File, spellcheckDir: File)
  extends IndexBase(placeIndexDir, objectIndexDir, taxonomyDir, spellcheckDir)
    with ObjectReader
    with ObjectWriter
    with PlaceReader
    with PlaceWriter
  
object Index {
  
  def open(indexDir: String): Index = {
    val baseDir = new File(indexDir)
      
    val placeIndexDir = createIfNotExists(new File(baseDir, "gazetteer"))
    val objectIndexDir = createIfNotExists(new File(baseDir, "objects"))
    
    val taxonomyDirectory = new File(baseDir, "taxonomy")
    if (!taxonomyDirectory.exists) {
      taxonomyDirectory.mkdirs()
      val taxonomyInitializer = new DirectoryTaxonomyWriter(FSDirectory.open(taxonomyDirectory))
      taxonomyInitializer.close()
    }
    
    val spellcheckIndexDir = createIfNotExists(new File(baseDir, "spellcheck"))
    
    new Index(placeIndexDir, objectIndexDir, taxonomyDirectory, spellcheckIndexDir)
  }
  
  private def createIfNotExists(dir: File): File = {
    if (!dir.exists) {
      dir.mkdirs()  
      val initConfig = new IndexWriterConfig(Version.LATEST, new StandardAnalyzer())
      val initializer = new IndexWriter(FSDirectory.open(dir), initConfig)
      initializer.close()      
    }
    
    dir  
  }
  
  def normalizeURI(uri: String) = {
    val noFragment = if (uri.indexOf('#') > -1) uri.substring(0, uri.indexOf('#')) else uri
    if (noFragment.endsWith("/"))
      noFragment.substring(0, noFragment.size - 1)
    else 
      noFragment
  }
  
}
