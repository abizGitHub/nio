package loadbalancer

import loadbalancer.ConnectionSelectionStrategy.*
import java.util.*
import kotlin.concurrent.thread

interface Connection {
    fun open(): Boolean
    fun close()
    fun isOpen(): Boolean
    fun getResponseTime(): Long
}

class MyConnection(private val id: String) : Connection {
    private var open = false

    override fun open(): Boolean {
        if (!open) {
            println("Opening connection $id")
            open = true
        }
        return open
    }

    override fun close() {
        if (open) {
            println("Closing connection $id")
            open = false
        }
    }

    override fun isOpen(): Boolean = open

    override fun getResponseTime(): Long = Random().nextLong()
}

enum class ConnectionSelectionStrategy { ROUND_ROBIN, LEAST_RESPONSE_TIME }

class ConnectionPool<T : Connection>(
    private val connectionFactory: () -> T,
    private val maxConnections: Int = 10,
    private val strategy: ConnectionSelectionStrategy = ROUND_ROBIN
) {
    private val availableConnections: Queue<T> = LinkedList()
    private val usedConnections: MutableList<T> = mutableListOf()

    init {
        fillPool()
    }

    private fun fillPool() {
        repeat(maxConnections) {
            availableConnections.add(connectionFactory())
        }
    }

    @Synchronized
    fun getConnection(): T {
        val connection = when (strategy) {
            ROUND_ROBIN -> getConnectionRoundRobin()
            LEAST_RESPONSE_TIME -> getConnectionLeastResponseTime()
        }
        if (connection.isOpen()) {
            usedConnections.add(connection)
        }
        return connection
    }

    // Round Robin strategy: Return the next available connection in a circular manner
    private fun getConnectionRoundRobin(): T {
        if (availableConnections.isEmpty()) {
            fillPool()
        }
        val connection = availableConnections.poll()
        return if (connection.open()) {
            connection
        } else {
            throw IllegalStateException("Connection $connection failed to open.")
        }
    }

    // Least Response Time strategy: Return the connection with the least response time
    private fun getConnectionLeastResponseTime(): T {
        if (availableConnections.isEmpty()) {
            fillPool()
        }
        return availableConnections.minByOrNull { it.getResponseTime() }!!.let {
            it.open()
            availableConnections.remove(it)
            it
        }
    }

    // Return a connection back to the pool
    @Synchronized
    fun releaseConnection(connection: T) {
        usedConnections.remove(connection)
        connection.close()
        availableConnections.offer(connection)
    }

    // Close all connections in the pool
    fun close() {
        availableConnections.forEach { it.close() }
        usedConnections.forEach { it.close() }
    }
}

fun main() {
    val connectionPool = ConnectionPool(
        connectionFactory = { MyConnection(UUID.randomUUID().toString()) },
        maxConnections = 5,
        strategy = ROUND_ROBIN
    )
    repeat(10) { threadIndex ->
        thread {
            try {
                println("Thread $threadIndex: Getting connection from the pool")
                val connection = connectionPool.getConnection()
                println("Thread $threadIndex: Got connection $connection with response time ${connection.getResponseTime()}ms")
                // Simulate doing some work with the connection
                Thread.sleep(1000)
                connectionPool.releaseConnection(connection)
                println("Thread $threadIndex: Released connection $connection")
            } catch (e: IllegalStateException) {
                println("Thread $threadIndex: Failed to get a connection - ${e.message}")
            }
        }
    }

    // Wait for all threads to finish
    Thread.sleep(5000)

    // Close the pool
    connectionPool.close()
    println("Connection pool closed.")
}
