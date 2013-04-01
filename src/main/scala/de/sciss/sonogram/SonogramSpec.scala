package de.sciss.sonogram

import de.sciss.dsp.ConstQ
import de.sciss.lucre.io.ImmutableSerializer
import de.sciss.lucre.io

object SonogramSpec {
  implicit object Serializer extends ImmutableSerializer[SonogramSpec] {
    private val COOKIE = 0

    def write(v: SonogramSpec, out: io.DataOutput) {
      import v._
      out.writeByte(COOKIE)
      out.writeDouble(sampleRate)
      out.writeFloat(minFreq)
      out.writeFloat(maxFreq)
      out.writeInt(bandsPerOct)
      // out.writeFloat(maxTimeRes)
      out.writeInt(maxFFTSize)
      out.writeInt(stepSize)
    }

    def read(in: io.DataInput): SonogramSpec = {
      val cookie = in.readByte()
      require(cookie == COOKIE, s"Unexpected cookie $cookie")
      val sampleRate  = in.readDouble()
      val minFreq     = in.readFloat()
      val maxFreq     = in.readFloat()
      val bandsPerOct = in.readInt()
      // val maxTimeRes  = in.readFloat()
      val maxFFTSize  = in.readInt()
      val stepSize    = in.readInt()
      SonogramSpec(sampleRate = sampleRate, minFreq = minFreq, maxFreq = maxFreq, bandsPerOct = bandsPerOct,
        /* maxTimeRes, */ maxFFTSize = maxFFTSize, stepSize = stepSize)
    }
  }
}

final case class SonogramSpec(sampleRate: Double, minFreq: Float, maxFreq: Float,
                              bandsPerOct: Int, /* maxTimeRes: Float, */ maxFFTSize: Int, stepSize: Int) {

  val numKernels = ConstQ.getNumKernels(bandsPerOct, maxFreq, minFreq)
}
