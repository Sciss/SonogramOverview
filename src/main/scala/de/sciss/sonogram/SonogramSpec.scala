package de.sciss.sonogram

import java.io.{DataOutput, DataInput}
import util.control.NonFatal
import de.sciss.dsp.ConstQ

object SonogramSpec {
  private[sonogram] def decode(dis: DataInput): Option[SonogramSpec] = {
    try {
      val sampleRate  = dis.readDouble()
      val minFreq     = dis.readFloat()
      val maxFreq     = dis.readFloat()
      val bandsPerOct = dis.readInt()
      //      val maxTimeRes  = dis.readFloat()
      val maxFFTSize  = dis.readInt()
      val stepSize    = dis.readInt()
      Some(SonogramSpec(sampleRate, minFreq, maxFreq, bandsPerOct, /* maxTimeRes, */ maxFFTSize, stepSize))
    }
    catch {
      case NonFatal(_) => None
    }
  }
}

final case class SonogramSpec(sampleRate: Double, minFreq: Float, maxFreq: Float,
                              bandsPerOct: Int, /* maxTimeRes: Float, */ maxFFTSize: Int, stepSize: Int) {

  val numKernels = ConstQ.getNumKernels(bandsPerOct, maxFreq, minFreq)

  private[sonogram] def encode(dos: DataOutput) {
    dos.writeDouble(sampleRate)
    dos.writeFloat(minFreq)
    dos.writeFloat(maxFreq)
    dos.writeInt(bandsPerOct)
    //    dos.writeFloat(maxTimeRes)
    dos.writeInt(maxFFTSize)
    dos.writeInt(stepSize)
  }
}
