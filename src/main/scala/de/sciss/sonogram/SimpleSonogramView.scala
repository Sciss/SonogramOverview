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
import java.awt.{ Color, Graphics, Graphics2D }
import java.awt.image.ImageObserver

class SimpleSonogramView
extends JComponent with SonogramPaintController {
   private var sonoO: Option[ SonogramOverview ] = None
   private var boostVar: Float = 1f

   override def paintComponent( g: Graphics ) {
      val g2   = g.asInstanceOf[ Graphics2D ]
      val i    = getInsets
      val x    = i.left
      val y    = i.top
      val w    = getWidth - (i.left + i.right)
      val h    = getHeight - (i.top + i.bottom)
      g2.setColor( Color.gray )
      g2.fillRect( x, y, w, h )
      g2.setColor( Color.white )
      g2.drawString( "Calculating...", 8, 20 )
      sonoO.foreach( sono => {
         sono.paint( 0L, sono.fileSpec.numFrames, g2, x, y, w, h, this )
      })
   }

   def sono = sonoO
   def sono_=( newSono: Option[ SonogramOverview ]) {
      sonoO.foreach( _.removeListener( listener ))
      sonoO = newSono
      sonoO.foreach( _.addListener( listener ))
      repaint()
   }

   def boost = boostVar
   def boost_=( newBoost: Float ) {
      boostVar = newBoost
      repaint()
   }

   // ---- SonogramPaintController ----
   def adjustGain( amp: Float, pos: Double ) = amp * boostVar
   def imageObserver: ImageObserver = this

   // ---- SonogramOverviewListener ----

   private val listener = (msg: AnyRef) => msg match {
      case OverviewComplete( ov ) => repaint()
      case _ =>
   }
}