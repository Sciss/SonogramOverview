/*
 *  SonogramOverviewManager.scala
 *  (SonogramOverview)
 *
 *  Copyright (c) 2010-2013 Hanns Holger Rutz. All rights reserved.
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

import java.awt.image.BufferedImage
import java.beans.{PropertyChangeEvent, PropertyChangeListener}
import java.io.{File, IOException}
import javax.swing.SwingWorker
import collection.immutable.{Queue => IQueue}
import math._
import de.sciss.dsp.ConstQ
import de.sciss.synth.io.{SampleFormat, AudioFileType, AudioFileSpec, AudioFile}

abstract class SonogramOverviewManager {
   mgr =>

   import SonogramOverview._

   // ---- subclass must define these abstract methods ----
   def appCode: String // APPCODE  = "Ttm "
//   def fileCache: CacheManager
   protected def createCacheFileName( path: File ) : File

  private var constQCache   = Map[SonogramSpec, ConstQCache]()
  private var imageCache    = Map[(Int, Int), ImageCache]()
  private var fileBufCache  = Map[SonogramImageSpec, FileBufCache]()
  private val sync          = new AnyRef

  @throws(classOf[IOException])
  def fromPath(path: File): SonogramOverview = {
    sync.synchronized {
      val af          = AudioFile.openRead(path)
      val afDescr     = af.spec
      af.close()    // render loop will re-open it if necessary...
      val sampleRate  = afDescr.sampleRate
      val stepSize    = max(64, (sampleRate * 0.0116 + 0.5).toInt) // 11.6ms spacing
      val sonoSpec    = SonogramSpec(sampleRate, 32, min(16384, sampleRate / 2).toFloat, 24,
          (stepSize / sampleRate * 1000).toFloat, 4096, stepSize)
      val decim       = List(1, 6, 6, 6, 6)
      val fileSpec    = new SonogramFileSpec(sonoSpec, path.lastModified, path,
        afDescr.numFrames, afDescr.numChannels, sampleRate, decim)
      // val cachePath     = fileCache.createCacheFileName( path )
      val cachePath = createCacheFileName(path)

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
        val d = AudioFileSpec(AudioFileType.AIFF, SampleFormat.Float, afDescr.numChannels, afDescr.sampleRate)
        AudioFile.openWrite(cachePath, d) // XXX eventually should use shared buffer!!
      }

      val so = new SonogramOverview(mgr, fileSpec, decimAF)
      // render overview if necessary
      if (decimAFO.isEmpty) queue(so)
      so
    }
  }

  private[sonogram] def allocateConstQ(spec: SonogramSpec): ConstQ = {
    sync.synchronized {
      val entry = constQCache.get(spec) getOrElse
        new ConstQCache(constQFromSpec(spec))
      entry.useCount += 1
      constQCache    += (spec -> entry) // in case it was newly created
      entry.constQ
    }
  }

  private[sonogram] def allocateSonoImage(spec: SonogramImageSpec): SonogramImage = {
    sync.synchronized {
      val img     = allocateImage(spec.width, spec.height)
      val fileBuf = allocateFileBuf(spec)
      new SonogramImage(img, fileBuf)
    }
  }

  private[sonogram] def releaseSonoImage(spec: SonogramImageSpec) {
    sync.synchronized {
      releaseImage(spec.width, spec.height)
      releaseFileBuf(spec)
    }
  }

  private[this] def allocateImage(width: Int, height: Int): BufferedImage = {
    sync.synchronized {
      val entry = imageCache.get((width, height)) getOrElse
        new ImageCache(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB))
      entry.useCount += 1
      imageCache += ((width, height) -> entry) // in case it was newly created
      entry.img
    }
  }

  private[this] def allocateFileBuf(spec: SonogramImageSpec): Array[Array[Float]] = {
    sync.synchronized {
      val entry = fileBufCache.get(spec) getOrElse
        new FileBufCache(Array.ofDim[Float](spec.numChannels, spec.width * spec.height))
      entry.useCount += 1
      fileBufCache += (spec -> entry) // in case it was newly created
      entry.buf
    }
  }

  private[sonogram] def releaseConstQ(spec: SonogramSpec) {
    sync.synchronized {
      val entry = constQCache(spec) // let it throw an exception if not contained
      entry.useCount -= 1
      if (entry.useCount == 0) {
        constQCache -= spec
      }
    }
  }

  private[this] def releaseImage(width: Int, height: Int) {
    sync.synchronized {
      val key         = (width, height)
      val entry       = imageCache(key) // let it throw an exception if not contained
      entry.useCount -= 1
      if (entry.useCount == 0) {
        imageCache -= key
      }
    }
  }

  private[this] def releaseFileBuf(spec: SonogramImageSpec) {
    sync.synchronized {
      val entry = fileBufCache(spec) // let it throw an exception if not contained
      entry.useCount -= 1
      if (entry.useCount == 0) {
        fileBufCache -= spec
      }
    }
  }

  private[this] def constQFromSpec(spec: SonogramSpec): ConstQ = {
    val cfg = ConstQ.Config()
    cfg.sampleRate = spec.sampleRate
    cfg.minFreq = spec.minFreq
    cfg.maxFreq = spec.maxFreq
    cfg.bandsPerOct = spec.bandsPerOct
    cfg.maxTimeRes = spec.maxTimeRes
    cfg.maxFFTSize = spec.maxFFTSize
    ConstQ(cfg)
  }

  private[this] var workerQueue   = IQueue[WorkingSonogram]()
  private[this] var runningWorker = Option.empty[WorkingSonogram]

  private def queue(sono: SonogramOverview) {
    sync.synchronized {
      workerQueue = workerQueue.enqueue(new WorkingSonogram(sono))
      checkRun()
    }
  }

  private[this] def dequeue(ws: WorkingSonogram) {
    sync.synchronized {
      val (s, q) = workerQueue.dequeue
      workerQueue = q
      assert(ws == s)
      checkRun()
    }
  }

  private[this] def checkRun() {
    sync.synchronized {
      if (runningWorker.isEmpty) {
        workerQueue.headOption.foreach { next =>
          runningWorker = Some(next)
          next.addPropertyChangeListener(new PropertyChangeListener {
            def propertyChange(e: PropertyChangeEvent) {
              if (verbose) println("WorkingSonogram got in : " + e.getPropertyName + " / " + e.getNewValue)
              if (e.getNewValue == SwingWorker.StateValue.DONE) {
                runningWorker = None
                dequeue(next)
              }
            }
          })
          next.execute()
        }
      }
    }
  }

  private[this] final class ConstQCache(val constQ: ConstQ) {
    var useCount: Int = 0
  }

  private[this] final class FileBufCache(val buf: Array[Array[Float]]) {
    var useCount: Int = 0
  }

  private[this] final class ImageCache(val img: BufferedImage) {
    var useCount: Int = 0
  }

}