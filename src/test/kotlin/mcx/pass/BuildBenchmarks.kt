package mcx.pass

import kotlinx.benchmark.*
import kotlinx.coroutines.runBlocking
import mcx.pass.build.Build
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@Warmup(iterations = 5, batchSize = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, batchSize = 1, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.SingleShotTime)
class BuildBenchmarks {
  @Benchmark
  fun std() {
    runBlocking { Build(BuildTests.std, BuildTests.std)() }
  }

  @Benchmark
  fun test() {
    runBlocking { Build(BuildTests.test, BuildTests.std)() }
  }
}