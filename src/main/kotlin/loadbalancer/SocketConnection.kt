package loadbalancer

import java.net.InetSocketAddress
import java.nio.channels.SocketChannel

class SocketConnection(private val port: Int) : Connection {
    private var responseTime = 0L
    private var open = false
    private lateinit var channel: SocketChannel

    override fun open(): Boolean {
        responseTime = System.currentTimeMillis()
        kotlin.runCatching {
            channel = SocketChannel.open(InetSocketAddress("localhost", port))
            open = true
        }.onFailure {
            open = false
        }
        return open
    }

    override fun close() {
        responseTime = System.currentTimeMillis() - responseTime
        if (open) {
            open = false
        }
    }

    override fun isOpen() = open

    override fun getResponseTime() = responseTime

    fun getChannel() = channel
}
