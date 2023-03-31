# References

## Papers

### 2008

* Danielsson, N.A., & Norell, U. **Parsing Mixfix Operators**. IFL.

### 2009

* Dragos, I., & Odersky, M. **Compiling generics through user-directed type specialization**. ICOOOLPS@ECOOP.

### 2010

* Oliveira, B.C., Moors, A., & Odersky, M. **Type classes as objects and implicits**. Proceedings of the ACM international conference on Object oriented programming systems languages and applications.

### 2011

* Jones, R.E., Hosking, A.L., & Moss, J.E. **The Garbage Collection Handbook: The art of automatic memory management**. Chapman and Hall / CRC Applied Algorithms and Data Structures Series.

### 2013

* Petříček, T., Orchard, D.A., & Mycroft, A. **Coeffects: Unified Static Analysis of Context-Dependence**. ICALP.

### 2014

* Petříček, T., Orchard, D.A., & Mycroft, A. **Coeffects: a calculus of context-dependent computation**. ICFP.

### 2017

* Eisenberg, R.A., & Jones, S.L. **Levity polymorphism**. Proceedings of the 38th ACM SIGPLAN Conference on Programming Language Design and Implementation.

### 2020

* Downen, P., Ariola, Z.M., Jones, S.L., & Eisenberg, R.A. **Kinds are calling conventions**. Proceedings of the ACM on Programming Languages, 4, 1 - 29.
* Wood, D.A. **Polymorphisation: Improving Rust compilation times through intelligent monomorphisation**.
* Willsey, M., Nandi, C., Wang, Y.R., Flatt, O., Tatlock, Z., & Panchekha, P. **egg: Fast and extensible equality saturation**. Proceedings of the ACM on Programming Languages, 5, 1 - 29.
* Mokhov, A., Mitchell, N., & Peyton Jones, S. **Build systems à la carte: Theory and practice**. Journal of Functional Programming, 30.

### 2021

* Huang, X., & Oliveira, B.C. **Distributing intersection and union types with splits and duality (functional pearl)**. Proceedings of the ACM on Programming Languages, 5, 1 - 24.
* Dunfield, J., & Krishnaswami, N.R. **Bidirectional Typing**. ACM Computing Surveys (CSUR), 54, 1 - 38.
* Tanabe, Y., Lubis, L.A., Aotani, T., & Masuhara, H. **A Functional Programming Language with Versions**. Art Sci. Eng. Program., 6, 5.

### 2022

* Brachthäuser, J.I., Schuster, P., Lee, E., & Boruch-Gruszecki, A. **Effects, capabilities, and boxes: from scope-based reasoning to type-based reasoning and back**. Proceedings of the ACM on Programming Languages, 6, 1 - 30.
* Kovács, A. **Staged compilation with two-level type theory**. Proceedings of the ACM on Programming Languages, 6, 540 - 569.
* Marshall, D., Vollmer, M., & Orchard, D.A. **Linearity and Uniqueness: An Entente Cordiale**. ESOP.
* Odersky, M., Boruch-Gruszecki, A., Lee, E., Brachthäuser, J.I., & Lhoták, O. **Scoped Capabilities for Polymorphic Effects**. ArXiv, abs/2207.03402.
* Rehman, B., Huang, X., Xie, N., & Oliveira, B.C. **Union Types with Disjoint Switches**. ECOOP.
* Madsen, M. **The Principles of the Flix Programming Language**. Proceedings of the 2022 ACM SIGPLAN International Symposium on New Ideas, New Paradigms, and Reflections on Programming and Software.
* Gratzer, D., Sterling, J., Angiuli, C., Coquand, T., & Birkedal, L. **Controlling unfolding in type theory**. ArXiv, abs/2210.05420.
* Tanabe, Y., Lubis, L.A., Aotani, T., & Masuhara, H. **A Step toward Programming with Versions in Real-World Functional Languages**. Proceedings of the 14th ACM International Workshop on Context-Oriented Programming and Advanced Modularity.
* Lubis, L.A., Tanabe, Y., Aotani, T., & Masuhara, H. **BatakJava: An Object-Oriented Programming Language with Versions**. Proceedings of the 15th ACM SIGPLAN International Conference on Software Language Engineering.

