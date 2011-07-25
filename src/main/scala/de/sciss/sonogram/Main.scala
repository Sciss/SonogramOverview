/*
 *  Main.scala
 *  (SonogramOverview)
 *
 *  Copyright (c) 2004-2011 Hanns Holger Rutz. All rights reserved.
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
 *
 *
 *  Changelog:
 */

package de.sciss.sonogram

import java.awt.{ BorderLayout, EventQueue, FileDialog }
import java.io.{ File }
import javax.swing.{ JFrame, JSlider, SwingConstants, WindowConstants }
import de.sciss.dsp.MathUtil
import javax.swing.event.{ ChangeEvent, ChangeListener }

object Main extends Runnable {
   def main( args: Array[ String ]) {
      EventQueue.invokeLater( this )
   }

   def run() {
      val f = new JFrame()
      val cp = f.getContentPane

      val fDlg = new FileDialog( f, "Select an audio file" )
      fDlg.setVisible( true )
      val fName = fDlg.getFile
      val fDir  = fDlg.getDirectory
      if( fName == null || fDir == null ) System.exit( 1 )

      f.setTitle( "SonagramOverview Demo : " + fName )

      try {
         val path    = new File( fDir, fName )
         val mgr     = new SimpleSonogramOverviewManager
         val ov      = mgr.fromPath( path )
         val view    = new SimpleSonogramView
         view.boost  = 4f
         view.sono   = Some( ov )
         val ggBoost = new JSlider( SwingConstants.VERTICAL, 0, 360, 120 )
         ggBoost.addChangeListener( new ChangeListener {
            def stateChanged( e: ChangeEvent ) {
//               if( !ggBoost.getValueIsAdjusting ) {
                  view.boost = MathUtil.dBToLinear( ggBoost.getValue * 0.1 ).toFloat
//               }
            }
         })
         ggBoost.putClientProperty( "JComponent.sizeVariant", "small" )
         cp.add( view, BorderLayout.CENTER )
         cp.add( ggBoost, BorderLayout.EAST )
         f.setSize( 800, 300 )
         f.setLocationRelativeTo( null )
         f.setDefaultCloseOperation( WindowConstants.EXIT_ON_CLOSE )
         f.setVisible( true )
      }
      catch {
         case e => {
            e.printStackTrace()
            System.exit( 1 )
         }
      }
   }
}