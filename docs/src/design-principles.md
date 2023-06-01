# Design Principles

## Modifiers vs Annotations

* A modifier is a keyword that affects (changes or restricts) the semantics of a target
* An annotation is a keyword that adds metadata to a target without affecting its semantics

### See Also

* Madsen, M. **The Principles of the Flix Programming Language**. Proceedings of the 2022 ACM SIGPLAN International Symposium on New Ideas, New Paradigms, and Reflections on Programming and Software.
* [Modifiers vs Annotations | The Kotlin Blog](https://blog.jetbrains.com/kotlin/2015/08/modifiers-vs-annotations/)

## Least Privilege

* The most restrictive state should be the default
* Restrictions should be explicitly relaxed by modifiers

### See Also

* [rust-lang/rust](https://github.com/rust-lang/rust)

## Almost Zero Cost Abstractions

* Abstractions must be as cheap as possible

### See Also

* [AndrasKovacs/staged](https://github.com/AndrasKovacs/staged)
* [Memory layout control and staged compilation](https://youtube.com/playlist?list=PL2ZpyLROj5FNeUJh7m6IB1wTPa75JUTzL)
* [[ICFP'22] Staged Compilation with Two-Level Type Theory](https://youtu.be/0BOQE48_qOM)

## Almost Drop-in Replacement for Data Packs

* There should be as few things as possible that can be done in data packs but cannot be done in mcx
