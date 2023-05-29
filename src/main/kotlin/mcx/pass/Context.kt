package mcx.pass

import java.util.concurrent.atomic.AtomicInteger

class Context(
  val config: Config,
) {
  private val procId: AtomicInteger = AtomicInteger(0)
  private val funcId: AtomicInteger = AtomicInteger(0)

  fun freshProcId(): Int {
    return procId.getAndIncrement()
  }

  fun freshFuncId(): Int {
    return funcId.getAndIncrement()
  }
}
