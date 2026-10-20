package de.goork.songflip

import de.goork.songflip.data.AcousticRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class AcousticRecognizerTest {

    @Test
    fun `parseShazamMatchResponse extracts Herbert Groenemeyer Mensch metadata and Apple Music link`() {
        val sampleResponse = """
        {
          "data": [
            {
              "id": "5970570",
              "type": "shazam-songs",
              "attributes": {
                "matchOffset": 170.517,
                "frequencySkew": 0.0
              }
            }
          ],
          "resources": {
            "shazam-songs": {
              "5970570": {
                "id": "5970570",
                "type": "shazam-songs",
                "attributes": {
                  "type": "MUSIC",
                  "title": "Mensch",
                  "artist": "Herbert Grönemeyer",
                  "primaryArtist": "Herbert Grönemeyer",
                  "label": "Grönland",
                  "explicit": false,
                  "isrc": "DEA620200301",
                  "webUrl": "https://www.shazam.com/track/5970570/mensch",
                  "artwork": {
                    "width": 1000,
                    "height": 1000,
                    "url": "https://is1-ssl.mzstatic.com/image/thumb/Music221/v4/de/f8/e1/def8e106-d714-3165-1695-1fb90e1c9d5c/cover.jpg/{w}x{h}bb.jpg"
                  }
                },
                "relationships": {
                  "songs": {
                    "data": [
                      {
                        "id": "1736884600",
                        "type": "songs"
                      }
                    ]
                  }
                }
              }
            },
            "songs": {
              "1736884600": {
                "id": "1736884600",
                "type": "songs",
                "attributes": {
                  "name": "Mensch",
                  "artistName": "Herbert Grönemeyer",
                  "albumName": "Mensch",
                  "url": "https://music.apple.com/de/album/mensch/1736884592?i=1736884600",
                  "isrc": "DEA620200301"
                }
              }
            }
          }
        }
        """.trimIndent()

        val match = AcousticRecognizer.parseShazamMatchResponse(sampleResponse)
        assertNotNull(match)
        assertEquals("Mensch", match!!.title)
        assertEquals("Herbert Grönemeyer", match.artist)
        assertEquals("1736884600", match.appleMusicId)
        assertEquals("https://music.apple.com/de/album/mensch/1736884592?i=1736884600", match.appleMusicUrl)
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music221/v4/de/f8/e1/def8e106-d714-3165-1695-1fb90e1c9d5c/cover.jpg/800x800bb.jpg",
            match.artworkUrl
        )
        assertEquals("https://www.shazam.com/track/5970570/mensch", match.webUrl)
    }

    @Test
    fun `parseShazamMatchResponse returns null on empty match response`() {
        val emptyResponse = """{"data":[],"resources":{}}"""
        assertNull(AcousticRecognizer.parseShazamMatchResponse(emptyResponse))
    }

    @Test
    fun `applySoftwareAgcAndRms amplifies quiet speaker PCM into Shazam peak range without clipping`() {
        val sampleCount = 1600
        val pcmBytes = ByteArray(sampleCount * 2)
        for (i in 0 until sampleCount) {
            val s = (sin(i * 0.15) * 450.0).toInt()
            pcmBytes[i * 2] = (s and 0xFF).toByte()
            pcmBytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }

        // Run a few frames so smooth AGC converges to target gain
        var gain = 1.0f
        var uiAmp = 0f
        for (frame in 0 until 6) {
            for (i in 0 until sampleCount) {
                val s = (sin(i * 0.15) * 450.0).toInt()
                pcmBytes[i * 2] = (s and 0xFF).toByte()
                pcmBytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
            }
            val res = AcousticRecognizer.applySoftwareAgcAndRms(pcmBytes, pcmBytes.size, gain)
            uiAmp = res.first
            gain = res.second
        }

        var maxOut = 0
        for (i in 0 until sampleCount) {
            val lo = pcmBytes[i * 2].toInt() and 0xFF
            val hi = pcmBytes[i * 2 + 1].toInt()
            val sample = (hi shl 8) or lo
            val a = abs(sample)
            if (a > maxOut) maxOut = a
        }

        assertTrue("Expected boosted peak >= 10000 for libsigx spectral peak detection, got $maxOut", maxOut >= 10000)
        assertTrue("Expected no hard clipping at 32767, got $maxOut", maxOut <= 32767)
        assertTrue("Expected responsive UI amplitude > 0.30f, got $uiAmp", uiAmp > 0.30f)
    }

    @Test
    fun `applySoftwareAgcAndRms does not amplify faint background noise below gate`() {
        val sampleCount = 800
        val pcmBytes = ByteArray(sampleCount * 2)
        for (i in 0 until sampleCount) {
            val s = (sin(i * 0.2) * 30.0).toInt()
            pcmBytes[i * 2] = (s and 0xFF).toByte()
            pcmBytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }

        val (_, nextGain, rawPeak) = AcousticRecognizer.applySoftwareAgcAndRms(pcmBytes, pcmBytes.size, 1.0f)
        assertTrue("Expected raw peak below 45 noise gate, got $rawPeak", rawPeak < 45)
        assertEquals(1.0f, nextGain, 0.001f)
    }
}
