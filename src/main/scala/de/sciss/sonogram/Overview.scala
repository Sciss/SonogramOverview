/*
 *  Overview.scala
 *  (Overview)
 *
 *  Copyright (c) 2010-2014 Hanns Holger Rutz. All rights reserved.
 *
 *	This software is published under the GNU General Public License v2+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.sonogram

import java.awt.Graphics2D
import de.sciss.model.Model
import de.sciss.synth.io.AudioFileSpec
import de.sciss.processor.Processor
import java.io.File
import de.sciss.serial.{DataInput, DataOutput, ImmutableSerializer}

object Overview {
  private final val COOKIE = 0x4f56

  object Config {
    implicit object Serializer extends ImmutableSerializer[Config] {
      def write(v: Config, out: DataOutput): Unit = {
        import v._
        out.writeShort(COOKIE)
        out.writeUTF(file.getCanonicalPath)
//        AudioFileSpec.Serializer.write(fileSpec, out)
//        out.writeLong(lastModified)
        SonogramSpec.Serializer.write(sonogram, out)
        out.writeShort(decimation.size)
        decimation.foreach(out.writeShort)
      }

      def read(in: DataInput): Config = {
        val cookie = in.readShort()
        require(cookie == COOKIE, s"Unexpected cookie $cookie")
        val file          = new File(in.readUTF())
//        val fileSpec      = AudioFileSpec.Serializer.read(in)
//        val lastModified  = in.readLong()
        val sonogram      = SonogramSpec.Serializer.read(in)
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
    implicit object Serializer extends ImmutableSerializer[Input] {
      def write(v: Input, out: DataOutput): Unit = {
        import v._
        out.writeShort(COOKIE)
        AudioFileSpec.Serializer.write(fileSpec, out)
        out.writeLong(lastModified)
      }

      def read(in: DataInput): Input = {
        val cookie = in.readShort()
        require(cookie == COOKIE, s"Unexpected cookie $cookie")
        val fileSpec      = AudioFileSpec.Serializer.read(in)
        val lastModified  = in.readLong()
        Input(fileSpec, lastModified)
      }
    }
  }
  final case class Input(fileSpec: AudioFileSpec, lastModified: Long)

  object Output {
    implicit object Serializer extends ImmutableSerializer[Output] {
      def write(v: Output, out: DataOutput): Unit = {
        import v._
        out.writeShort(COOKIE)
        Input.Serializer.write(input, out)
        out.writeUTF(output.getPath)
      }

      def read(in: DataInput): Output = {
        val cookie = in.readShort()
        require(cookie == COOKIE, s"Unexpected cookie $cookie")
        val input   = Input.Serializer.read(in)
        val output  = new File(in.readUTF())
        Output(input, output)
      }
    }
  }
  final case class Output(input: Input, output: File)

  //  * @param fileSpec     The specification of the input audio file.
  //  * @param lastModified The time stamp of the input audio file.

  //  private def makeAllAvailable(): Unit = {
  //    decimSpecs.foreach(d => d.windowsReady = d.numWindows)
  //  }

  //  def apply(manager: OverviewManager, file: FileSpec, decimation: AudioFile): Overview =
  //    new Impl(manager, file, decimation)

  // var verbose = false

  //  def openRead (config: Config,    decimation: AudioFile,    manager: OverviewManager): Overview = ...
  //  def openWrite(config: Config, /* decimation: AudioFile, */ manager: OverviewManager): Overview = ...

  type Observer = Model.Listener[Processor.Update[Any, Overview]]
}
trait Overview extends Processor[Any, Overview] {
  def config: Overview.Config
  def inputSpec: AudioFileSpec

  // def dispose(): Unit

  def paint(spanStart: Double, spanStop: Double, g2: Graphics2D, tx: Int, ty: Int, width: Int, height: Int,
            ctrl: PaintController): Unit
}