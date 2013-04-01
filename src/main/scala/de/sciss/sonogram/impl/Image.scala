package de.sciss.sonogram
package impl

import java.awt.image.BufferedImage
import de.sciss.synth.io.Frames

private[sonogram] final case class ImageSpec(numChannels: Int, width: Int, height: Int)

private[sonogram] final class Image(val img: BufferedImage, val fileBuf: Frames)
