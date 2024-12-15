package proxy

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

fun main(args: Array<String>) {
    val listeningPort = args[0].toInt()
    val backendPort = args[1].toInt()
    println("Starting proxy port:$listeningPort to $backendPort")
    ProxyServer(listeningPort, backendPort).start()
}

class ProxyServer(private val listeningPort: Int, private val backendPort: Int) {
    var count = 0

    private data class Session(
        val buffer: ByteBuffer,
        val peerChannel: SocketChannel
    )

    fun start() {
        val proxyServerChannel = ServerSocketChannel.open()
        proxyServerChannel.bind(InetSocketAddress("localhost", listeningPort))
        proxyServerChannel.configureBlocking(false)
        val selector = Selector.open()
        proxyServerChannel.register(selector, SelectionKey.OP_ACCEPT)
        while (true) {
            selector.select()
            val keys = selector.selectedKeys()
            val iterator = keys.iterator()
            while (iterator.hasNext()) {
                val key = iterator.next()
                if (!key.isValid) {
                    println("key-invalllid")
                    continue
                }
                val channel = key.channel()
                when {
                    key.isAcceptable -> {
                        val balancerChannel = proxyServerChannel.accept()
                        balancerChannel.configureBlocking(false)
                        val client = SocketChannel.open(InetSocketAddress("localhost", backendPort))
                        val buffer = ByteBuffer.allocate(1024)
                        balancerChannel.register(
                            selector, SelectionKey.OP_READ,
                            Session(buffer, client)
                        )
                        client.configureBlocking(false)
                        client.register(
                            selector, SelectionKey.OP_CONNECT,
                            Session(buffer, balancerChannel)
                        )
                        count++
                        if (count % 1000 == 0)
                            println(count)
                    }

                    key.isConnectable -> {
                        println("${key.hashCode()} isConnectable")
                        channel.register(selector, SelectionKey.OP_WRITE, key.attachment())
                    }

                    key.isReadable -> {
                        val session = key.attachment() as Session
                        val r: Int = handleRead(key)
                        if (r == -1) {
                            session.peerChannel.close()
                            channel.close()
                            key.cancel()
                        } else
                            session.peerChannel.register(
                                selector, SelectionKey.OP_WRITE,
                                Session(session.buffer, key.channel() as SocketChannel)
                            )
                    }

                    key.isWritable -> {
                        handleWrite(key)
                        key.channel().register(
                            selector,
                            SelectionKey.OP_READ,
                            key.attachment()
                        )
                    }
                }
                iterator.remove()
            }
        }
    }

    private fun handleWrite(key: SelectionKey) {
        val buffer = (key.attachment() as Session).buffer
        kotlin.runCatching {
            (key.channel() as SocketChannel).write(buffer)
        }
        buffer.flip()
    }

    private fun handleRead(key: SelectionKey): Int {
        val buffer = (key.attachment() as Session).buffer
        buffer.clear()
        val r = kotlin.runCatching {
            (key.channel() as SocketChannel).read(buffer)
        }.onFailure {
            it.printStackTrace()
        }
        buffer.flip()
        return r.getOrElse { -1 }
    }

}