###

* Lionel Parreaux. **The Ultimate Conditional Syntax**.
* Jonathan Sterling, Robert Harper. **A metalanguage for multi-phase modularity**.

## Repositories

* [AndrasKovacs/elaboration-zoo](https://github.com/AndrasKovacs/elaboration-zoo)
* [AndrasKovacs/staged](https://github.com/AndrasKovacs/staged)
* [JetBrains/kotlin](https://github.com/JetBrains/kotlin)
* [Mojang/DataFixerUpper](https://github.com/Mojang/DataFixerUpper)
* [Mojang/brigadier](https://github.com/Mojang/brigadier)
* [egraphs-good/egg](https://github.com/egraphs-good/egg)
* [rust-lang/rust](https://github.com/rust-lang/rust)
* [snowleopard/build](https://github.com/snowleopard/build)

## Videos

* András Kovács. [Type theory elaboration 1: bidirectional type checking](https://youtu.be/_K5Yt-cmKcY).
* András Kovács. [Type theory elaboration 8: comparison of De Bruijn, non-shadowing and fresh variable conventions](https://youtu.be/ZKu1oNSbZ9I).
* Jon Sterling. [Jon Sterling, How to code your own type theory](https://youtu.be/DEj-_k2Nx6o).

## Articles

* [Major & Minor Versions in Minecraft: Java Edition | Minecraft Help](https://help.minecraft.net/hc/en-us/articles/9971900758413-Major-Minor-Versions-in-Minecraft-Java-Edition)
* [Query-based compiler architectures | Olle Fredriksson's blog](https://ollef.github.io/blog/posts/query-based-compilers.html)
* [Queries: demand-driven compilation - Rust Compiler Development Guide](https://rustc-dev-guide.rust-lang.org/query.html)
* [Monomorphization - Rust Compiler Development Guide](https://rustc-dev-guide.rust-lang.org/backend/monomorph.html)
* [type telescope in nLab](https://ncatlab.org/nlab/show/type+telescope)

## Issues

* [![MC-116388 Fixed](https://img.shields.io/badge/MC--116388-Fixed-brightgreen)](https://bugs.mojang.com/browse/MC-116388)
  Chain command blocks can clone themselves to create an infinite loop which freezes the server
* [![MC-124444 Won't Fix](https://img.shields.io/badge/MC--124444-Won't%20Fix-orange)](https://bugs.mojang.com/browse/MC-124444)
  Gamerule maxCommandChainLength allows 0 and negative integer values
* [![MC-124446 Won't Fix](https://img.shields.io/badge/MC--124446-Won't%20Fix-orange)](https://bugs.mojang.com/browse/MC-124446)
  No warning message when function is terminated because of maxCommandChainLength gamerule
* [![MC-124447 Works As Intended](https://img.shields.io/badge/MC--124447-Works%20As%20Intended-yellow)](https://bugs.mojang.com/browse/MC-124447)
  Modifying maxCommandChainLength inside of function or command blocks does not affect currently running chain
* [![MC-143266 Unresolved](https://img.shields.io/badge/MC--143266-Unresolved-red)](https://bugs.mojang.com/browse/MC-143266)
  Nested function calls reevaluate maxCommandChainLength before queueing commands
* [![MC-143269 Unresolved](https://img.shields.io/badge/MC--143269-Unresolved-red)](https://bugs.mojang.com/browse/MC-143269)
  Nested intermediate functions are skipped when maxCommandChainLength commands are already queued
* [![MC-159633 Unresolved](https://img.shields.io/badge/MC--159633-Unresolved-red)](https://bugs.mojang.com/browse/MC-159633)
  Command feedback messages are unnecessarily created during function execution
* [![MC-163943 Unresolved](https://img.shields.io/badge/MC--163943-Unresolved-red)](https://bugs.mojang.com/browse/MC-163943)
  Read-only scores can be mutated by swapping
* [![MC-171881 Unresolved](https://img.shields.io/badge/MC--171881-Unresolved-red)](https://bugs.mojang.com/browse/MC-171881)
  Negative zero cannot be represented in NBT
* [![MC-173120 Unresolved](https://img.shields.io/badge/MC--173120-Unresolved-red)](https://bugs.mojang.com/browse/MC-173120)
  Lack of escape sequences of newlines prevents quoted strings from containing newlines in a function
* [![MC-174587 Unresolved](https://img.shields.io/badge/MC--174587-Unresolved-red)](https://bugs.mojang.com/browse/MC-174587)
  Greedy strings cannot contain trailing spaces in a function
* [![MC-178997 Unresolved](https://img.shields.io/badge/MC--178997-Unresolved-red)](https://bugs.mojang.com/browse/MC-178997)
  Storing a tag with a mismatched type in a numeric array tag succeeds even though its elements are not changed
* [![MC-179181 Unresolved](https://img.shields.io/badge/MC--179181-Unresolved-red)](https://bugs.mojang.com/browse/MC-179181)
  Match element nodes in NBT paths throw an unexpected UnsupportedOperationException against non-compound lists
* [![MC-182368 Unresolved](https://img.shields.io/badge/MC--182368-Unresolved-red)](https://bugs.mojang.com/browse/MC-182368)
  Inconsistent constant results of commands
* [![MC-184512 Unresolved](https://img.shields.io/badge/MC--184512-Unresolved-red)](https://bugs.mojang.com/browse/MC-184512)
  commands.json does not include redirect paths to the root node
* [![MC-196890 Unresolved](https://img.shields.io/badge/MC--196890-Unresolved-red)](https://bugs.mojang.com/browse/MC-196890)
  "/data merge" deep-copies target compound tag
* [![MC-201769 Fixed](https://img.shields.io/badge/MC--201769-Fixed-brightgreen)](https://bugs.mojang.com/browse/MC-201769)
  Copying deeply nested NBT causes StackOverflowError
* [![MC-208005 Unresolved](https://img.shields.io/badge/MC--208005-Unresolved-red)](https://bugs.mojang.com/browse/MC-208005)
  Reference equality of NaNs is exposed
* [![MC-208974 Unresolved](https://img.shields.io/badge/MC--208974-Unresolved-red)](https://bugs.mojang.com/browse/MC-208974)
  Target NBT is changed despite being reported as unchanged
* [![MC-221421 Fixed](https://img.shields.io/badge/MC--221421-Fixed-brightgreen)](https://bugs.mojang.com/browse/MC-221421)
  A list tag can be modified during insertion into itself
* [![MC-225425 Unresolved](https://img.shields.io/badge/MC--225425-Unresolved-red)](https://bugs.mojang.com/browse/MC-225425)
  "/data modify" inserts source elements into target collection one by one
* [![MC-227538 Unresolved](https://img.shields.io/badge/MC--227538-Unresolved-red)](https://bugs.mojang.com/browse/MC-227538)
  Inserting elements into collection tags can be aborted in the middle
* [![MC-231408 Works As Intended](https://img.shields.io/badge/MC--231408-Works%20As%20Intended-yellow)](https://bugs.mojang.com/browse/MC-231408)
  A function tag collapses the same values
* [![MC-248261 Unresolved](https://img.shields.io/badge/MC--248261-Unresolved-red)](https://bugs.mojang.com/browse/MC-248261)
  "/execute store" makes the storage file dirty without any modifications
* [![MC-248769 Invalid](https://img.shields.io/badge/MC--248769-Invalid-lightgray)](https://bugs.mojang.com/browse/MC-248769)
  "/data remove" always traverses the NBT path to the end
* [![MC-254353 Unresolved](https://img.shields.io/badge/MC--254353-Unresolved-red)](https://bugs.mojang.com/browse/MC-254353)
  A packet to remove the score is always broadcast when a score is reset
* [![MC-258195 Fixed](https://img.shields.io/badge/MC--258195-Fixed-brightgreen)](https://bugs.mojang.com/browse/MC-258195)
  Performance degradation of NBT modification
* [![MC-258479 Unresolved](https://img.shields.io/badge/MC--258479-Unresolved-red)](https://bugs.mojang.com/browse/MC-258479)
  NBT depth limit can be exceeded by "set_nbt"
* [![MC-259282 Won't Fix](https://img.shields.io/badge/MC--259282-Won't%20Fix-orange)](https://bugs.mojang.com/browse/MC-259282)
  NBT strings that exceed the length limit can be created
* [![MC-259563 Invalid](https://img.shields.io/badge/MC--259563-Invalid-lightgray)](https://bugs.mojang.com/browse/MC-259563)
  Command exception messages are unnecessarily created and stringified during function execution
* [![MC-261376 Unresolved](https://img.shields.io/badge/MC--261376-Unresolved-red)](https://bugs.mojang.com/browse/MC-261376)
  Elements in a numeric array tag at the deepest level can be retrieved, but not be set

## Quotes

⚠️ These quotes may not represent official views.

### <svg width="1em" height="1em" role="img" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><title>Discord</title><path fill="#5865F2" d="M20.317 4.3698a19.7913 19.7913 0 00-4.8851-1.5152.0741.0741 0 00-.0785.0371c-.211.3753-.4447.8648-.6083 1.2495-1.8447-.2762-3.68-.2762-5.4868 0-.1636-.3933-.4058-.8742-.6177-1.2495a.077.077 0 00-.0785-.037 19.7363 19.7363 0 00-4.8852 1.515.0699.0699 0 00-.0321.0277C.5334 9.0458-.319 13.5799.0992 18.0578a.0824.0824 0 00.0312.0561c2.0528 1.5076 4.0413 2.4228 5.9929 3.0294a.0777.0777 0 00.0842-.0276c.4616-.6304.8731-1.2952 1.226-1.9942a.076.076 0 00-.0416-.1057c-.6528-.2476-1.2743-.5495-1.8722-.8923a.077.077 0 01-.0076-.1277c.1258-.0943.2517-.1923.3718-.2914a.0743.0743 0 01.0776-.0105c3.9278 1.7933 8.18 1.7933 12.0614 0a.0739.0739 0 01.0785.0095c.1202.099.246.1981.3728.2924a.077.077 0 01-.0066.1276 12.2986 12.2986 0 01-1.873.8914.0766.0766 0 00-.0407.1067c.3604.698.7719 1.3628 1.225 1.9932a.076.076 0 00.0842.0286c1.961-.6067 3.9495-1.5219 6.0023-3.0294a.077.077 0 00.0313-.0552c.5004-5.177-.8382-9.6739-3.5485-13.6604a.061.061 0 00-.0312-.0286zM8.02 15.3312c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9555-2.4189 2.157-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.9555 2.4189-2.1569 2.4189zm7.9748 0c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9554-2.4189 2.1569-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.946 2.4189-2.1568 2.4189Z"/></svg> boq — 2019/09/19

> [Oh, also: our plan with this is to make you stop using nbt](https://discord.com/channels/154777837382008833/593812273164976166/623949632812089344)

> [I meant for querying, since nbt is still somehow ok as generic storage format. But `data entity`, `data block` and `@e[nbt=]` are filthy hack](https://discord.com/channels/154777837382008833/593812273164976166/623950417625350157)

> [No. NBT sharing was a bug and will not return.](https://discord.com/channels/154777837382008833/593812273164976166/623950625960361995)

> [NBT matching is a hack. Nothing should work on serialized data, all that should be exposed properly. But It may take long time](https://discord.com/channels/154777837382008833/593812273164976166/623973886702256168)

### <svg width="1em" height="1em" role="img" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><title>Discord</title><path fill="#5865F2" d="M20.317 4.3698a19.7913 19.7913 0 00-4.8851-1.5152.0741.0741 0 00-.0785.0371c-.211.3753-.4447.8648-.6083 1.2495-1.8447-.2762-3.68-.2762-5.4868 0-.1636-.3933-.4058-.8742-.6177-1.2495a.077.077 0 00-.0785-.037 19.7363 19.7363 0 00-4.8852 1.515.0699.0699 0 00-.0321.0277C.5334 9.0458-.319 13.5799.0992 18.0578a.0824.0824 0 00.0312.0561c2.0528 1.5076 4.0413 2.4228 5.9929 3.0294a.0777.0777 0 00.0842-.0276c.4616-.6304.8731-1.2952 1.226-1.9942a.076.076 0 00-.0416-.1057c-.6528-.2476-1.2743-.5495-1.8722-.8923a.077.077 0 01-.0076-.1277c.1258-.0943.2517-.1923.3718-.2914a.0743.0743 0 01.0776-.0105c3.9278 1.7933 8.18 1.7933 12.0614 0a.0739.0739 0 01.0785.0095c.1202.099.246.1981.3728.2924a.077.077 0 01-.0066.1276 12.2986 12.2986 0 01-1.873.8914.0766.0766 0 00-.0407.1067c.3604.698.7719 1.3628 1.225 1.9932a.076.076 0 00.0842.0286c1.961-.6067 3.9495-1.5219 6.0023-3.0294a.077.077 0 00.0313-.0552c.5004-5.177-.8382-9.6739-3.5485-13.6604a.061.061 0 00-.0312-.0286zM8.02 15.3312c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9555-2.4189 2.157-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.9555 2.4189-2.1569 2.4189zm7.9748 0c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9554-2.4189 2.1569-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.946 2.4189-2.1568 2.4189Z"/></svg> boq — 2020/04/20

> [NBT should not be exposed to user](https://discord.com/channels/154777837382008833/593812273164976166/701543553675034695)

### <svg width="1em" height="1em" role="img" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><title>Discord</title><path fill="#5865F2" d="M20.317 4.3698a19.7913 19.7913 0 00-4.8851-1.5152.0741.0741 0 00-.0785.0371c-.211.3753-.4447.8648-.6083 1.2495-1.8447-.2762-3.68-.2762-5.4868 0-.1636-.3933-.4058-.8742-.6177-1.2495a.077.077 0 00-.0785-.037 19.7363 19.7363 0 00-4.8852 1.515.0699.0699 0 00-.0321.0277C.5334 9.0458-.319 13.5799.0992 18.0578a.0824.0824 0 00.0312.0561c2.0528 1.5076 4.0413 2.4228 5.9929 3.0294a.0777.0777 0 00.0842-.0276c.4616-.6304.8731-1.2952 1.226-1.9942a.076.076 0 00-.0416-.1057c-.6528-.2476-1.2743-.5495-1.8722-.8923a.077.077 0 01-.0076-.1277c.1258-.0943.2517-.1923.3718-.2914a.0743.0743 0 01.0776-.0105c3.9278 1.7933 8.18 1.7933 12.0614 0a.0739.0739 0 01.0785.0095c.1202.099.246.1981.3728.2924a.077.077 0 01-.0066.1276 12.2986 12.2986 0 01-1.873.8914.0766.0766 0 00-.0407.1067c.3604.698.7719 1.3628 1.225 1.9932a.076.076 0 00.0842.0286c1.961-.6067 3.9495-1.5219 6.0023-3.0294a.077.077 0 00.0313-.0552c.5004-5.177-.8382-9.6739-3.5485-13.6604a.061.061 0 00-.0312-.0286zM8.02 15.3312c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9555-2.4189 2.157-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.9555 2.4189-2.1569 2.4189zm7.9748 0c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9554-2.4189 2.1569-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.946 2.4189-2.1568 2.4189Z"/></svg> boq — 2020/11/12

> [storage is for NBT, inventories are for items](https://discord.com/channels/154777837382008833/593812273164976166/776177804294881310)

> [Remember, NBT manipulation bad!](https://discord.com/channels/154777837382008833/593812273164976166/776178079239372823)

> [by "NBT manipulation" I mean "manipulation of in-game objects via NBT". Using that as generic data structure is still fine](https://discord.com/channels/154777837382008833/593812273164976166/776178452268580864)

### <svg width="1em" height="1em" role="img" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><title>Twitter</title><path fill="#1DA1F2" d="M23.953 4.57a10 10 0 01-2.825.775 4.958 4.958 0 002.163-2.723c-.951.555-2.005.959-3.127 1.184a4.92 4.92 0 00-8.384 4.482C7.69 8.095 4.067 6.13 1.64 3.162a4.822 4.822 0 00-.666 2.475c0 1.71.87 3.213 2.188 4.096a4.904 4.904 0 01-2.228-.616v.06a4.923 4.923 0 003.946 4.827 4.996 4.996 0 01-2.212.085 4.936 4.936 0 004.604 3.417 9.867 9.867 0 01-6.102 2.105c-.39 0-.779-.023-1.17-.067a13.995 13.995 0 007.557 2.209c9.053 0 13.998-7.496 13.998-13.985 0-.21 0-.42-.015-.63A9.935 9.935 0 0024 4.59z"/></svg> slicedlime — 2022/11/10

> [I’ve seen some comments about the pack formats in the snapshots. Let’s have a 🧵about pack versions and formats.](https://twitter.com/slicedlime/status/1590684972633190400)
>
> [With the new more frequent minor releases we’re also switching the way we bump pack formats.](https://twitter.com/slicedlime/status/1590684975388819462)
>
> [Previously, we would bump at most once per snapshot cycle, leading to it being impossible to know which packs work for which snapshots - and that packs would inevitably become incompatible, including the built-in packs.](https://twitter.com/slicedlime/status/1590684978207395843)
>
> [Now we will instead keep bumping the pack version whenever breaking changes are introduced. This will lead to more frequent but smaller differences to the format, and pack versions that “skip” several numbers between full releases.](https://twitter.com/slicedlime/status/1590684981554479105)
>
> [Nothing really changes with how to update packs - you can choose to skip to the next release version if you want.](https://twitter.com/slicedlime/status/1590684984414994432)
>
> [However, if you do individual updates for snapshots, this new scheme means it’ll be easier to keep track of what needs to be done for release and if anyone is testing your packs, they will have an easier time knowing what is compatible.](https://twitter.com/slicedlime/status/1590684987153866752)

### <svg width="1em" height="1em" role="img" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><title>Discord</title><path fill="#5865F2" d="M20.317 4.3698a19.7913 19.7913 0 00-4.8851-1.5152.0741.0741 0 00-.0785.0371c-.211.3753-.4447.8648-.6083 1.2495-1.8447-.2762-3.68-.2762-5.4868 0-.1636-.3933-.4058-.8742-.6177-1.2495a.077.077 0 00-.0785-.037 19.7363 19.7363 0 00-4.8852 1.515.0699.0699 0 00-.0321.0277C.5334 9.0458-.319 13.5799.0992 18.0578a.0824.0824 0 00.0312.0561c2.0528 1.5076 4.0413 2.4228 5.9929 3.0294a.0777.0777 0 00.0842-.0276c.4616-.6304.8731-1.2952 1.226-1.9942a.076.076 0 00-.0416-.1057c-.6528-.2476-1.2743-.5495-1.8722-.8923a.077.077 0 01-.0076-.1277c.1258-.0943.2517-.1923.3718-.2914a.0743.0743 0 01.0776-.0105c3.9278 1.7933 8.18 1.7933 12.0614 0a.0739.0739 0 01.0785.0095c.1202.099.246.1981.3728.2924a.077.077 0 01-.0066.1276 12.2986 12.2986 0 01-1.873.8914.0766.0766 0 00-.0407.1067c.3604.698.7719 1.3628 1.225 1.9932a.076.076 0 00.0842.0286c1.961-.6067 3.9495-1.5219 6.0023-3.0294a.077.077 0 00.0313-.0552c.5004-5.177-.8382-9.6739-3.5485-13.6604a.061.061 0 00-.0312-.0286zM8.02 15.3312c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9555-2.4189 2.157-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.9555 2.4189-2.1569 2.4189zm7.9748 0c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9554-2.4189 2.1569-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.946 2.4189-2.1568 2.4189Z"/></svg> boq — 2022/01/19

> [personally? I just don't want feeding command blocks generated commands to become meta](https://discord.com/channels/154777837382008833/593812273164976166/1065341844403257365)

> [A/B problem. We see: command blocks as legacy feature. You see: oh, I can do argument substitution now and will use it everywhere!](https://discord.com/channels/154777837382008833/593812273164976166/1065342292199735368)

> [I don't want to receive report that changing time format in command block output breaks your Lua interpreter done in commands](https://discord.com/channels/154777837382008833/593812273164976166/1065343358807052388)

> [See? That's what I'm talking about. It's an kludge that obfuscated original intent.
> We never intended commands to be insane mess of tricks that's impossible to be read](https://discord.com/channels/154777837382008833/593812273164976166/1065343981266927617)

> [We want commands to be accessible. Endorsing kludges is not the way to do it](https://discord.com/channels/154777837382008833/593812273164976166/1065344316333109289)

> [If you want to do vector math, that should be possible directly. Without implementing half of IEEE 754 with integers.
Asking for binary operators because it makes IEEE 754 implementation easier is, IMO, wrong mindset](https://discord.com/channels/154777837382008833/593812273164976166/1065345022112841790)

> [What I'm trying to get it that you are already thinking with scoreboards as solution for everything.
> You are not thinking about "feature X", you are automatically assuming that what will you get is "feature X for scoreboards"](https://discord.com/channels/154777837382008833/593812273164976166/1065346170504880148)

### <svg width="1em" height="1em" role="img" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><title>Discord</title><path fill="#5865F2" d="M20.317 4.3698a19.7913 19.7913 0 00-4.8851-1.5152.0741.0741 0 00-.0785.0371c-.211.3753-.4447.8648-.6083 1.2495-1.8447-.2762-3.68-.2762-5.4868 0-.1636-.3933-.4058-.8742-.6177-1.2495a.077.077 0 00-.0785-.037 19.7363 19.7363 0 00-4.8852 1.515.0699.0699 0 00-.0321.0277C.5334 9.0458-.319 13.5799.0992 18.0578a.0824.0824 0 00.0312.0561c2.0528 1.5076 4.0413 2.4228 5.9929 3.0294a.0777.0777 0 00.0842-.0276c.4616-.6304.8731-1.2952 1.226-1.9942a.076.076 0 00-.0416-.1057c-.6528-.2476-1.2743-.5495-1.8722-.8923a.077.077 0 01-.0076-.1277c.1258-.0943.2517-.1923.3718-.2914a.0743.0743 0 01.0776-.0105c3.9278 1.7933 8.18 1.7933 12.0614 0a.0739.0739 0 01.0785.0095c.1202.099.246.1981.3728.2924a.077.077 0 01-.0066.1276 12.2986 12.2986 0 01-1.873.8914.0766.0766 0 00-.0407.1067c.3604.698.7719 1.3628 1.225 1.9932a.076.076 0 00.0842.0286c1.961-.6067 3.9495-1.5219 6.0023-3.0294a.077.077 0 00.0313-.0552c.5004-5.177-.8382-9.6739-3.5485-13.6604a.061.061 0 00-.0312-.0286zM8.02 15.3312c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9555-2.4189 2.157-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.9555 2.4189-2.1569 2.4189zm7.9748 0c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9554-2.4189 2.1569-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.946 2.4189-2.1568 2.4189Z"/></svg> slicedlime — 2023/02/16

> [Nothing is impossible, but here's the situation: with very limited time, we're trying to provide as good map making functionality as possible that isn't just a hijack of some gameplay entity like armor stands. Given lots of time to focus on this stuff, we could make better things. But this is a lot better than nothing, and we'll try to make it as good as we can with the time we have.](https://discord.com/channels/154777837382008833/593812273164976166/1075462092385161348)

> [&lt;insert value here&gt; in various commands are an obvious gap right now. We have some thoughts about it, but not this version.](https://discord.com/channels/154777837382008833/593812273164976166/1075467884135985226)
