/*
 *  ResourceManager.scala
 *  (Overview)
 *
 *  Copyright (c) 2010-2014 Hanns Holger Rutz. All rights reserved.
 *
 *	This software is published under the GNU Lesser General Public License v2.1+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.sonogram

import de.sciss.dsp.ConstQ
import java.awt.image.BufferedImage
import de.sciss.synth.io.Frames

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