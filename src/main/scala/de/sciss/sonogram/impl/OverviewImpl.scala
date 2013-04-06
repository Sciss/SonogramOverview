/*
 *  Overview.scala
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
 */

package de.sciss.sonogram
package impl

import java.awt.Graphics2D
import java.awt.image.DataBufferInt
import de.sciss.dsp.{ConstQ, FastLog}
import de.sciss.synth.io.{SampleFormat, AudioFileSpec, AudioFileType, AudioFile}
import de.sciss.intensitypalette.IntensityPalette
import java.{util => ju}
import de.sciss.processor.impl.ProcessorImpl
import collection.breakOut
import de.sciss.sonogram.ResourceManager.ImageSpec
import java.io.File
import scala.concurrent.blocking
import de.sciss.filecache.Producer
import Overview.{Output => OvrOut, Input => OvrIn, Config => OvrSpec}

private[sonogram] object OverviewImpl {
  private lazy val log10 = FastLog(base = 10, q = 11)
}

private[sonogram] class OverviewImpl(val config: OvrSpec, input: OvrIn,
                                     manager: ResourceManager, // folder: File,
                                     producer: Producer[OvrSpec, OvrOut])
  extends Overview with ProcessorImpl[OvrOut, Overview] {

  import OverviewImpl._

  val inputSpec           = input.fileSpec
  private val futOut      = producer.acquireWith(config, this)

  // synchronized via sync
  private var futRes: AudioFile = _

  start()(producer.executionContext)

  futOut.onSuccess {
    case OvrOut(_, f) =>
      sync.synchronized {
        if (!disposed && futRes == null) {
          futRes = AudioFile.openRead(f)
        }
      }
  }

  private val sync        = new AnyRef
  private val numChannels = inputSpec.numChannels
  private val numKernels  = config.sonogram.numKernels
  private val imgSpec     = ImageSpec(numChannels, width = 128, height = numKernels)
  private val sonoImg     = manager.allocateImage(imgSpec)
  private val imgData     = sonoImg.img.getRaster.getDataBuffer.asInstanceOf[DataBufferInt].getData

  // private def decimAF: AudioFile = ...

  // caller must have sync
  private def seekWindow(af: AudioFile, decim: DecimationSpec, idx: Long) {
    val framePos = idx * numKernels + decim.offset
    if ( /* (decim.windowsReady > 0L) && */ (af.position != framePos)) {
      af.seek(framePos)
    }
  }

  private val decimSpecs: Array[DecimationSpec] = {
    var totalDecim  = config.sonogram.stepSize
    var numWindows  = (inputSpec.numFrames + totalDecim - 1) / totalDecim
    var offset      = 0L

    config.decimation.map(decimFactor => {
      if (offset != 0L) require(decimFactor % 2 == 0, s"Only even decimation factors supported: $decimFactor")
      totalDecim   *= decimFactor
      numWindows    = (numWindows + decimFactor - 1) / decimFactor
      val decimSpec = new DecimationSpec(offset, numWindows, decimFactor, totalDecim)
      offset       += numWindows * numKernels
      decimSpec
    })(breakOut)
  }

  private def getBestDecim(idealDecim: Float): DecimationSpec = {
    var i = 0
    while (i < decimSpecs.length && decimSpecs(i).totalDecim < idealDecim) i += 1
    decimSpecs(i)
  }

  //   val rnd = new java.util.Random()
  def paint(spanStart: Double, spanStop: Double, g2: Graphics2D, tx: Int,
            ty: Int, width: Int, height: Int,
            ctrl: PaintController) {

    val daf  = futRes
    if (daf == null) return

    val idealDecim  = ((spanStop - spanStart) / width).toFloat
    val in          = getBestDecim(idealDecim)
    // val scaleW        = idealDecim / in.totalDecim
    val scaleW      = in.totalDecim / idealDecim
    val vDecim      = math.max(1, (numChannels * numKernels) / height)
    val numVFull    = numKernels / vDecim
    // val vRemain       = numKernels % vDecim
    // val hasVRemain    = vRemain != 0
    val scaleH      = height.toFloat / (numChannels * numVFull)
    val startD      = spanStart / in.totalDecim
    val start       = startD.toLong // i.e. trunc
    // val transX = (-(startD % 1.0) / idealDecim).toFloat
    val transX      = (-(startD % 1.0) * scaleW).toFloat
    val stop        = math.ceil(spanStop / in.totalDecim).toLong // XXX include startD % 1.0 ?

    var windowsRead = 0L
    val imgW        = imgSpec.width
    val iOffStart   = (numVFull - 1) * imgW
    val l10         = log10
    // val c             = IntensityColorScheme.colors

    val pixScale    = 1f / 6 // 1072 / bels span
    val pixOff      = 6f // bels floor
    val iBuf        = imgData
    // val numK          = numKernels

    val atOrig = g2.getTransform
    try {
      g2.translate(tx + transX, ty)
      g2.scale(scaleW, scaleH)
      var xOff      = 0
      var yOff      = 0
      var fOff      = 0
      var iOff      = 0
      var x         = 0
      var v         = 0
      var i         = 0
      var sum       = 0f
      var xReset    = 0
      var firstPass = true
      sync.synchronized {
        if (in.windowsReady <= start) return // or draw busy-area
        seekWindow(daf, in, start)
        val numWindows = math.min(in.windowsReady, stop) - start
        while (windowsRead < numWindows) {
          val chunkLen2 = math.min(imgW - xReset, numWindows - windowsRead).toInt
          val chunkLen  = chunkLen2 + xReset
          daf.read(sonoImg.fileBuf, 0, chunkLen2 * numKernels)
          windowsRead += chunkLen2
          if (firstPass) {
            firstPass = false
          } else {
            xReset  = 4 // overlap
            iOff    = 0
            v       = 0
            while (v < numVFull) {
              iBuf(iOff)     = iBuf(iOff + imgW - 4)
              iBuf(iOff + 1) = iBuf(iOff + imgW - 3)
              iBuf(iOff + 2) = iBuf(iOff + imgW - 2)
              iBuf(iOff + 3) = iBuf(iOff + imgW - 1)
              v    += 1
              iOff += imgW
            }
          }
          yOff   = 0
          var ch = 0
          while (ch < numChannels) {
            val fBuf = sonoImg.fileBuf(ch)
            fOff = 0
            x    = xReset
            while (x < chunkLen) {
              iOff = iOffStart + x
              v    = 0
              while (v < numVFull) {
                sum = fBuf(fOff)
                i = 0
                while (i < vDecim) {
                  sum  += fBuf(fOff)
                  fOff += 1
                  i    += 1
                }
                val amp = ctrl.adjustGain(sum / vDecim, (iOff + xOff) / scaleW)
                iBuf(iOff) = IntensityPalette.apply(
                  (l10.calc(math.max(1.0e-9f, amp)) + pixOff) * pixScale
                )
                v   += 1
                iOff -= imgW
              }
              /*
               if( hasVRemain ) {
                  var sum = fBuf( fOff )
                  fOff += 1
                  var i = 0; while( i < vRemain ) {
                     sum += fBuf( fOff )
                     fOff += 1; i += 1
                  }
                  val ampLog = l10.calc( max( 1.0e-9f, sum / vRemain ))
                  iBuf( iOff ) = c( max( 0, min( 1072, ((ampLog + pixOff) * pixScale).toInt )))
                  iOff += imgW
               }
              */
              x += 1
            }
            //                  g2.drawImage( sonoImg.img, xOff, yOff, observer )
            g2.drawImage(sonoImg.img, xOff, yOff, xOff + chunkLen, yOff + numVFull,
                         0, 0, chunkLen, numVFull, ctrl.imageObserver)
            ch   += 1
            yOff += numVFull
          }
          xOff += chunkLen - 4
        }
      }
    }
    finally {
      g2.setTransform(atOrig)
    }
  }

  protected def body(): OvrOut = blocking {
    val constQ = manager.allocateConstQ(config.sonogram)
    // val fftSize = constQ.getFFTSize
    val t1 = System.currentTimeMillis
    var daf: AudioFile = null
    val f = File.createTempFile("sono", ".au", producer.config.folder)
    try {
      val af = AudioFile.openRead(config.file)
      try {
        val afd = AudioFileSpec(AudioFileType.NeXT, SampleFormat.Float,
          numChannels = inputSpec.numChannels, sampleRate = inputSpec.sampleRate)
        daf     = AudioFile.openWrite(f, afd)
        sync.synchronized { futRes = daf }
        try {
          primaryRender(daf, constQ, af)
        } finally {
          daf.cleanUp()
        }
      }
      finally {
        af.cleanUp()
      }
    }
    finally {
      manager.releaseConstQ(config.sonogram)
    }
    val t2      = System.currentTimeMillis
    var idxIn   = 0
    var idxOut  = 1
    while (idxOut < decimSpecs.length) {
//      if (ws.isCancelled) return
      // if( verbose ) println( "start " + pair.head.totalDecim )
      secondaryRender(daf, decimSpecs(idxIn), decimSpecs(idxOut))
      // if( verbose ) println( "finished " + pair.head.totalDecim )
      idxIn   = idxOut
      idxOut += 1
    }
    daf.flush()
    val t3 = System.currentTimeMillis
    if (Overview.verbose) println("primary : secondary ratio = " + (t2 - t1).toDouble / (t3 - t2))

    OvrOut(input, f)
  }

  private def primaryRender(daf: AudioFile, constQ: ConstQ, in: AudioFile) {
    val fftSize     = constQ.fftSize
    val stepSize    = config.sonogram.stepSize
    val inBuf       = Array.ofDim[Float](numChannels, fftSize)
    val outBuf      = Array.ofDim[Float](numChannels, numKernels)

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
        constQ.transform(inBuf(ch), fftSize, outBuf(ch), 0, 0)
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

  // XXX THIS NEEDS BIGGER BUFSIZE BECAUSE NOW WE SEEK IN THE SAME FILE
  // FOR INPUT AND OUTPUT!!!
  private def secondaryRender(daf: AudioFile, in: DecimationSpec, out: DecimationSpec) {
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

  private var disposed = false

  def dispose() {
    sync.synchronized(
      if (!disposed) {
        disposed = true
        releaseListeners()
        manager.releaseImage(imgSpec)
        // decimAF.cleanUp()
        futOut.onSuccess {
          case _ => sync.synchronized {
            futRes.cleanUp()   // XXX delete?
            futRes = null
          }
        }
      }
    )
  }
}