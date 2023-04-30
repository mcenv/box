package mcx.util

class State<S> @PublishedApi internal constructor(
  private var state: S,
) {
  fun get(): S {
    return state
  }

  fun set(state: S) {
    this.state = state
  }

  inline fun modify(action: (S) -> S) {
    set(action(get()))
  }
}

@Suppress("nothing_to_inline")
inline fun <S> stateOf(state: S): State<S> {
  return State(state)
}
