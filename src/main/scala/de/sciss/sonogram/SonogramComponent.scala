/*
 *  SimpleSonogramView.scala
 *  (SonogramOverview)
 *
 *  Copyright (c) 2010-2020 Hanns Holger Rutz. All rights reserved.
 *
 *	This software is published under the GNU Lesser General Public License v2.1+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.sonogram

import java.awt.image.ImageObserver
import java.awt.{Color, Graphics, Graphics2D, RenderingHints}

import de.sciss.processor.Processor
import javax.swing.JComponent

import scala.util.Success

class SonogramComponent
  extends JComponent with PaintController {

  private var sonogramOpt: Option[Overview] = None
  private var boostVar: Float = 1f

  override def paintComponent(g: Graphics): Unit = {
    val g2   = g.asInstanceOf[Graphics2D]
    val i    = getInsets
    val x    = i.left
    val y    = i.top
    val w    = getWidth - (i.left + i.right)
    val h    = getHeight - (i.top + i.bottom)
    sonogramOpt.foreach { sonogram =>
      g2.setColor(Color.gray)
      g2.fillRect(x, y, w, h)
      g2.setColor(Color.white)
      g2.drawString("Calculating...", 8, 20)
      // note: we get glitches if using other type of interpolation.
      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
//      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
      // none of these have any visible influence:
      //      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,   RenderingHints.VALUE_ANTIALIAS_ON)
      //      g2.setRenderingHint(RenderingHints.KEY_RENDERING,      RenderingHints.VALUE_RENDER_QUALITY)
      //      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
      sonogram.paint(0L, sonogram.inputSpec.numFrames.toDouble, g2, x, y, w, h, this)
    }
  }

  def sonogram: Option[Overview] = sonogramOpt
  def sonogram_=(value: Option[Overview]): Unit = {
    sonogramOpt.foreach(_.removeListener(listener))
    sonogramOpt = value
    sonogramOpt.foreach(_.addListener(listener))
    repaint()
  }

  def boost: Float = boostVar
  def boost_=(newBoost: Float): Unit = {
    boostVar = newBoost
    repaint()
  }

  // ---- PaintController ----
  def adjustGain(amp: Float, pos: Double): Float = amp * boostVar

  def imageObserver: ImageObserver = this

  // ---- SonogramOverviewListener ----

  private val listener: Overview.Observer = {
//    case Processor.Progress(_, _)         => repaint()
    case Processor.Result(_, Success(_))  => repaint()
  }
}