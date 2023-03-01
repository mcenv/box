package mcx

import kotlinx.coroutines.runBlocking
import mcx.phase.Build
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.io.path.Path
import kotlin.test.Test

object BuildTests {
  @Test
  fun std() {
    assertDoesNotThrow {
      runBlocking {
        Build(Path("src", "main", "resources", "std"))()
      }
    }
  }
}
