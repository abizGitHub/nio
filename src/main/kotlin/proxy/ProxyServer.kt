package proxy

import java.io.File
import java.io.FileInputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

fun main() {
    val properties = Properties().apply { load(FileInputStream("config.properties")) }
    val listeningPort = properties.getProperty("server_port").trim().toInt()
    val proxyPort = properties.getProperty("proxy_port").trim().toInt()
    val requestLog = properties.getProperty("request_log").trim()
    val responseLog = properties.getProperty("response_log").trim()
    println("Starting proxy port:$listeningPort to $proxyPort")
    ProxyServer(listeningPort, proxyPort, File(requestLog), File(responseLog)).start()
}

enum class FlowSide {
    FROM_CLIENT, TO_SERVER;

    fun flip() = when (this) {
        FROM_CLIENT -> TO_SERVER
        TO_SERVER -> FROM_CLIENT
    }
}

class ProxyServer(
    private val listeningPort: Int,
    private val backendPort: Int,
    private val requestLog: File,
    private val responseLog: File
) {
    var count = 0

    private data class Session(
        val buffer: ByteBuffer,
        val peerChannel: SocketChannel,
        val flowSide: FlowSide
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
                            Session(buffer, client, FlowSide.TO_SERVER)
                        )
                        client.configureBlocking(false)
                        client.register(
                            selector, SelectionKey.OP_CONNECT,
                            Session(buffer, balancerChannel, FlowSide.FROM_CLIENT)
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
                                Session(session.buffer, key.channel() as SocketChannel, session.flowSide.flip())
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
            logContent((key.attachment() as Session).flowSide, buffer)
        }
        buffer.flip()
    }

    private fun logContent(flowSide: FlowSide, buffer: ByteBuffer) {
        val now = Instant.now()
        val localDateTime = ZonedDateTime.ofInstant(now, ZoneId.systemDefault())

        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
        val formatted = localDateTime.format(formatter)
        val socketContent = "\n--------------------$formatted----------------------\n" +
                (buffer.arrayOffset() until buffer.limit())
                    .map { buffer[it].toInt().toChar() }
                    .joinToString("")

        when (flowSide) {
            FlowSide.FROM_CLIENT -> requestLog.appendText(socketContent)
            FlowSide.TO_SERVER -> responseLog.appendText(socketContent)
        }
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
