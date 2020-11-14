/*
 *  ResourceManager.scala
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

import java.awt.image.BufferedImage

import de.sciss.audiofile.AudioFile.Frames
import de.sciss.dsp.ConstQ

object ResourceManager {
  final case class ImageSpec(numChannels: Int, width: Int, height: Int)

  final class Image(val img: BufferedImage, val fileBuf: Frames)
}
trait ResourceManager {
  import ResourceManager._

  def allocateConstQ(spec: SonogramSpec): ConstQ
  def releaseConstQ (spec: SonogramSpec): Unit

  def allocateImage(spec: ImageSpec): Image
  def releaseImage (spec: ImageSpec): Unit
}