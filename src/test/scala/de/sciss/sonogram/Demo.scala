/*
 *  Main.scala
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

import java.awt.FileDialog
import java.io.File
import de.sciss.dsp.Util
import util.control.NonFatal
import java.awt.event.KeyEvent
import de.sciss.desktop.impl.{WindowImpl, SwingApplicationImpl}
import de.sciss.desktop.{Window, KeyStrokes, Menu}
import scala.swing.{Swing, Component, Orientation, Slider, BorderPanel}
import scala.swing.event.ValueChanged
import Swing._

object Demo extends SwingApplicationImpl("Demo") {
  import Util.dbamp

  def useCache = false

  lazy val menuFactory: Menu.Root = {
    import KeyStrokes._
    import KeyEvent._
    Menu.Root()
      .add(Menu.Group("file", "File")
      .add(Menu.Item("open")("Open" -> (menu1 + VK_O)) {
        openDialog()
      })
    )
  }

  val mgr = {
    val cfg = OverviewManager.Config()
    if (useCache) {
      val folder  = new File(sys.props("java.io.tmpdir"), "sono_demo")
      if (!folder.exists()) folder.mkdir()
      cfg.caching = Some(OverviewManager.Caching(folder, (1L << 20) * 256))
    }
    OverviewManager(cfg)
  }

  def openDialog() {
    val fDlg  = new FileDialog(null: java.awt.Frame, "Select an audio file")
    fDlg.setVisible(true)
    val fName = fDlg.getFile
    val fDir  = fDlg.getDirectory
    if (fName != null && fDir != null) {
      open(new File(fDir, fName))
    }
  }

  def open(f: File) {
    try {
      val ov      = mgr.acquire(OverviewManager.Job(f))
      //      ov.addListener {
      //        case Processor.Result(_, Failure(e)) => e.printStackTrace()
      //      }
      val view    = new SonogramComponent
      view.boost  = 4f
      view.sono   = Some(ov)
      val ggBoost = new Slider {
        orientation = Orientation.Vertical
        min         = 0
        max         = 360
        value       = 120

        listenTo(this)
        reactions += {
          case ValueChanged(_) => view.boost = dbamp(value * 0.1).toFloat
        }
        peer.putClientProperty("JComponent.sizeVariant", "small")
      }

      new WindowImpl {
        def handler = windowHandler
        protected def style = Window.Regular

        title     = f.getName
        file      = Some(f)
        contents  = new BorderPanel {
          add(Component.wrap(view), BorderPanel.Position.Center)
          add(ggBoost, BorderPanel.Position.East)
        }

        size = (800, 300)
        // f.setLocationRelativeTo(null)

        closeOperation = Window.CloseIgnore
        reactions += {
          case Window.Closing(_) =>
            dispose()
            mgr.release(ov)
        }

        front()
      }
    }
    catch {
      case NonFatal(e) => {
        e.printStackTrace()
        // sys.exit(1)
      }
    }
  }

  override def init() {
    openDialog()
  }

  def quit() {
    sys.exit(0)
  }
}