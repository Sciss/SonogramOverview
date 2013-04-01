/*
 *  OverviewManagerImpl.scala
 *  (Overview)
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
package impl

import java.awt.image.BufferedImage
import java.io.{FileInputStream, File}
import de.sciss.dsp.ConstQ
import de.sciss.synth.io.AudioFile
import util.control.NonFatal
import de.sciss.lucre.io.DataInput
import de.sciss.sonogram.ResourceManager.{Image, ImageSpec}

abstract class OverviewManagerImpl extends OverviewManager {
  mgr =>

  import OverviewManager._

//  // ---- subclass must define these abstract methods ----
//  def appCode: String

  // APPCODE  = "Ttm "
  //   def fileCache: CacheManager
  protected def createCachePrefix(path: File): File

  private var constQCache   = Map[SonogramSpec, ConstQCache]()
  private var imageCache    = Map[(Int, Int), ImageCache]()
  private var fileBufCache  = Map[ImageSpec, FileBufCache]()
  private val sync          = new AnyRef

//  sampleRate: Double, minFreq: Float, maxFreq: Float,
//  bandsPerOct: Int, maxTimeRes: Float, maxFFTSize: Int, stepSize: Int

  /**
   * Creates a new sonogram overview from a given audio file
   *
   * @param config  the settings for the analysis resolution. Note that `sampleRate` will be ignored as it is replaced
   *                by the file's sample rate. Also note that `maxFreq` will be clipped to nyquist.
   * @return
   */
  protected def prepare(config: Config): Overview = {
    val inF         = config.file
    val in          = AudioFile.openRead(inF)
    val inSpec      = in.spec
    in.close()    // render loop will re-open it if necessary...
    val sampleRate  = inSpec.sampleRate
    val cq          = config.analysis
    val stepSize    = (cq.maxTimeRes/1000 * sampleRate + 0.5).toInt

    val sonogram    = SonogramSpec(
      sampleRate = sampleRate, minFreq = cq.minFreq,
      maxFreq = math.min(cq.maxFreq, sampleRate / 2).toFloat, bandsPerOct = cq.bandsPerOct,
      maxFFTSize = 4096, stepSize = stepSize)

    val decim       = List(1, 6, 6, 6, 6)
    val overCfg     = Overview.Config(file = inF, fileSpec = inSpec, sonogram = sonogram,
      lastModified = inF.lastModified, decimation = decim)
    // val cachePath     = fileCache.createCacheFileName(path)
    val cacheF        = createCachePrefix(inF)
    val cacheFolder   = cacheF.getParentFile
    val cachePrefix   = cacheF.getName
    val cacheMetaF    = new File(cacheFolder, cachePrefix + ".sono")
    val cacheAudioF   = new File(cacheFolder, cachePrefix + ".aif" )

    var overviewO     = Option.empty[Overview]
    if (cacheMetaF.isFile && cacheAudioF.isFile) {
      try {
        val metaIn = new FileInputStream(cacheMetaF)
        try {
          val arr = new Array[Byte](metaIn.available())
          metaIn.read(arr)
          val cacheConfig = Overview.Config.Serializer.read(DataInput(arr))
          if (cacheConfig == overCfg) {
            val cacheAudio = AudioFile.openRead(cacheAudioF)
            ??? // this is wrong: overCfg.fileSpec is audio input spec not decim spec
            if (cacheAudio.spec == overCfg.fileSpec) {
              val overview = Overview.openRead(overCfg, cacheAudio, this)
              overviewO = Some(overview)
            }
          }
        } finally {
          metaIn.close()
        }
      } catch {
        case NonFatal(_) =>
      }
    }

    overviewO.getOrElse {
//      val d           = AudioFileSpec(AudioFileType.AIFF, SampleFormat.Float, inSpec.numChannels, sampleRate)
//      val cacheAudio  = AudioFile.openWrite(cacheAudioF, d) // XXX TODO: eventually should use shared buffer!!
      Overview.openWrite(overCfg, /* cacheAudio, */ this)
    }
  }

  def allocateConstQ(spec: SonogramSpec): ConstQ = {
    sync.synchronized {
      val entry = constQCache.get(spec) getOrElse
        new ConstQCache(constQFromSpec(spec))
      entry.useCount += 1
      constQCache    += (spec -> entry) // in case it was newly created
      entry.constQ
    }
  }

  def releaseConstQ(spec: SonogramSpec) {
    sync.synchronized {
      val entry = constQCache(spec) // let it throw an exception if not contained
      entry.useCount -= 1
      if (entry.useCount == 0) {
        constQCache -= spec
      }
    }
  }

  def allocateImage(spec: ImageSpec): Image = {
    sync.synchronized {
      val img     = allocateImage(spec.width, spec.height)
      val fileBuf = allocateFileBuf(spec)
      new Image(img, fileBuf)
    }
  }

  def releaseImage(spec: ImageSpec) {
    sync.synchronized {
      releaseImage(spec.width, spec.height)
      releaseFileBuf(spec)
    }
  }

  private def allocateImage(width: Int, height: Int): BufferedImage = {
    sync.synchronized {
      val entry = imageCache.get((width, height)) getOrElse
        new ImageCache(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB))
      entry.useCount += 1
      imageCache += ((width, height) -> entry) // in case it was newly created
      entry.img
    }
  }

  private def allocateFileBuf(spec: ImageSpec): Array[Array[Float]] = {
    sync.synchronized {
      val entry = fileBufCache.get(spec) getOrElse
        new FileBufCache(Array.ofDim[Float](spec.numChannels, spec.width * spec.height))
      entry.useCount += 1
      fileBufCache += (spec -> entry) // in case it was newly created
      entry.buf
    }
  }

  private def releaseImage(width: Int, height: Int) {
    sync.synchronized {
      val key         = (width, height)
      val entry       = imageCache(key) // let it throw an exception if not contained
      entry.useCount -= 1
      if (entry.useCount == 0) {
        imageCache -= key
      }
    }
  }

  private def releaseFileBuf(spec: ImageSpec) {
    sync.synchronized {
      val entry = fileBufCache(spec) // let it throw an exception if not contained
      entry.useCount -= 1
      if (entry.useCount == 0) {
        fileBufCache -= spec
      }
    }
  }

  private def constQFromSpec(spec: SonogramSpec): ConstQ = {
    val cfg = ConstQ.Config()
    cfg.sampleRate  = spec.sampleRate
    cfg.minFreq     = spec.minFreq
    cfg.maxFreq     = spec.maxFreq
    cfg.bandsPerOct = spec.bandsPerOct
    val maxTimeRes  = spec.stepSize / spec.sampleRate * 1000
//    cfg.maxTimeRes  = spec.maxTimeRes
    cfg.maxTimeRes  = maxTimeRes.toFloat  // note: this is a purely informative field
    cfg.maxFFTSize  = spec.maxFFTSize
    ConstQ(cfg)
  }

