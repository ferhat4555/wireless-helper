package com.andrerinas.wirelesshelper.connection

import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch

class NearbySocket : Socket() {
    private var internalInputStream: InputStream? = null
    private var internalOutputStream: OutputStream? = null
    
    private val inputLatch = CountDownLatch(1)
    private val outputLatch = CountDownLatch(1)

    var inputStreamWrapper: InputStream?
        get() = internalInputStream
        set(value) {
            internalInputStream = value
            if (value != null) {
                android.util.Log.i("HUREV_NEARBY", "NearbySocket: InputStream is now AVAILABLE. Releasing latch.")
                inputLatch.countDown()
            }
        }

    var outputStreamWrapper: OutputStream?
        get() = internalOutputStream
        set(value) {
            internalOutputStream = value
            if (value != null) outputLatch.countDown()
        }

    override fun isConnected() = true
    
    override fun getInetAddress(): InetAddress = InetAddress.getLoopbackAddress()

    override fun getInputStream(): InputStream {
        return object : InputStream() {
            private fun waitForStream(): InputStream {
                inputLatch.await()
                return internalInputStream!!
            }

            override fun read(): Int = waitForStream().read()
            override fun read(b: ByteArray): Int = waitForStream().read(b)
            override fun read(b: ByteArray, off: Int, len: Int): Int = waitForStream().read(b, off, len)
            override fun available(): Int = if (inputLatch.count == 0L) internalInputStream!!.available() else 0
            override fun close() = if (inputLatch.count == 0L) internalInputStream!!.close() else Unit
        }
    }

    override fun getOutputStream(): OutputStream {
        return object : OutputStream() {
            private fun waitForStream(): OutputStream {
                outputLatch.await()
                return internalOutputStream!!
            }

            override fun write(b: Int) = waitForStream().write(b)
            override fun write(b: ByteArray) = waitForStream().write(b)
            override fun write(b: ByteArray, off: Int, len: Int) = waitForStream().write(b, off, len)
            override fun flush() = if (outputLatch.count == 0L) internalOutputStream!!.flush() else Unit
            override fun close() = if (outputLatch.count == 0L) internalOutputStream!!.close() else Unit
        }
    }
}
