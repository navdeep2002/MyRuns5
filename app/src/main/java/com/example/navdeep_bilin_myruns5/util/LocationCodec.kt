package com.example.navdeep_bilin_myruns5.util

import com.google.android.gms.maps.model.LatLng
import java.nio.ByteBuffer

// utility object for the encode / decode of the path in gps maps
// used moderate AI here for help with making this
object LocationCodec {

    // Encode a list of LatLng into a single String for storage
    fun encode(points: List<LatLng>): ByteArray {
        val buf = ByteBuffer.allocate(8 + points.size * 16) // count + pairs
        buf.putInt(points.size)     // high 4 bytes used, low 4 padding
        buf.putInt(0)
        for (p in points) {
            buf.putDouble(p.latitude)
            buf.putDouble(p.longitude)
        }
        return buf.array()
    }

    // Decode a stored String back into a list of LatLng points
    fun decode(bytes: ByteArray?): List<LatLng> {
        if (bytes == null) return emptyList()
        val buf = ByteBuffer.wrap(bytes)
        val count = buf.int // read high
        buf.int              // advance padding
        return buildList(count) {
            repeat(count) { add(LatLng(buf.double, buf.double)) }
        }
    }
}
