package de.sciss.sonogram
package impl

import java.awt.image.BufferedImage

private[sonogram] final case class ImageSpec(numChannels: Int, width: Int, height: Int)

private[sonogram] final class Image(val img: BufferedImage, val fileBuf: Array[Array[Float]])
