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

import java.awt.Graphics2D
import de.sciss.model.Model
import de.sciss.synth.io.{AudioFileSpec, AudioFile}
import de.sciss.processor.Processor
import java.io.File
import de.sciss.serial.{DataInput, DataOutput, ImmutableSerializer}

object Overview {
  object Config {
    private final val COOKIE = 0x53000001 // 'Ttm ', 'S' version 1

    implicit object Serializer extends ImmutableSerializer[Config] {
      def write(v: Config, out: DataOutput) {
        import v._
        out.writeInt(COOKIE)
        out.writeUTF(file.getCanonicalPath)
        AudioFileSpec.Serializer.write(fileSpec, out)
        out.writeLong(lastModified)
        SonogramSpec.Serializer.write(sonogram, out)
        out.writeShort(decimation.size)
        decimation.foreach(out.writeShort _)
      }

      def read(in: DataInput): Config = {
        val cookie = in.readInt()
        require(cookie == COOKIE, s"Unexpected cookie $cookie")
        val file          = new File(in.readUTF())
        val fileSpec      = AudioFileSpec.Serializer.read(in)
        val lastModified  = in.readLong()
        val sonogram      = SonogramSpec.Serializer.read(in)
        val numDecim      = in.readShort()
        val decimation    = List.fill(numDecim)(in.readShort().toInt)
        Config(file, fileSpec, lastModified, sonogram, decimation)
      }
    }
  }
  /** The configuration of an overview generation.
    *
    * @param file         The input audio file.
    * @param fileSpec     The specification of the input audio file.
    * @param lastModified The time stamp of the input audio file.
    * @param sonogram     The sonogram analysis specification.
    * @param decimation   The decimation specification.
    */
  final case class Config(file: File, fileSpec: AudioFileSpec, lastModified: Long, sonogram: SonogramSpec,
                          decimation: List[Int])

//  private def makeAllAvailable() {
//    decimSpecs.foreach(d => d.windowsReady = d.numWindows)
//  }

//  def apply(manager: OverviewManager, file: FileSpec, decimation: AudioFile): Overview =
//    new Impl(manager, file, decimation)

  var verbose = false

  def openRead (config: Config,    decimation: AudioFile,    manager: OverviewManager): Overview = ???
  def openWrite(config: Config, /* decimation: AudioFile, */ manager: OverviewManager): Overview = ???

  type Observer = Model.Listener[Processor.Update[Unit, Overview]]
}
trait Overview extends Processor[Unit, Overview] {
  def config: Overview.Config

  def dispose(): Unit

  def paint(spanStart: Double, spanStop: Double, g2: Graphics2D, tx: Int, ty: Int, width: Int, height: Int,
            ctrl: PaintController): Unit
}