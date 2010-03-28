package de.sciss.sonogram

import java.awt.image.{ ImageObserver }

trait SonogramPaintController {
   def imageObserver: ImageObserver
   def adjustGain( amp: Float, pos: Double ) : Float
}