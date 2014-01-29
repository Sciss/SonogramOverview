/*
 *  DecimationSpec.scala
 *  (Overview)
 *
 *  Copyright (c) 2010-2014 Hanns Holger Rutz. All rights reserved.
 *
 *	This software is published under the GNU General Public License v2+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.sonogram.impl

private[sonogram] final class DecimationSpec(val offset: Long, val numWindows: Long, val decimFactor: Int,
                                             val totalDecim: Int) {
  var windowsReady = 0L

  def markReady(): Unit = windowsReady = numWindows
}

