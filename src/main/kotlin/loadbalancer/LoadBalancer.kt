package loadbalancer

import java.io.FileInputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.*
import java.util.*

private data class Params(val strategy: ConnectionSelectionStrategy, val serverPort: Int, val backEndServers: List<Int>)

fun main(args: Array<String>) {
    val params = if (args.isEmpty()) {
        readFromProperties()
    } else {
        Params(
            strategy = ConnectionSelectionStrategy.valueOf(args.first()),
            serverPort = args.component2().toInt(),
            backEndServers = args.slice(2..<args.size).toList().map { it.toInt() }
        )
    }
    LoadBalancer(params.strategy, params.serverPort, params.backEndServers).start()
}

private fun readFromProperties(): Params {
    val properties = Properties().apply { load(FileInputStream("config.properties")) }
    val strategy = ConnectionSelectionStrategy.valueOf(properties.getProperty("balancing_strategy"))
    val serverPort = properties.getProperty("server_port").trim().toInt()
    val backEndServers = properties.getProperty("upstream_ports").split(",").map { it.trim().toInt() }
    return Params(strategy, serverPort, backEndServers)
}

class LoadBalancer(
    private val strategy: ConnectionSelectionStrategy,
    private val serverPort: Int, private val backEndServers: List<Int>
) {
    private data class Session(
        val buffer: ByteBuffer, val peerChannel: SocketChannel, val upstreamConnection: SocketConnection
    )

    private var count = 0

    private val connectionPool = ConnectionPool(
        connectionFactory = {
            val port = backEndServers[count++ % backEndServers.size]
            println("factory called for $port")
            SocketConnection(port)
        },
        maxConnections = backEndServers.size,
        strategy = strategy
    )

    fun start() {
        println("starting load-balancer with $strategy strategy on port $serverPort, backEndServers:$backEndServers")
        val serverChannel = ServerSocketChannel.open()
        serverChannel.bind(InetSocketAddress("localhost", serverPort))
        serverChannel.configureBlocking(false)
        val selector = Selector.open()
        serverChannel.register(selector, SelectionKey.OP_ACCEPT)
        while (true) {
            selector.select()
            val keys = selector.selectedKeys()
            val iterator = keys.iterator()
            while (iterator.hasNext()) {
                val key = iterator.next()
                if (!key.isValid) {
                    println("invalid-key")
                    continue
                }
                val channel = key.channel()
                when {
                    key.isAcceptable -> {
                        val clientChannel = serverChannel.accept()
                        clientChannel.configureBlocking(false)
                        val upstreamConnection = connectionPool.getConnection()
                        val upstreamChannel = upstreamConnection.getChannel()
                        val buffer = ByteBuffer.allocate(1024)
                        clientChannel.register(
                            selector, SelectionKey.OP_READ,
                            Session(buffer, upstreamChannel, upstreamConnection)
                        )
                        upstreamChannel.configureBlocking(false)
                        upstreamChannel.register(
                            selector, SelectionKey.OP_CONNECT,
                            Session(buffer, clientChannel, upstreamConnection)
                        )
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
                            connectionPool.releaseConnection(session.upstreamConnection)
                        } else
                            session.peerChannel.register(
                                selector, SelectionKey.OP_WRITE,
                                Session(session.buffer, key.channel() as SocketChannel, session.upstreamConnection)
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