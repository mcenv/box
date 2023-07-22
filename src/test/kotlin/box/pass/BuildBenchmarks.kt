package box.pass

import kotlinx.benchmark.*
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@Warmup(iterations = 5, batchSize = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, batchSize = 1, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.SingleShotTime)
class BuildBenchmarks {
  @Benchmark
  fun core() {
    runBlocking { Build(BuildTests.core, BuildTests.core)() }
  }

  @Benchmark
  fun test() {
    runBlocking { Build(BuildTests.test, BuildTests.core)() }
  }
}
