/*
 *  PaintController.scala
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

import java.awt.image.ImageObserver

trait PaintController {
  def imageObserver: ImageObserver

  def adjustGain(amp: Float, pos: Double): Float
}