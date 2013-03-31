package de.sciss.sonogram.impl

private[sonogram] final class DecimationSpec(val offset: Long, val numWindows: Long, val decimFactor: Int,
                                             val totalDecim: Int) {
  var windowsReady = 0L
}

