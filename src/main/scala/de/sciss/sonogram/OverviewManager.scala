/*
 *  OverviewManager.scala
 *  (Overview)
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
 *    28-Mar-10   extracted from Kontur. Removing app package dependancy
 */

package de.sciss.sonogram

import java.io.File
import de.sciss.dsp.ConstQ

object OverviewManager {
  final case class Config(file: File, analysis: ConstQ.Config = ConstQ.Config())
}
trait OverviewManager {
//  /**
//   * Creates a new sonogram overview from a given audio file
//   *
//   * @param file    the audio file to analyze
//   * @param config  the settings for the analysis resolution. Note that `sampleRate` will be ignored as it is replaced
//   *                by the file's sample rate. Also note that `maxFreq` will be clipped to nyquist.
//   * @return
//   */
//  def fromFile(file: File, config: ConstQ.Config = ConstQ.Config()): Overview
}