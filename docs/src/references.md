# References

## Papers

* Danielsson, N.A., & Norell, U. (2008). **Parsing Mixfix Operators**. IFL.
* Dragos, I., & Odersky, M. (2009). **Compiling generics through user-directed type specialization**. ICOOOLPS@ECOOP.
* Oliveira, B.C., Moors, A., & Odersky, M. (2010). **Type classes as objects and implicits**. Proceedings of the ACM international conference on Object oriented programming systems languages and applications.
* Jones, R.E., Hosking, A.L., & Moss, J.E. (2011). **The Garbage Collection Handbook: The art of automatic memory management**. Chapman and Hall / CRC Applied Algorithms and Data Structures Series.
* Petříček, T., Orchard, D.A., & Mycroft, A. (2013). **Coeffects: Unified Static Analysis of Context-Dependence**. ICALP.
* Petříček, T., Orchard, D.A., & Mycroft, A. (2014). **Coeffects: a calculus of context-dependent computation**. ICFP.
* Eisenberg, R.A., & Jones, S.L. (2017). **Levity polymorphism**. Proceedings of the 38th ACM SIGPLAN Conference on Programming Language Design and Implementation.
* Downen, P., Ariola, Z.M., Jones, S.L., & Eisenberg, R.A. (2020). **Kinds are calling conventions**. Proceedings of the ACM on Programming Languages, 4, 1 - 29.
* Wood, D.A. (2020). **Polymorphisation: Improving Rust compilation times through intelligent monomorphisation**.
* Huang, X., & Oliveira, B.C. (2021). **Distributing intersection and union types with splits and duality (functional pearl)**. Proceedings of the ACM on Programming Languages, 5, 1 - 24.
* Dunfield, J., & Krishnaswami, N.R. (2021). **Bidirectional Typing**. ACM Computing Surveys (CSUR), 54, 1 - 38.
* Brachthäuser, J.I., Schuster, P., Lee, E., & Boruch-Gruszecki, A. (2022). **Effects, capabilities, and boxes: from scope-based reasoning to type-based reasoning and back**. Proceedings of the ACM on Programming Languages, 6, 1 - 30.
* Kovács, A. (2022). **Staged compilation with two-level type theory**. Proceedings of the ACM on Programming Languages, 6, 540 - 569.
* Marshall, D., Vollmer, M., & Orchard, D.A. (2022). **Linearity and Uniqueness: An Entente Cordiale**. ESOP.
* Odersky, M., Boruch-Gruszecki, A., Lee, E., Brachthäuser, J.I., & Lhoták, O. (2022). **Scoped Capabilities for Polymorphic Effects**. ArXiv, abs/2207.03402.
* Rehman, B., Huang, X., Xie, N., & Oliveira, B.C. (2022). **Union Types with Disjoint Switches**. ECOOP.
* Madsen, M. (2022). **The Principles of the Flix Programming Language**. Proceedings of the 2022 ACM SIGPLAN International Symposium on New Ideas, New Paradigms, and Reflections on Programming and Software.
* Lionel Parreaux. (). **The Ultimate Conditional Syntax**.
* Jonathan Sterling, Robert Harper. (). **A metalanguage for multi-phase modularity**.

## Repositories

* [AndrasKovacs/elaboration-zoo](https://github.com/AndrasKovacs/elaboration-zoo)
* [AndrasKovacs/staged](https://github.com/AndrasKovacs/staged)
* [JetBrains/kotlin](https://github.com/JetBrains/kotlin)
* [bytecodealliance/wasmtime](https://github.com/bytecodealliance/wasmtime)
* [rust-lang/rust](https://github.com/rust-lang/rust)

## Videos

* András Kovács. [Type theory elaboration 1: bidirectional type checking](https://youtu.be/_K5Yt-cmKcY).
* András Kovács. [Type theory elaboration 8: comparison of De Bruijn, non-shadowing and fresh variable conventions](https://youtu.be/ZKu1oNSbZ9I).
* Jon Sterling. [Jon Sterling, How to code your own type theory](https://youtu.be/DEj-_k2Nx6o).

## Issues

- [`MC-159633`](https://bugs.mojang.com/browse/MC-159633) - Command feedback messages are unnecessarily created during function execution
- [`MC-163943`](https://bugs.mojang.com/browse/MC-163943) - Read-only scores can be mutated by swapping
- [`MC-171881`](https://bugs.mojang.com/browse/MC-171881) - Negative zero cannot be represented in NBT
- [`MC-173120`](https://bugs.mojang.com/browse/MC-173120) - Lack of escape sequences of newlines prevents quoted strings from containing newlines in a function
- [`MC-174587`](https://bugs.mojang.com/browse/MC-174587) - Greedy strings cannot contain trailing spaces in a function
- [`MC-178997`](https://bugs.mojang.com/browse/MC-178997) - Storing a tag with a mismatched type in a numeric array tag succeeds even though its elements are not changed
- [`MC-179181`](https://bugs.mojang.com/browse/MC-179181) - Match element nodes in NBT paths throw an unexpected UnsupportedOperationException against non-compound lists
- [`MC-182368`](https://bugs.mojang.com/browse/MC-182368) - Inconsistent constant results of commands
- [`MC-184512`](https://bugs.mojang.com/browse/MC-184512) - commands.json does not include redirect paths to the root node
- [`MC-196890`](https://bugs.mojang.com/browse/MC-196890) - "/data merge" deep-copies target compound tag
- ~~[`MC-201769`](https://bugs.mojang.com/browse/MC-201769)~~ - Copying deeply nested NBT causes StackOverflowError
- [`MC-208005`](https://bugs.mojang.com/browse/MC-208005) - Reference equality of NaNs is exposed
- [`MC-208974`](https://bugs.mojang.com/browse/MC-208974) - Target NBT is changed despite being reported as unchanged
- ~~[`MC-221421`](https://bugs.mojang.com/browse/MC-221421)~~ - A list tag can be modified during insertion into itself
- [`MC-225425`](https://bugs.mojang.com/browse/MC-225425) - "/data modify" inserts source elements into target collection one by one
- [`MC-227538`](https://bugs.mojang.com/browse/MC-227538) - Inserting elements into collection tags can be aborted in the middle
- ~~[`MC-231408`](https://bugs.mojang.com/browse/MC-231408)~~ - A function tag collapses the same values
- [`MC-248261`](https://bugs.mojang.com/browse/MC-248261) - "/execute store" makes the storage file dirty without any modifications
- ~~[`MC-248769`](https://bugs.mojang.com/browse/MC-248769)~~ - "/data remove" always traverses the NBT path to the end
- [`MC-254353`](https://bugs.mojang.com/browse/MC-254353) - A packet to remove the score is always broadcast when a score is reset
- ~~[`MC-258195`](https://bugs.mojang.com/browse/MC-258195)~~ - Performance degradation of NBT modification
- [`MC-258479`](https://bugs.mojang.com/browse/MC-258479) - NBT depth limit can be exceeded by "set_nbt"
- ~~[`MC-259282`](https://bugs.mojang.com/browse/MC-259282)~~ - NBT strings that exceed the length limit can be created
- ~~[`MC-259563`](https://bugs.mojang.com/browse/MC-259563)~~ - Command exception messages are unnecessarily created and stringified during function execution
- [`MC-261376`](https://bugs.mojang.com/browse/MC-261376) - Elements in a numeric array tag at the deepest level can be retrieved, but not be set
