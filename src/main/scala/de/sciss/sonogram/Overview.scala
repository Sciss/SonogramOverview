/*
 *  Overview.scala
 *  (Overview)
 *
 *  Copyright (c) 2010-2020 Hanns Holger Rutz. All rights reserved.
 *
 *	This software is published under the GNU Lesser General Public License v2.1+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.sonogram

import java.awt.Graphics2D
import java.io.File

import de.sciss.intensitypalette.IntensityPalette
import de.sciss.model.Model
import de.sciss.processor.{Processor, ProcessorLike}
import de.sciss.serial.{DataInput, DataOutput, ConstFormat}
import de.sciss.synth.io.AudioFileSpec

object Overview {
  private final val COOKIE = 0x4F56

  object Config {
    implicit object format extends ConstFormat[Config] {
      def write(v: Config, out: DataOutput): Unit = {
        import v._
        out.writeShort(COOKIE)
        out.writeUTF(file.getCanonicalPath)
        SonogramSpec.format.write(sonogram, out)
        out.writeShort(decimation.size)
        decimation.foreach(out.writeShort)
      }

      def read(in: DataInput): Config = {
        val cookie = in.readShort()
        require(cookie == COOKIE, s"Unexpected cookie $cookie")
        val file          = new File(in.readUTF())
        val sonogram      = SonogramSpec.format.read(in)
        val numDecim      = in.readShort()
        val decimation    = List.fill(numDecim)(in.readShort().toInt)
        Config(file, /* fileSpec, lastModified, */ sonogram, decimation)
      }
    }
  }
  /** The configuration of an overview generation.
    *
    * @param file         The input audio file.
    * @param sonogram     The sonogram analysis specification.
    * @param decimation   The decimation specification.
    */
  final case class Config(file: File, /* fileSpec: AudioFileSpec, lastModified: Long, */ sonogram: SonogramSpec,
                          decimation: List[Int])

  object Input {
    implicit object format extends ConstFormat[Input] {
      def write(v: Input, out: DataOutput): Unit = {
        import v._
        out.writeShort(COOKIE)
        AudioFileSpec.format.write(fileSpec, out)
        out.writeLong(lastModified)
      }

      def read(in: DataInput): Input = {
        val cookie = in.readShort()
        require(cookie == COOKIE, s"Unexpected cookie $cookie")
        val fileSpec      = AudioFileSpec.format.read(in)
        val lastModified  = in.readLong()
        Input(fileSpec, lastModified)
      }
    }
  }
  final case class Input(fileSpec: AudioFileSpec, lastModified: Long)

  object Output {
    implicit object format extends ConstFormat[Output] {
      def write(v: Output, out: DataOutput): Unit = {
        import v._
        out.writeShort(COOKIE)
        Input.format.write(input, out)
        out.writeUTF(output.getPath)
      }

      def read(in: DataInput): Output = {
        val cookie = in.readShort()
        require(cookie == COOKIE, s"Unexpected cookie $cookie")
        val input   = Input.format.read(in)
        val output  = new File(in.readUTF())
        Output(input, output)
      }
    }
  }
  final case class Output(input: Input, output: File)

  type Observer = Model.Listener[Processor.Update[Any, Overview]]

  /** A palette function maps a normalized value between zero and one
    * to an ARGB integer color value.
    */
  type Palette = Float => Int
  object Palette {
    final val Intensity: Palette = IntensityPalette.apply
    final val Gray     : Palette = { x =>
      val c = (math.max(0f, math.min(1f, 1f - x)) * 0xFF + 0.5f).toInt
      0xFF000000 | (c << 16) | (c << 8) | c
    }

    def reverse(in: Palette): Palette = x => in(1.0f - x)
    def inverse(in: Palette): Palette = { x =>
      val colr = in(x)
      colr ^ 0xFFFFFF
    }
  }
}
trait Overview extends ProcessorLike[Any, Overview] {
  def config: Overview.Config
  def inputSpec: AudioFileSpec

  var palette: Overview.Palette

  // def dispose(): Unit

  def paint(spanStart: Double, spanStop: Double, g2: Graphics2D, tx: Int, ty: Int, width: Int, height: Int,
            ctrl: PaintController): Unit
}