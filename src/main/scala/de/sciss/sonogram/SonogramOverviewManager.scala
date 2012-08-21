/*
 *  SonogramOverviewManager.scala
 *  (SonogramOverview)
 *
 *  Copyright (c) 2010-2012 Hanns Holger Rutz. All rights reserved.
 *
 *	This software is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU General Public License
 *	as published by the Free Software Foundation; either
 *	version 2, june 1991 of the License, or (at your option) any later version.
 *
 *	This software is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *	General Public License for more details.
 *
 *	You should have received a copy of the GNU General Public
 *	License (gpl.txt) along with this software; if not, write to the Free Software
 *	Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 *    28-Mar-10   extracted from Kontur. Removing app package dependancy
 */

package de.sciss.sonogram

import java.awt.{ Dimension }
import java.awt.image.{ BufferedImage }
import java.beans.{ PropertyChangeEvent, PropertyChangeListener }
import java.io.{ File, IOException }
import javax.swing.{ SwingWorker }
import collection.immutable.{ Queue }
import math._
import de.sciss.dsp.{ ConstQ }
import de.sciss.synth.io.{SampleFormat, AudioFileType, AudioFileSpec, AudioFile}

/**
 *    @version 0.15  07-Oct-10
 */
abstract class SonogramOverviewManager {
   mgr =>

   import SonogramOverview._

   // ---- subclass must define these abstract methods ----
   def appCode: String // APPCODE  = "Ttm "
//   def fileCache: CacheManager
   protected def createCacheFileName( path: File ) : File

   private var constQCache    = Map[ SonogramSpec, ConstQCache ]()
   private var imageCache     = Map[ Dimension, ImageCache ]()
   private var fileBufCache   = Map[ SonogramImageSpec, FileBufCache ]()
   private val sync           = new AnyRef
//   private lazy val fileCache = {
//      val app = AbstractApplication.getApplication
//      new PrefCacheManager(
//         app.getUserPrefs.node( PrefsUtil.NODE_IO ).node( PrefsUtil.NODE_SONACACHE ),
//         true, new File( System.getProperty( "java.io.tmpdir" ), app.getName ), 10240 ) // XXX 10 GB
//   }

   @throws( classOf[ IOException ])
   def fromPath( path: File ) : SonogramOverview = {
      sync.synchronized {
         val af            = AudioFile.openRead( path )
         val afDescr       = af.spec
         af.close // render loop will re-open it if necessary...
         val sampleRate    = afDescr.sampleRate
         val stepSize      = max( 64, (sampleRate * 0.0116 + 0.5).toInt ) // 11.6ms spacing
         val sonoSpec      = SonogramSpec( sampleRate, 32, min( 16384, sampleRate / 2 ).toFloat, 24,
                                (stepSize / sampleRate * 1000).toFloat, 4096, stepSize )
         val decim         = List( 1, 6, 6, 6, 6 )
         val fileSpec      = new SonogramFileSpec( sonoSpec, path.lastModified, path,
                             afDescr.numFrames, afDescr.numChannels, sampleRate, decim )
//         val cachePath     = fileCache.createCacheFileName( path )
         val cachePath     = createCacheFileName( path )

         // try to retrieve existing overview file from cache
//         val decimAFO      = if( cachePath.isFile ) {
//            try {
//               val cacheAF    = AudioFile.openRead( cachePath )
//               try {
//                  cacheAF.readAppCode()
//                  val cacheDescr = cacheAF.spec
//                  val blob       = cacheDescr.getProperty( AudioFileDescr.KEY_APPCODE ).asInstanceOf[ Array[ Byte ]]
//                  if( (cacheDescr.appCode == appCode) && (blob != null) && (SonogramFileSpec.decode( blob ) == Some( fileSpec ))
//                      && (cacheDescr.length == fileSpec.expectedDecimNumFrames) ) {
//                     af.cleanUp // do not need it anymore for reading
//                     fileSpec.makeAllAvailable
//                     Some( cacheAF )
//                  } else {
//                     cacheAF.cleanUp
//                     None
//                  }
//               }
//               catch { case e: IOException => { cacheAF.cleanUp; None }}
//            }
//            catch { case e: IOException => { None }}
//         } else None
val decimAFO = None

         // on failure, create new cache file
         val decimAF = decimAFO getOrElse {
//            val d             = new AudioFileDescr()
//            d.file            = cachePath
//            d.`type`          = AudioFileDescr.TYPE_AIFF
//            d.channels        = afDescr.channels
//            d.rate            = afDescr.rate
//            d.bitsPerSample   = 32  // XXX really?
//            d.sampleFormat    = AudioFileDescr.FORMAT_FLOAT
//            d.appCode         = appCode
//            d.setProperty( AudioFileDescr.KEY_APPCODE, fileSpec.encode )
            val d = AudioFileSpec( AudioFileType.AIFF, SampleFormat.Float, afDescr.numChannels, afDescr.sampleRate )
            AudioFile.openWrite( cachePath, d ) // XXX eventually should use shared buffer!!
         }

         val so = new SonogramOverview( mgr, fileSpec, decimAF )
         // render overview if necessary
         if( decimAFO.isEmpty ) queue( so )
         so
      }
   }

