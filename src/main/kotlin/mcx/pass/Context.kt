package mcx.pass

import java.util.concurrent.atomic.AtomicInteger

class Context(
  val config: Config,
) {
  private val id: AtomicInteger = AtomicInteger(0)

  fun freshId(): Int {
    return id.getAndIncrement()
  }
}
