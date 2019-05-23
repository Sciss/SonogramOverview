/*
 *  OverviewManagerImpl.scala
 *  (Overview)
 *
 *  Copyright (c) 2010-2019 Hanns Holger Rutz. All rights reserved.
 *
 *	This software is published under the GNU Lesser General Public License v2.1+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.sonogram
package impl

import java.awt.image.BufferedImage
import java.io.File

import de.sciss.dsp.ConstQ
import de.sciss.filecache
import de.sciss.model.impl.ModelImpl
import de.sciss.sonogram.ResourceManager.{Image, ImageSpec}
import de.sciss.synth.io.{AudioFile, Frames}

private object OverviewManagerImpl {
//  private final class ConstQCache(val constQ: ConstQ) {
//    var useCount = 0
//  }

  private final class FileBufCache(val buf: Frames) {
    var useCount = 0
  }

  private final class ImageCache(val img: BufferedImage) {
    var useCount = 0
  }

  private final class OverviewCache(val overview: OverviewImpl) {
    var useCount = 0
  }
}
private[sonogram] final class OverviewManagerImpl(val config: OverviewManager.Config)
  extends OverviewManager with ModelImpl[OverviewManager.Update] with ResourceManager {

  import Overview.{Config => OvrSpec, Output => OvrOut}
  import OverviewImpl.debug
  import OverviewManager._
  import OverviewManagerImpl._

  //  /** Creates the file for the overview cache meta data. It should have a filename extension,
  //    * and the manager will store the overview binary data in a file with the same prefix but
  //    * different filename extension.
  //    *
  //    * @param path   the audio input file to derive the cache from
  //    */
  //  protected def createCacheFile(path: File): File

  /** This is a constant, but can be overridden by subclasses. */
  protected val decimation: List[Int] = List(1, 6, 6, 6, 6)

  // private var constQCache   = Map.empty[SonogramSpec, ConstQCache]
  private var imageCache    = Map.empty[(Int, Int), ImageCache]
  private var fileBufCache  = Map.empty[ImageSpec, FileBufCache]

  // private var futures       = Map.empty[OvrSpec, Future[OvrOut]]
  private var overviewCache = Map.empty[OvrSpec, OverviewCache]
  private val sync          = new AnyRef

  private val producer = {
    val cc = filecache.Config[OvrSpec, OvrOut]()
    cc.extension  = "sono"
    cc.accept     = { (spec: OvrSpec, out: OvrOut) =>
      val specAF = AudioFile.readSpec(spec.file)
      debug(s"accept ${spec.file.lastModified()} == ${out.input.lastModified} && $specAF == ${out.input.fileSpec}")
      spec.file.lastModified() == out.input.lastModified && specAF == out.input.fileSpec
    }
    cc.space      = { (_: OvrSpec, out: OvrOut) =>
      val res = out.output.length()
      debug(s"space of $out is $res")
      res
    }
    cc.evict      = { (_: OvrSpec, out: OvrOut) =>
      debug(s"evict $out")
      out.output.delete()
    }
    cc.executionContext = config.executionContext

    config.caching match {
      case Some(c) =>
        cc.capacity = filecache.Limit(space = c.sizeLimit)
        debug(s"Producer capacity is ${cc.capacity}")
        cc.folder   = c.folder

      case _ =>
        cc.capacity = filecache.Limit(count = 0, space = 0L)
        val f = File.createTempFile(".cache", "")
        f.delete()
        f.mkdir()
        f.deleteOnExit()
        cc.folder   = f
    }
    filecache.MutableProducer(cc)
  }

  //  private val consumer = Consumer(producer) { ovrSpec =>
  //    overviews
  //    ... : Future[OvrOut]
  //  }

  def acquire(job: Job): Overview = {
    val (ovrSpec, ovrIn) = jobToOverviewConfig(job)
    sync.synchronized {
      val entry = overviewCache.getOrElse(ovrSpec, {
        val res = new OverviewCache(new OverviewImpl(ovrSpec, ovrIn, manager = this, producer = producer))
        overviewCache += ovrSpec -> res
        res
      })
      entry.useCount += 1
      entry.overview
    }
  }

  def release(overview: Overview): Unit = {
    val ovrSpec = overview.config
    sync.synchronized {
      val entry = overviewCache.getOrElse(ovrSpec,
        throw new IllegalStateException(s"Trying to release an unregistered overview")
      )
      entry.useCount -= 1
      debug(s"release $overview; count = ${entry.useCount}")
      if (entry.useCount == 0) {
        overviewCache -= ovrSpec
        entry.overview.dispose()
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

  def dispose(): Unit = sync.synchronized {
    releaseListeners()
    overviewCache.foreach(_._2.overview.abort())
  }

  def allocateConstQ(spec: SonogramSpec): ConstQ = {
    //    sync.synchronized {
    //      val entry = constQCache.get(spec) getOrElse {
    //    val res = new ConstQCache(
    constQFromSpec(spec)
    //    )
    //        constQCache += spec -> res
    //    res
    //      }
    //      entry.useCount += 1
    //      entry.constQ
    //    }
  }

  def releaseConstQ(spec: SonogramSpec): Unit = {
    //    sync.synchronized {
    //      val entry = constQCache(spec) // let it throw an exception if not contained
    //      entry.useCount -= 1
    //      if (entry.useCount == 0) {
    //        constQCache -= spec
    //      }
    //    }
  }

  def allocateImage(spec: ImageSpec): Image = sync.synchronized {
    val img     = allocateImage(spec.width, spec.height)
    val fileBuf = allocateFileBuf(spec)
    new Image(img, fileBuf)
  }

  def releaseImage(spec: ImageSpec): Unit = sync.synchronized {
    releaseImage(spec.width, spec.height)
    releaseFileBuf(spec)
  }

  private def allocateImage(width: Int, height: Int): BufferedImage = sync.synchronized {
    val entry = imageCache.getOrElse((width, height), {
      val res = new ImageCache(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB))
      imageCache += (width, height) -> res
      res
    })
    entry.useCount += 1
    entry.img
  }

  private def allocateFileBuf(spec: ImageSpec): Array[Array[Float]] = sync.synchronized {
    val entry = fileBufCache.getOrElse(spec, {
      val res = new FileBufCache(Array.ofDim[Float](spec.numChannels, spec.width * spec.height))
      fileBufCache += spec -> res
      res
    })
    entry.useCount += 1
    entry.buf
  }

  private def releaseImage(width: Int, height: Int): Unit = sync.synchronized {
    val key         = (width, height)
    val entry       = imageCache(key) // let it throw an exception if not contained
    entry.useCount -= 1
    if (entry.useCount == 0) {
      imageCache -= key
    }
  }

  private def releaseFileBuf(spec: ImageSpec): Unit = sync.synchronized {
    val entry = fileBufCache(spec) // let it throw an exception if not contained
    entry.useCount -= 1
    if (entry.useCount == 0) {
      fileBufCache -= spec
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
}