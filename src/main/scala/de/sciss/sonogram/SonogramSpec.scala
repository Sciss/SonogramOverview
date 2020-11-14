/*
 *  SonogramSpec.scala
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

import de.sciss.dsp.ConstQ
import de.sciss.serial.{DataInput, DataOutput, ConstFormat}

object SonogramSpec {
  implicit object format extends ConstFormat[SonogramSpec] {
    private val COOKIE = 1

    def write(v: SonogramSpec, out: DataOutput): Unit = {
      import v._
      out.writeByte(COOKIE)
      out.writeDouble(sampleRate)
      out.writeDouble(minFreq)
      out.writeDouble(maxFreq)
      out.writeInt(bandsPerOct)
      // out.writeDouble(maxTimeRes)
      out.writeInt(maxFFTSize)
      out.writeInt(stepSize)
    }

    def read(in: DataInput): SonogramSpec = {
      val cookie = in.readByte()
      require(cookie == COOKIE, s"Unexpected cookie $cookie")
      val sampleRate  = in.readDouble()
      val minFreq     = in.readDouble()
      val maxFreq     = in.readDouble()
      val bandsPerOct = in.readInt()
      // val maxTimeRes  = in.readDouble()
      val maxFFTSize  = in.readInt()
      val stepSize    = in.readInt()
      SonogramSpec(sampleRate = sampleRate, minFreq = minFreq, maxFreq = maxFreq, bandsPerOct = bandsPerOct,
        /* maxTimeRes, */ maxFFTSize = maxFFTSize, stepSize = stepSize)
    }
  }
}

final case class SonogramSpec(sampleRate: Double, minFreq: Double, maxFreq: Double,
                              bandsPerOct: Int, /* maxTimeRes: Double, */ maxFFTSize: Int, stepSize: Int) {

  val numKernels: Int = ConstQ.getNumKernels(bandsPerOct, maxFreq, minFreq)
}
