/*
 *  SimpleSonogramView.scala
 *  (SonogramOverview)
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

import javax.swing.JComponent
import java.awt.{RenderingHints, Color, Graphics, Graphics2D}
import java.awt.image.ImageObserver
import de.sciss.processor.Processor
import scala.util.Success

class SonogramComponent
  extends JComponent with PaintController {
  private var sonoO: Option[Overview] = None
  private var boostVar: Float = 1f

  override def paintComponent(g: Graphics): Unit = {
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
  def sono_=(newSono: Option[Overview]): Unit = {
    sonoO.foreach(_.removeListener(listener))
    sonoO = newSono
    sonoO.foreach(_.addListener(listener))
    repaint()
  }

  def boost = boostVar
  def boost_=(newBoost: Float): Unit = {
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