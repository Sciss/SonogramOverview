/*
 *  Demo.scala
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

import java.awt.FileDialog
import java.io.File

import de.sciss.dsp.Util

import util.control.NonFatal
import de.sciss.desktop.impl.{SwingApplicationImpl, WindowImpl}
import de.sciss.desktop.{KeyStrokes, Menu, Window, WindowHandler}

import scala.swing._
import scala.swing.event.{Key, ValueChanged}
import Swing._

object Demo extends SwingApplicationImpl("Demo") {
  import Util.dbamp

  def useCache = false

  private val palettes0 = Seq(
    "Intensity" -> Overview.Palette.Intensity,
    "Gray"      -> Overview.Palette.Gray
  )
  private val palettes = palettes0.flatMap { case (namePlain, funPlain) =>
    val nameRev  = s"$namePlain Reverse"
    val nameInv  = s"$namePlain Invert"
    val nameBoth = s"$namePlain Reverse+Invert"
    val funRev   = Overview.Palette.reverse(funPlain)
    val funInv   = Overview.Palette.inverse(funPlain)
    val funBoth  = Overview.Palette.inverse(Overview.Palette.reverse(funPlain))
    Seq((namePlain, funPlain), (nameRev, funRev), (nameInv, funInv), (nameBoth, funBoth))
  }

  lazy val menuFactory: Menu.Root = {
    import KeyStrokes._

    val groupPalette = Menu.Group("palette", "Palette")
    palettes.zipWithIndex.foreach { case ((name0, _ /*fun*/), idx) =>
      val item = Menu.Item(s"palette-$idx", name0 -> (menu1 + Key(Key.Key0.id + idx)))
      groupPalette.add(item)
    }

    Menu.Root()
      .add(Menu.Group("file", "File")
        .add(Menu.Item("open")("Open" -> (menu1 + Key.O)) {
          openDialog()
        })
      )
      .add(groupPalette)
  }

  val mgr: OverviewManager = {
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
        def handler: WindowHandler = windowHandler

        title     = f.getName
        file      = Some(f)
        contents  = new BorderPanel {
          add(Component.wrap(view), BorderPanel.Position.Center)
          add(ggBoost, BorderPanel.Position.East)
        }

        bindMenus(palettes.zipWithIndex.map { case ((_ /*name0*/, fun), idx) =>
          val key = s"palette.palette-$idx"
          val action = Action(null) {
            ov.palette = fun
            view.repaint()
          }
          (key, action)
        } : _*)

        size = (800, 300)
        // f.setLocationRelativeTo(null)

        closeOperation = Window.CloseExit
        reactions += {
          case Window.Closing(_) =>
            dispose()
            mgr.release(ov)
        }

        front()
      }
    }
    catch {
      case NonFatal(e) => e.printStackTrace()
    }
  }

  override def init(): Unit = {
    if (args.length >= 2 && args(0) == "-o") {
      open(new File(args(1)))
    } else {
      openDialog()
    }
  }
}