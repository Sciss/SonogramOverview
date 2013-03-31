package de.sciss.sonogram
package impl

import javax.swing.SwingWorker
import util.control.NonFatal

private[sonogram] final class WorkingSonogram(sono: Overview)
  extends SwingWorker[Unit, Unit] {

  override protected def doInBackground() {
    try {
      ??? // sono.render(this)
    }
    catch {
      case NonFatal(e) => e.printStackTrace()
    }
  }

  override protected def done() {
    ??? // sono.dispatchComplete()
  }
}