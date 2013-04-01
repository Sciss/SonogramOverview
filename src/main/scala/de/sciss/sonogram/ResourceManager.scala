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