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
import de.sciss.synth.io.{AudioFile, Frames}
import util.control.NonFatal
import de.sciss.sonogram.ResourceManager.{Image, ImageSpec}
import de.sciss.model.impl.ModelImpl
import de.sciss.serial.{ImmutableSerializer, DataInput}
import de.sciss.filecache.{Consumer, Limit, Producer}
import scala.concurrent.Future

class OverviewManagerImpl(val caching: Option[OverviewManager.Caching])
  extends OverviewManager with ModelImpl[OverviewManager.Update] with ResourceManager {

  mgr =>

  import OverviewManager._
  import Overview.{Config => OvrSpec, Input => OvrIn, Output => OvrOut}

  //  /** Creates the file for the overview cache meta data. It should have a filename extension,
  //    * and the manager will store the overview binary data in a file with the same prefix but
  //    * different filename extension.
  //    *
  //    * @param path   the audio input file to derive the cache from
  //    */
  //  protected def createCacheFile(path: File): File

  /** This is a constant, but can be overridden by subclasses. */
  protected val decimation  = List(1, 6, 6, 6, 6)

  private var constQCache   = Map.empty[SonogramSpec, ConstQCache]
  private var imageCache    = Map.empty[(Int, Int), ImageCache]
  private var fileBufCache  = Map.empty[ImageSpec, FileBufCache]

  private var futures       = Map.empty[OvrSpec, Future[OvrOut]]
  private var overviews     = Map.empty[OvrSpec, Overview]
  private val sync          = new AnyRef

  private val producer = {
    val cc = Producer.Config[OvrSpec, OvrOut]()
    cc.extension  = "sono"
    cc.accept     = { (spec: OvrSpec, out: OvrOut) =>
      spec.file.lastModified() == out.input.lastModified && AudioFile.readSpec(spec.file) == out.input.fileSpec
    }
    cc.space      = { (spec: OvrSpec, out: OvrOut) => out.output.length() }
    cc.evict      = { (spec: OvrSpec, out: OvrOut) => out.output.delete() }

    caching match {
      case Some(c) =>
        cc.capacity = Limit(space = c.sizeLimit)
        cc.folder   = c.folder

      case _ =>
        cc.capacity = Limit(count = 0, space = 0L)
        val f = File.createTempFile(".cache", "")
        f.delete()
        f.mkdir()
        f.deleteOnExit()
        cc.folder   = f
    }
    Producer(cc)
  }

  //  private val consumer = Consumer(producer) { ovrSpec =>
  //    overviews
  //    ... : Future[OvrOut]
  //  }

  /**
   * Creates a new sonogram overview from a given audio file
   *
   * @param job  the settings for the analysis resolution. Note that `sampleRate` will be ignored as it is replaced
   *                by the file's sample rate. Also note that `maxFreq` will be clipped to nyquist.
   * @return
   */
  final def submit(job: Job): Overview = {
    val (ovrSpec, ovrIn) = jobToOverviewConfig(job)
    sync.synchronized {
      overviews.get(ovrSpec) match {
        case Some(ovr) =>
          // TODO: add use count
          ovr

        case _ =>
          val res    = new OverviewImpl(ovrSpec, ovrIn, manager = this, /* folder = folder, */ producer = producer)
          overviews += ovrSpec -> res
          import producer.executionContext
          // res.start()
          res
      }
    }
  }

  private def jobToOverviewConfig(job: Job): (Overview.Config, Overview.Input) = {
    val inSpec      = AudioFile.readSpec(job.file)
    val sampleRate  = inSpec.sampleRate
    val cq          = job.analysis
    val stepSize    = (cq.maxTimeRes/1000 * sampleRate + 0.5).toInt

    val sonogram    = SonogramSpec(
      sampleRate = sampleRate, minFreq = cq.minFreq,
      maxFreq = math.min(cq.maxFreq, sampleRate / 2).toFloat, bandsPerOct = cq.bandsPerOct,
      maxFFTSize = cq.maxFFTSize, stepSize = stepSize
    )

    (Overview.Config(job.file, sonogram, decimation), Overview.Input(inSpec, job.file.lastModified()))
  }

//  private def gagaismo(job: Job): Overview = {
//    val inF         = job.file
//    val in          = AudioFile.openRead(inF)
//    val inSpec      = in.spec
//    in.close()    // render loop will re-open it if necessary...
//    val sampleRate  = inSpec.sampleRate
//    val cq          = job.analysis
//    val stepSize    = (cq.maxTimeRes/1000 * sampleRate + 0.5).toInt
//
//    val sonogram    = SonogramSpec(
//      sampleRate = sampleRate, minFreq = cq.minFreq,
//      maxFreq = math.min(cq.maxFreq, sampleRate / 2).toFloat, bandsPerOct = cq.bandsPerOct,
//      maxFFTSize = cq.maxFFTSize, stepSize = stepSize)
//
//    val overCfg     = Overview.Config(file = inF, fileSpec = inSpec, sonogram = sonogram,
//      lastModified  = inF.lastModified, decimation = decimation)
//
//    val existing    = caching.flatMap { c =>
//      val cacheMetaF    = ... : java.io.File // createCacheFile(inF)
//      val cacheFolder   = cacheMetaF.getParentFile
//      val cachePrefix   = cacheMetaF.getName
//      val cacheAudioF   = new File(cacheFolder, cachePrefix + ".aif" )
//
//      var overviewO     = Option.empty[Overview]
//      if (cacheMetaF.isFile && cacheAudioF.isFile) {
//        try {
//          val metaIn = new FileInputStream(cacheMetaF)
//          try {
//            val arr = new Array[Byte](metaIn.available())
//            metaIn.read(arr)
//            val cacheConfig = Overview.Config.Serializer.read(DataInput(arr))
//            if (cacheConfig == overCfg) {
//              val cacheAudio = AudioFile.openRead(cacheAudioF)
//              //            // this is wrong: overCfg.fileSpec is audio input spec not decim spec
//              //            if (cacheAudio.spec == overCfg.fileSpec) {
//              val overview = Overview.openRead(overCfg, cacheAudio, this)
//                overviewO = Some(overview)
//              //            }
//            }
//          } finally {
//            metaIn.close()
//          }
//        } catch {
//          case NonFatal(_) =>
//        }
//      }
//      overviewO
//    }
//
//    existing.getOrElse {
//      //      val d           = AudioFileSpec(AudioFileType.AIFF, SampleFormat.Float, inSpec.numChannels, sampleRate)
//      //      val cacheAudio  = AudioFile.openWrite(cacheAudioF, d) // ...eventually should use shared buffer!!
//      Overview.openWrite(overCfg, /* cacheAudio, */ this)
//    }
//  }

  def dispose() {
    sync.synchronized {
      releaseListeners()
      overviews.foreach(_._2.abort())
    }
  }

  final def allocateConstQ(spec: SonogramSpec): ConstQ = {
    sync.synchronized {
      val entry = constQCache.get(spec) getOrElse
        new ConstQCache(constQFromSpec(spec))
      entry.useCount += 1
      constQCache    += (spec -> entry) // in case it was newly created
      entry.constQ
    }
  }

  final def releaseConstQ(spec: SonogramSpec) {
    sync.synchronized {
      val entry = constQCache(spec) // let it throw an exception if not contained
      entry.useCount -= 1
      if (entry.useCount == 0) {
        constQCache -= spec
      }
    }
  }

  final def allocateImage(spec: ImageSpec): Image = {
    sync.synchronized {
      val img     = allocateImage(spec.width, spec.height)
      val fileBuf = allocateFileBuf(spec)
      new Image(img, fileBuf)
    }
  }

  final def releaseImage(spec: ImageSpec) {
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

  private final class ConstQCache(val constQ: ConstQ) {
    var useCount: Int = 0
  }

  private final class FileBufCache(val buf: Frames) {
    var useCount: Int = 0
  }

  private final class ImageCache(val img: BufferedImage) {
    var useCount: Int = 0
  }
}