   private[ sonogram ] def allocateConstQ( spec: SonogramSpec /*, createKernels: Boolean = true */ ) : ConstQ = {
      sync.synchronized {
         val entry = constQCache.get( spec ) getOrElse
            new ConstQCache( constQFromSpec( spec /*, createKernels */))
         entry.useCount += 1
         constQCache += (spec -> entry)  // in case it was newly created
         entry.constQ
      }
   }

   private[ sonogram ] def allocateSonoImage( spec: SonogramImageSpec ) : SonogramImage = {
      sync.synchronized {
         val img     = allocateImage( spec.dim )
         val fileBuf = allocateFileBuf( spec )
         SonogramImage( img, fileBuf )
      }
   }

   private[ sonogram ] def releaseSonoImage( spec: SonogramImageSpec ) {
      sync.synchronized {
         releaseImage( spec.dim )
         releaseFileBuf( spec )
      }
   }

   private[this] def allocateImage( dim: Dimension ) : BufferedImage = {
      sync.synchronized {
         val entry = imageCache.get( dim ) getOrElse
            new ImageCache( new BufferedImage( dim.width, dim.height, BufferedImage.TYPE_INT_RGB ))
         entry.useCount += 1
         imageCache += (dim -> entry)  // in case it was newly created
         entry.img
      }
   }

   private[this] def allocateFileBuf( spec: SonogramImageSpec ) : Array[ Array[ Float ]] = {
      sync.synchronized {
         val entry = fileBufCache.get( spec ) getOrElse
            new FileBufCache( Array.ofDim[ Float ]( spec.numChannels, spec.dim.width * spec.dim.height ))
         entry.useCount += 1
         fileBufCache += (spec -> entry)  // in case it was newly created
         entry.buf
      }
   }

//   private def releaseConstQ( constQ: ConstQ ) : Unit =
//      releaseConstQ( specFromConstQ( constQ ))

   private[ sonogram ] def releaseConstQ( spec: SonogramSpec ) {
      sync.synchronized {
         val entry   = constQCache( spec ) // let it throw an exception if not contained
         entry.useCount -= 1
         if( entry.useCount == 0 ) {
            constQCache -= spec
         }
      }
   }

   private[this] def releaseImage( dim: Dimension ) {
      sync.synchronized {
         val entry = imageCache( dim ) // let it throw an exception if not contained
         entry.useCount -= 1
         if( entry.useCount == 0 ) {
            imageCache -= dim
         }
      }
   }

   private[this] def releaseFileBuf( spec: SonogramImageSpec ) {
      sync.synchronized {
         val entry = fileBufCache( spec ) // let it throw an exception if not contained
         entry.useCount -= 1
         if( entry.useCount == 0 ) {
            fileBufCache -= spec
         }
      }
   }

//   private def specFromConstQ( constQ: ConstQ ) =
//      SonogramSpec( constQ.getSampleRate, constQ.getMinFreq, constQ.getMaxFreq,
//                    constQ.getBandsPerOct, constQ.getMaxTimeRes,
//                    constQ.getMaxFFTSize )

   private[this] def constQFromSpec( spec: SonogramSpec /*, createKernels: Boolean */) : ConstQ = {
   	val cfg  = ConstQ.Config()
      cfg.sampleRate = spec.sampleRate
      cfg.minFreq    = spec.minFreq
      cfg.maxFreq    = spec.maxFreq
      cfg.bandsPerOct= spec.bandsPerOct
      cfg.maxTimeRes = spec.maxTimeRes
      cfg.maxFFTSize = spec.maxFFTSize
//		println( "Creating ConstQ Kernels..." )
//		if( createKernels ) constQ.createKernels()
      ConstQ( cfg )
   }

   private[this] var workerQueue = Queue[ WorkingSonogram ]()
   private[this] var runningWorker: Option[ WorkingSonogram ] = None

   private def queue( sono: SonogramOverview ) {
      sync.synchronized {
         workerQueue = workerQueue.enqueue( new WorkingSonogram( sono ))
         checkRun
      }
   }

   private[this] def dequeue( ws: WorkingSonogram ) {
      sync.synchronized {
         val (s, q) = workerQueue.dequeue
         workerQueue = q
         assert( ws == s )
         checkRun
      }
   }

   private[this] def checkRun {
      sync.synchronized {
         if( runningWorker.isEmpty ) {
            workerQueue.headOption.foreach( next => {
               runningWorker = Some( next )
               next.addPropertyChangeListener( new PropertyChangeListener {
                  def propertyChange( e: PropertyChangeEvent ) {
if( verbose ) println( "WorkingSonogram got in : " + e.getPropertyName + " / " + e.getNewValue )
                     if( e.getNewValue == SwingWorker.StateValue.DONE ) {
                        runningWorker = None
                        dequeue( next )
                     }
                  }
               })
               next.execute()
            })
         }
      }
   }

   private[this] class ConstQCache( val constQ: ConstQ ) {
      var useCount: Int = 0
   }

   private[this] class FileBufCache( val buf: Array[ Array[ Float ]]) {
      var useCount: Int = 0
   }

   private[this] class ImageCache( val img: BufferedImage ) {
      var useCount: Int = 0
   }
}