/*
 *  SimpleSonogramView.scala
 *  (SonogramOverview)
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

import javax.swing.JComponent
import java.awt.{RenderingHints, Color, Graphics, Graphics2D}
import java.awt.image.ImageObserver
import de.sciss.processor.Processor
import scala.util.Success

class SonogramComponent
  extends JComponent with PaintController {
  private var sonoO: Option[Overview] = None
  private var boostVar: Float = 1f

  override def paintComponent( g: Graphics ) {
    val g2   = g.asInstanceOf[Graphics2D]
    val i    = getInsets
    val x    = i.left
    val y    = i.top
    val w    = getWidth - (i.left + i.right)
    val h    = getHeight - (i.top + i.bottom)
    sonoO.foreach { sono =>
      g2.setColor(Color.gray)
      g2.fillRect(x, y, w, h)
      g2.setColor(Color.white)
      g2.drawString("Calculating...", 8, 20)
      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,  RenderingHints.VALUE_INTERPOLATION_BILINEAR)
      // none of these have any visible influence:
      //      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,   RenderingHints.VALUE_ANTIALIAS_ON)
      //      g2.setRenderingHint(RenderingHints.KEY_RENDERING,      RenderingHints.VALUE_RENDER_QUALITY)
      //      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
      sono.paint(0L, sono.inputSpec.numFrames, g2, x, y, w, h, this)
    }
  }

  def sono = sonoO
  def sono_=(newSono: Option[Overview]) {
    sonoO.foreach(_.removeListener(listener))
    sonoO = newSono
    sonoO.foreach(_.addListener(listener))
    repaint()
  }

  def boost = boostVar
  def boost_=(newBoost: Float) {
    boostVar = newBoost
    repaint()
  }

  // ---- PaintController ----
  def adjustGain(amp: Float, pos: Double) = amp * boostVar

  def imageObserver: ImageObserver = this

  // ---- SonogramOverviewListener ----

  private val listener: Overview.Observer = {
//    case Processor.Progress(_, _)         => repaint()
    case Processor.Result(_, Success(_))  => repaint()
  }
}