/*
 *  SonogramOverview.scala
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

import java.awt.Graphics2D
import de.sciss.model.Model
import de.sciss.synth.io.AudioFile
import impl.{SonogramOverviewImpl => Impl}

object SonogramOverview {
  sealed trait Update { val view: SonogramOverview }
  final case class Complete(view: SonogramOverview) extends Update

  type Listener = Model.Listener[Update]

  def apply(manager: SonogramOverviewManager, file: SonogramFileSpec, decimation: AudioFile): SonogramOverview =
    new Impl(manager, file, decimation)

  var verbose = false
}
trait SonogramOverview extends Model[SonogramOverview.Update] {
  def fileSpec: SonogramFileSpec

  def paint(spanStart: Double, spanStop: Double, g2: Graphics2D, tx: Int, ty: Int, width: Int, height: Int,
            ctrl: SonogramPaintController): Unit
}
