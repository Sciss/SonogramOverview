/*
 *  Demo.scala
 *  (SonogramOverview)
 *
 *  Copyright (c) 2010-2014 Hanns Holger Rutz. All rights reserved.
 *
 *	This software is published under the GNU General Public License v2+
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

  def openDialog(): Unit = {
    val fDlg  = new FileDialog(null: java.awt.Frame, "Select an audio file")
    fDlg.setVisible(true)
    val fName = fDlg.getFile
    val fDir  = fDlg.getDirectory
    if (fName != null && fDir != null) {
      open(new File(fDir, fName))
    }
  }

  def open(f: File): Unit = {
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

  override def init(): Unit = openDialog()
}