/*
 *  OverviewManager.scala
 *  (Overview)
 *
 *  Copyright (c) 2010-2014 Hanns Holger Rutz. All rights reserved.
 *
 *	This software is published under the GNU General Public License v2+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 *    28-Mar-10   extracted from Kontur. Removing app package dependancy
 */

package de.sciss.sonogram

import java.io.File
import de.sciss.dsp.ConstQ
import de.sciss.model.Model
import util.Try
import impl.{OverviewManagerImpl => Impl}
import de.sciss.serial.{DataOutput, DataInput, ImmutableSerializer}
import scala.concurrent.ExecutionContext
import language.implicitConversions

object OverviewManager {
  object Job {
    implicit object Serializer extends ImmutableSerializer[Job] {
      private final val COOKIE = 0x4a6f

      def write(v: Job, out: DataOutput): Unit = {
        import v._
        out.writeShort(COOKIE)
        out.writeUTF(file.getPath)
        ConstQ.Config.Serializer.write(analysis, out)
      }

      def read(in: DataInput): Job = {
        val cookie = in.readShort()
        require(cookie == COOKIE, s"Unexpected cookie $cookie")
        val file      = new File(in.readUTF())
        val analysis  = ConstQ.Config.Serializer.read(in)
        new Job(file = file, analysis = analysis)
      }
    }
  }
  final case class Job(file: File, analysis: ConstQ.Config = ConstQ.Config())

  sealed trait Update { def overview: Overview }
  final case class Progress(overview: Overview, percent: Float) extends Update {
    def toInt = (percent * 100).toInt
  }
  final case class Result(overview: Overview, value: Try[Unit]) extends Update

  sealed trait ConfigLike {
    def caching: Option[Caching]
    def executionContext: ExecutionContext
  }
  object Config {
    def apply() = new ConfigBuilder
    implicit def build(b: ConfigBuilder): Config = b.build
  }
  final case class Config private[OverviewManager] (caching: Option[Caching], executionContext: ExecutionContext)
    extends ConfigLike
  final class ConfigBuilder private[OverviewManager] extends ConfigLike {
    var caching = Option.empty[Caching]
    var executionContext: ExecutionContext = ExecutionContext.global
    def build = Config(caching, executionContext)
  }

  /** Cache settings.
    *
    * @param folder     the folder to use for caching
    * @param sizeLimit  the maximum size of the cache in bytes
    */
  final case class Caching(folder: File, sizeLimit: Long = -1L)

  def apply(config: Config = Config().build): OverviewManager = new Impl(config)
}
trait OverviewManager extends Model[OverviewManager.Update] {
  import OverviewManager._

  def acquire(job: Job): Overview
  def release(overview: Overview): Unit

  def dispose(): Unit

  def config: Config

  //  /**
  //   * Creates a new sonogram overview from a given audio file
  //   *
  //   * @param file    the audio file to analyze
  //   * @param config  the settings for the analysis resolution. Note that `sampleRate` will be ignored as it is replaced
  //   *                by the file's sample rate. Also note that `maxFreq` will be clipped to nyquist.
  //   * @return
  //   */
  //  def fromFile(file: File, config: ConstQ.Config = ConstQ.Config()): Overview
}