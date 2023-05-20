package mcx.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

class Rcon private constructor(
  private val socket: Socket,
) : Closeable {
  private val input: InputStream = socket.getInputStream()
  private val output: OutputStream = socket.getOutputStream()

  companion object {
    private const val AUTH: Int = 3
    private const val AUTH_RESPONSE: Int = 2
    private const val EXECCOMMAND: Int = 2
    private const val RESPONSE_VALUE: Int = 0
    private const val AUTH_FAILURE: Int = -1

    suspend fun connect(
      password: String,
      hostname: String,
      port: Int,
      timeout: Long,
    ): Rcon {
      lateinit var socket: Socket
      val start = System.currentTimeMillis()
      while (true) {
        try {
          socket = Socket(hostname, port)
          break
        } catch (e: ConnectException) {
          if (System.currentTimeMillis() - start > timeout) {
            throw e
          } else {
            delay(1L.seconds)
          }
        }
      }
      val rcon = Rcon(socket)
      val auth = rcon.request(Packet.fresh(AUTH, password))
      check(auth.id != AUTH_FAILURE)
      check(auth.type == AUTH_RESPONSE)
      return rcon
    }
  }

  suspend fun exec(command: String): String {
    return request(Packet.fresh(EXECCOMMAND, command)).body
  }

  private suspend fun request(packet: Packet): Packet {
    return withContext(Dispatchers.IO) {
      output.write(
        ByteBuffer
          .allocate(packet.size + 4)
          .order(ByteOrder.LITTLE_ENDIAN)
          .putInt(packet.size)
          .putInt(packet.id)
          .putInt(packet.type)
          .put(packet.encodedBody)
          .put(0)
          .put(0)
          .flip()
          .array()
      )
      output.flush()
      Packet.from(input)
    }
  }

  override fun close() {
    socket.close()
  }
}

class Packet private constructor(
  val id: Int,
  val type: Int,
  val body: String,
) {
  val encodedBody: ByteArray = body.encodeToByteArray()
  val size: Int = encodedBody.size + Int.SIZE_BYTES + Int.SIZE_BYTES + Byte.SIZE_BYTES + Byte.SIZE_BYTES

  companion object {
    private val freshId: AtomicInteger = AtomicInteger()

    fun fresh(type: Int, body: String): Packet {
      return Packet(freshId.getAndIncrement(), type, body)
    }

    fun from(input: InputStream): Packet {
      fun readInt(): Int {
        return ByteBuffer.wrap(input.readNBytes(Int.SIZE_BYTES)).order(ByteOrder.LITTLE_ENDIAN).getInt()
      }

      val size = readInt()
      val id = readInt()
      val type = readInt()
      val body = String(input.readNBytes(size - Int.SIZE_BYTES - Int.SIZE_BYTES - Byte.SIZE_BYTES - Byte.SIZE_BYTES))
      input.read() // drop null
      input.read() // drop null
      return Packet(id, type, body)
    }
  }
}
