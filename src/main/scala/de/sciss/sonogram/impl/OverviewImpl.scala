/*
 *  Overview.scala
 *  (SonogramOverview)
*
 *  Copyright (c) 2010-2020 Hanns Holger Rutz. All rights reserved.
 *
 *	This software is published under the GNU Affero General Public License v3+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.sonogram
package impl

import java.awt.Graphics2D
import java.awt.image.DataBufferInt
import java.io.File
import java.{util => ju}

import de.sciss.dsp.{ConstQ, FastLog}
import de.sciss.filecache.MutableProducer
import de.sciss.processor.impl.ProcessorImpl
import de.sciss.sonogram.Overview.{Config => OvrSpec, Input => OvrIn, Output => OvrOut}
import de.sciss.sonogram.ResourceManager.ImageSpec
import de.sciss.audiofile.{AudioFile, AudioFileSpec, AudioFileType, SampleFormat}

import scala.annotation.elidable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, blocking}

private object OverviewImpl {
  private lazy val log10 = FastLog(base = 10, q = 11)
  @elidable(elidable.CONFIG) def debug(what: => String): Unit = println(s"<overview> $what")
}

private[sonogram] final class OverviewImpl(val config: OvrSpec, input: OvrIn,
                                           manager: ResourceManager, // folder: File,
                                           producer: MutableProducer[OvrSpec, OvrOut])
  extends Overview with ProcessorImpl[OvrOut, Overview] {

  import OverviewImpl._

  var palette: Overview.Palette = Overview.Palette.Intensity

  private var disposed    = false
  val inputSpec: AudioFileSpec  = input.fileSpec

  // synchronized via sync
  private var futRes: AudioFile = _

  private val sync        = new AnyRef
  private val numChannels = inputSpec.numChannels
  private val numKernels  = config.sonogram.numKernels
  private val imgW        = 256
  private val imgSpec     = ImageSpec(numChannels, width = imgW /* 128 */, height = numKernels)
  private val sonoImg     = manager.allocateImage(imgSpec)
  private val imgData     = sonoImg.img.getRaster.getDataBuffer.asInstanceOf[DataBufferInt].getData

  // private def decimAF: AudioFile = ...

  // caller must have sync
  private def seekWindow(af: AudioFile, decim: DecimationSpec, idx: Long): Unit = {
    val framePos = idx * numKernels + decim.offset
    if ( /* (decim.windowsReady > 0L) && */ af.position != framePos) {
      // debug(s"Seek decim file, idx = $idx, offset = ${decim.offset}; numKernels = $numKernels, framePos = $framePos")
      // debug(s"File $af; numFrames = ${af.numFrames}; numChannels = ${af.numChannels}; isOpen? ${af.isOpen}")
      af.seek(framePos)
    }
  }

  private val decimSpecs: Array[DecimationSpec] = {
    var totalDecim  = config.sonogram.stepSize
    var numWindows  = (inputSpec.numFrames + totalDecim - 1) / totalDecim
    var offset      = 0L

    config.decimation.iterator.map { decimFactor =>
      if (offset != 0L) require(decimFactor % 2 == 0, s"Only even decimation factors supported: $decimFactor")
      totalDecim   *= decimFactor
      numWindows    = (numWindows + decimFactor - 1) / decimFactor
      val decimSpec = new DecimationSpec(offset, numWindows, decimFactor, totalDecim)
      offset       += numWindows * numKernels
      decimSpec
    } .toArray
  }

  // ---- submission and start ----

  start()(producer.executionContext)

  //  onSuccess({
  //    case out @ OvrOut(_, f) =>
  //      debug(s"future succeeded with $out")
  //      sync.synchronized {
  //        if (!disposed && futRes == null) {
  //          futRes = AudioFile.openRead(f)
  //          debug("opened existing decimation")
  //          var i = 0; while (i < decimSpecs.length ) { decimSpecs(i).markReady(); i += 1 }
  //          dispatch(Processor.Result(this, Success(out)))
  //        }
  //      }
  //  })(producer.executionContext) // crucial not to demand context from Processor trait because that is not known yet

  // ---- public ----

  //   val rnd = new java.util.Random()
  def paint(spanStart: Double, spanStop: Double, g2: Graphics2D, tx: Int,
            ty: Int, width: Int, height: Int,
            ctrl: PaintController): Unit = {

    val daf  = futRes
    if (daf == null) return

    // the ideal decimation factor is input span divided by screen span
    val idealDecim  = ((spanStop - spanStart) / width).toFloat
    // find the greatest factor that is less than or equal to this ideal factor
    // (it might be greater than idealDecim is we zoomed in close enough)
    val in          = getBestDecim(idealDecim)
    // the live decimation is the the integer floor of ideal decimation
    // divided by provided decimation.
    val hDecim      = math.max(1, (idealDecim / in.totalDecim).toInt)
    // the according horizontal graphics context scale for drawing the image
    val hScale      = (in.totalDecim * hDecim) / idealDecim

    // the vertical decimation factor is input num bands * num-channels divided by screen span
    // (this is the integer floor)
    val vDecim      = math.max(1, (numChannels * numKernels) / height)
    // the down-sampled image height. the image only
    // contains _one_ channel, and we iterate over the channels
    // re-using the same image.
    // (used-image-height)
    val uih         = numKernels / vDecim
    // the according vertical graphics context scale for drawing the image
    val vScale      = height.toFloat / (numChannels * uih)
    // the start frame in the decimated file (fractional)
    val startD      = spanStart / in.totalDecim
    // ...and rounded down to integer, actually seekable frame
    val start       = startD.toLong
    // due to this rounding, the image drawing might need to start offset to the left
    val transX      = (-(startD % 1.0) * hScale).toFloat
    // the stop frame in the decimated file
    val stop        = math.ceil(spanStop / in.totalDecim).toLong

    // ???
    var windowsRead = 0L
    // the offset in the image buffer of the
    // left most pixel in the last line
    // (because frequencies are shown bottom-up)
    val iOffStart   = (uih - 1) * imgW
    val l10         = log10

    // colour coding
    val pixScale    = 1f / 6  // 1072 / bels span
    val pixOff      = 6f      // bels floor
    val iBuf        = imgData

    val atOrig = g2.getTransform
    try {
      g2.translate(tx + transX, ty)
      g2.scale    (hScale, vScale)
      var xOff      = 0
      var yOff      = 0
      var iOff      = 0
      // OOO
      //      var xReset    = 0
      //      var firstPass = true

      val uiw = imgW - (imgW % hDecim)  // used image width: largest width being a multiple of the h decimation

      // debug(f"paint span $spanStart%1.1f...$spanStop%1.1f on $width (ideal $idealDecim%1.2f, file ${in.totalDecim}, in-place $hDecim); hScale $hScale%1.4f")

      val p = palette

      sync.synchronized {
        if (in.windowsReady <= start) return // or draw busy-area
        seekWindow(daf, in, start)  // continuously read from here
        // the in-file decimated frames to go
        val numWindows = math.min(in.windowsReady, stop) - start
        while (windowsRead < numWindows) {
          // the in-file chunk length is either the available image buffer width, or the windows left.
          val chunkLen2 = math.min(uiw, numWindows - windowsRead).toInt
          // ???
          val chunkLen  = chunkLen2 // OOO + xReset
          val w = (chunkLen + hDecim - 1) / hDecim  // h decimated frames (ceil integer)
          // remember that the decimated file groups kernels together in subsequent frames,
          // so we need to multiply the logical number of frames by the number of kernels
          daf.read(sonoImg.fileBuf, 0, chunkLen2 * numKernels)  // OOO TODO: here is where the offset goes
          windowsRead += chunkLen2

          // debug(s"chunkLen $chunkLen; w $w")

          // OOO
          //          if (firstPass) {
          //            firstPass = false
          //          } else {      // if not in the first pass, we have a horizontal image overlap going on
          //            xReset  = 4 // the overlap in pixels
          //            iOff    = 0
          //            v       = 0
          //            while (v < uih) {
          //              iBuf(iOff)     = iBuf(iOff + imgW - 4)
          //              iBuf(iOff + 1) = iBuf(iOff + imgW - 3)
          //              iBuf(iOff + 2) = iBuf(iOff + imgW - 2)
          //              iBuf(iOff + 3) = iBuf(iOff + imgW - 1)
          //              v    += 1
          //              iOff += imgW
          //            }
          //          }
          yOff   = 0
          var ch = 0
          while (ch < numChannels) {
            val fBuf  = sonoImg.fileBuf(ch)
            var x     = 0      // OOO xReset
            var frame = 0
            var ehd   = hDecim // effective horizontal decimation
            while (x < w) {
              iOff      = iOffStart + x
              if (frame + ehd > chunkLen) ehd = chunkLen - frame
              var y     = 0
              var fOff  = frame * numKernels
              while (y < uih) {
                var sum   = fBuf(fOff)
                var vdi   = 0
                while (vdi < vDecim) {
                  var hdi   = 0
                  var fOff2 = fOff
                  while (hdi < ehd) {
                    sum   += fBuf(fOff2)
                    // sum = math.max(sum, fBuf(fOff2))
                    hdi   += 1
                    fOff2 += numKernels // = imgH
                  }
                  fOff += 1
                  vdi  += 1
                }
                val amp = ctrl.adjustGain(sum / (vDecim * ehd), (iOff + xOff) / hScale)
                val v   = (l10.calc(math.max(1.0e-9f, amp)) + pixOff) * pixScale
                iBuf(iOff) = p(v.toFloat) // IntensityPalette.apply()
                y    += 1
                iOff -= imgW
              }
              x     += 1
              frame += hDecim
            }
            g2.drawImage(sonoImg.img, xOff, yOff, w + xOff, uih + yOff,
                                      0,    0,    w,        uih,        ctrl.imageObserver)
            ch   += 1
            yOff += uih     // each channel exactly beneath another
          }
          xOff += w  // OOO - 4
        }
      }
    }
    finally {
      g2.setTransform(atOrig)
    }
  }

  def dispose(): Unit = sync.synchronized {
    if (!disposed) {
      disposed = true
      releaseListeners()
      manager.releaseImage(imgSpec)
      // decimAF.cleanUp()

      /* futOut. */ foreach { _ =>
        sync.synchronized {
          futRes.cleanUp()   // XXX delete?
          futRes = null
          producer.release(config)
        }
      } (producer.executionContext)
    }
  }

  // ---- protected ----

  protected def body(): OvrOut = {
    val futOut  = producer.acquire(config)(generate())
    val out     = Await.result(futOut, Duration.Inf)
    debug(s"future succeeded with $out")
    blocking {
      sync.synchronized {
        if (!disposed && futRes == null) {
          futRes = AudioFile.openRead(out.output)
          debug("opened existing decimation")
          var i = 0; while (i < decimSpecs.length ) { decimSpecs(i).markReady(); i += 1 }
          // dispatch(Processor.Result(this, Success(out)))
        }
      }
    }
    out
  }

  private def generate(): OvrOut = blocking {
    debug("enter body")
    val constQ = manager.allocateConstQ(config.sonogram)
    // val fftSize = constQ.getFFTSize
    // val t1 = System.currentTimeMillis
    var daf: AudioFile = null
    val f = File.createTempFile("sono", ".w64", producer.config.folder)
    debug(s"created decim file $f")
    try {
      val af = AudioFile.openRead(config.file)
      debug(s"opened input file ${config.file}")
      try {
        val afd = AudioFileSpec(AudioFileType.Wave64, SampleFormat.Float,
          numChannels = inputSpec.numChannels, sampleRate = inputSpec.sampleRate)
        daf = AudioFile.openWrite(f, afd)
        debug("opened decim file")
        var success = false
        try {
          sync.synchronized { futRes = daf }
          primaryRender(daf, constQ, af)
          // val t2      = System.currentTimeMillis
          var idxIn  = 0
          var idxOut = 1
          while (idxOut < decimSpecs.length) {
            //      if (ws.isCancelled) return
            // if( verbose ) println( "start " + pair.head.totalDecim )
            secondaryRender(daf, decimSpecs(idxIn), decimSpecs(idxOut))
            // if( verbose ) println( "finished " + pair.head.totalDecim )
            idxIn = idxOut
            idxOut += 1
          }
          daf.flush()
          // well... currently the sequential downsampling doesn't really
          // permit incremental progress notifications :-(
          progress = 1.0
          success = true
        } finally {
          if (!success) daf.cleanUp()
        }
      }
      finally {
        af.cleanUp()
      }
    }
    finally {
      manager.releaseConstQ(config.sonogram)
    }

    debug("body exit")
    OvrOut(input, f)
  }

  // ---- private ----

  private def getBestDecim(idealDecim: Float): DecimationSpec = {
    val d0 = decimSpecs(0)
    if (d0.totalDecim >= idealDecim) return d0
    var i = 1
    var pred = d0
    while (i < decimSpecs.length) {
      val di = decimSpecs(i)
      if (di.totalDecim > idealDecim) return pred
      pred = di
      i += 1
    }
    pred
  }

  private def primaryRender(daf: AudioFile, constQ: ConstQ, in: AudioFile): Unit = {
    debug("enter primaryRender")
    val fftSize     = constQ.fftSize
    val stepSize    = config.sonogram.stepSize
    val inBuf       = Array.ofDim[Float](numChannels, fftSize)
    val outBuf      = Array.ofDim[Float](numChannels, numKernels)
    val inBufD      = new Array[Double](fftSize)
    val outBufD     = new Array[Double](numKernels)

    var inOff       = fftSize / 2
    var inLen       = fftSize - inOff
    val overLen     = fftSize - stepSize
    val numFrames   = inputSpec.numFrames
    var framesRead  = 0L
    val out         = decimSpecs(0)
    var ch          = 0

    var step = 0
    while (step < out.numWindows) {
      checkAborted()

      val chunkLen = math.min(inLen, numFrames - framesRead).toInt
      in.read(inBuf, inOff, chunkLen)
      framesRead += chunkLen
      if (chunkLen < inLen) {
        ch = 0
        while (ch < numChannels) {
          ju.Arrays.fill(inBuf(ch), inOff + chunkLen, fftSize, 0f)
          ch += 1
        }
      }
      ch = 0
      while (ch < numChannels) {
        // input, inOff, inLen, output, outOff
        var i = 0
        val inBufCh = inBuf(ch)
        while (i < fftSize) {
          inBufD(i) = inBufCh(i).toDouble
          i += 1
        }
        constQ.transform(inBufD, fftSize, outBufD, 0, 0)
        i = 0
        val outBufCh = outBuf(ch)
        while (i < numKernels) {
          outBufCh(i) = outBufD(i).toFloat
          i += 1
        }
        ch += 1
      }

      sync.synchronized {
        seekWindow(daf, out, out.windowsReady)
        daf.write(outBuf, 0, numKernels)
        out.windowsReady += 1
      }

      ch = 0
      while (ch < numChannels) {
        val convBuf = inBuf(ch)
        System.arraycopy(convBuf, stepSize, convBuf, 0, overLen)
        ch += 1
      }

      if (step == 0) {
        // stupid one instance case
        inOff = overLen
        inLen = stepSize
      }
      step += 1
    }
  }

  // XXX THIS NEEDS BIGGER BUF-SIZE BECAUSE NOW WE SEEK IN THE SAME FILE
  // FOR INPUT AND OUTPUT!!!
  private def secondaryRender(daf: AudioFile, in: DecimationSpec, out: DecimationSpec): Unit = {
    debug(s"enter secondaryRender ${in.decimFactor}")
    val dec         = out.decimFactor
    val bufSize     = dec * numKernels
    val buf         = Array.ofDim[Float](numChannels, bufSize)
    // since dec is supposed to be even, this
    // lands on the beginning of a kernel:
    var inOff       = bufSize / 2
    var inLen       = bufSize - inOff
    var windowsRead = 0L

    var step = 0
    var ch = 0
    while (step < out.numWindows) {
      checkAborted()

      val chunkLen = math.min(inLen, (in.numWindows - windowsRead) * numKernels).toInt
      sync.synchronized {
        seekWindow(daf, in, windowsRead)
        daf.read(buf, inOff, chunkLen)
      }
      windowsRead += chunkLen / numKernels
      if (chunkLen < inLen) {
        ch = 0
        while (ch < numChannels) {
          ju.Arrays.fill(buf(ch), inOff + chunkLen, bufSize, 0f)
          ch += 1
        }
      }
      ch = 0
      while (ch < numChannels) {
        val convBuf = buf(ch)
        var i = 0
        while (i < numKernels) {
          var sum = 0f
          var j = i
          while (j < bufSize) {
            sum += convBuf(j)
            // sum = math.max(sum, convBuf(j))
            j   += numKernels
          }
          convBuf(i) = sum / dec
          i += 1
        }
        ch += 1
      }

      sync.synchronized {
        seekWindow(daf, out, out.windowsReady)
        daf.write(buf, 0, numKernels)
        out.windowsReady += 1
      }

      if (step == 0) {
        // stupid one instance case
        inOff = 0
        inLen = bufSize
      }
      step += 1
    }
  }
}