//  private var workerQueue   = IQueue[WorkingSonogram]()
//  private var runningWorker = Option.empty[WorkingSonogram]
//
//  private def queue(sono: Overview) {
//    sync.synchronized {
//      workerQueue = workerQueue.enqueue(new WorkingSonogram(sono))
//      checkRun()
//    }
//  }
//
//  private def dequeue(ws: WorkingSonogram) {
//    sync.synchronized {
//      val (s, q) = workerQueue.dequeue
//      workerQueue = q
//      assert(ws == s)
//      checkRun()
//    }
//  }
//
//  private def checkRun() {
//    sync.synchronized {
//      if (runningWorker.isEmpty) {
//        workerQueue.headOption.foreach { next =>
//          runningWorker = Some(next)
//          next.addPropertyChangeListener(new PropertyChangeListener {
//            def propertyChange(e: PropertyChangeEvent) {
//              if (Overview.verbose) println("WorkingSonogram got in : " + e.getPropertyName + " / " + e.getNewValue)
//              if (e.getNewValue == SwingWorker.StateValue.DONE) {
//                runningWorker = None
//                dequeue(next)
//              }
//            }
//          })
//          next.execute()
//        }
//      }
//    }
//  }

  private final class ConstQCache(val constQ: ConstQ) {
    var useCount: Int = 0
  }

  private final class FileBufCache(val buf: Array[Array[Float]]) {
    var useCount: Int = 0
  }

  private final class ImageCache(val img: BufferedImage) {
    var useCount: Int = 0
  }
}