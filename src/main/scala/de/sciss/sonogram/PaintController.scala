package de.sciss.sonogram

import java.awt.image.ImageObserver

trait PaintController {
  def imageObserver: ImageObserver

  def adjustGain(amp: Float, pos: Double): Float
}