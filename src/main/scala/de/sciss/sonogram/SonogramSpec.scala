/*
 *  SonogramSpec.scala
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

import de.sciss.dsp.ConstQ
import de.sciss.serial.{DataInput, DataOutput, ImmutableSerializer}

object SonogramSpec {
  implicit object Serializer extends ImmutableSerializer[SonogramSpec] {
    private val COOKIE = 0

    def write(v: SonogramSpec, out: DataOutput): Unit = {
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

    def read(in: DataInput): SonogramSpec = {
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

  val numKernels: Int = ConstQ.getNumKernels(bandsPerOct, maxFreq, minFreq)
}
