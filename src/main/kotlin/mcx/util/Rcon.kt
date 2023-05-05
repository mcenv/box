package mcx.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

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
    ): Rcon {
      val rcon = Rcon(Socket(hostname, port))
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
          .put(packet.body.encodeToByteArray())
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
  val size: Int = body.encodeToByteArray().size + Int.SIZE_BYTES + Int.SIZE_BYTES + Byte.SIZE_BYTES + Byte.SIZE_BYTES

  companion object {
    private val freshId: AtomicInteger = AtomicInteger()

    fun fresh(type: Int, body: String): Packet {
      return Packet(freshId.getAndIncrement(), type, body)
    }

    fun from(input: InputStream): Packet {
      fun readInt(): Int {
        return ByteBuffer.wrap(input.readNBytes(Integer.BYTES)).order(ByteOrder.LITTLE_ENDIAN).getInt()
      }

      val size = readInt()
      val id = readInt()
      val type = readInt()
      val body = String(input.readNBytes(size - Integer.BYTES - Integer.BYTES))
      return Packet(id, type, body)
    }
  }
}