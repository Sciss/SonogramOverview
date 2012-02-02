/*
 *  SonogramFileSpec.scala
 *  (SonogramOverview)
 *
 *  Copyright (c) 2004-2010 Hanns Holger Rutz. All rights reserved.
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

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream, File }

object SonogramFileSpec {
   private val COOKIE   = 0x53000000  // 'Ttm ', 'S' version 0

   private[ sonogram ] def decode( blob: Array[ Byte ]) : Option[ SonogramFileSpec ] = {
      val bais    = new ByteArrayInputStream( blob )
      val dis     = new DataInputStream( bais )
      val result  = decode( dis )
      bais.close
      result
   }

   private[ sonogram ] def decode( dis: DataInputStream ) : Option[ SonogramFileSpec ] = {
      try {
         val cookie = dis.readInt()
         if( cookie != COOKIE ) return None
         SonogramSpec.decode( dis ).map( sono => {
            val lastModified  = dis.readLong()
            val audioPath     = new File( dis.readUTF() )
            val numFrames     = dis.readLong()
            val numChannels   = dis.readInt()
            val sampleRate    = dis.readDouble()
            val numDecim      = dis.readShort()
            val decim         = (0 until numDecim).map( i => dis.readShort().toInt ).toList
            SonogramFileSpec( sono, lastModified, audioPath, numFrames, numChannels, sampleRate, decim )
         }) orElse None
      }
      catch { case _ => None }
   }
}

case class SonogramFileSpec( sono: SonogramSpec, lastModified: Long, audioPath: File,
                             numFrames: Long, numChannels: Int, sampleRate: Double, decim: List[ Int ]) {

   import SonogramFileSpec._

   private[ sonogram ] val decimSpecs = {
      var totalDecim    = sono.stepSize
      var numWindows    = (numFrames + totalDecim - 1) / totalDecim
      var offset        = 0L

      if( decim.tail.exists( _ % 2 != 0 )) println( "WARNING: only even decim factors supported ATM" )

      decim.map( decimFactor => {
         totalDecim       *= decimFactor
         numWindows        = (numWindows + decimFactor - 1) / decimFactor
         val decimSpec     = new SonogramDecimSpec( offset, numWindows, decimFactor, totalDecim )
         offset           += numWindows * sono.numKernels
         decimSpec
      })
   }

   private[ sonogram ] def makeAllAvailable {
      decimSpecs.foreach( d => d.windowsReady = d.numWindows )
   }

   private[ sonogram ] def expectedDecimNumFrames =
      decimSpecs.last.offset + decimSpecs.last.numWindows * sono.numKernels

   private[ sonogram ] def getBestDecim( idealDecim: Float ) : SonogramDecimSpec = {
      var best = decimSpecs.head
      var i = 0; while( (i < decimSpecs.size) && (decimSpecs( i ).totalDecim < idealDecim) ) {
         best = decimSpecs( i )
         i += 1
      }
      best
   }

   private[ sonogram ] def encode: Array[ Byte ] = {
      val baos = new ByteArrayOutputStream()
      val dos  = new DataOutputStream( baos )
      encode( dos )
      baos.close
      baos.toByteArray
   }

   private[ sonogram ] def encode( dos: DataOutputStream ) {
      dos.writeInt( COOKIE )
      sono.encode( dos )
      dos.writeLong( lastModified )
      dos.writeUTF( audioPath.getCanonicalPath() )
      dos.writeLong( numFrames )
      dos.writeInt( numChannels )
      dos.writeDouble( sampleRate )
      dos.writeShort( decim.size )
      decim.foreach( d => dos.writeShort( d ))
   }
}