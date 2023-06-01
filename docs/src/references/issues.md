# Issues

* [![MC-116388](https://img.shields.io/badge/dynamic/json?label=MC-116388&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-116388)](https://bugs.mojang.com/browse/MC-116388)
  Chain command blocks can clone themselves to create an infinite loop which freezes the server
* [![MC-121692](https://img.shields.io/badge/dynamic/json?label=MC-121692&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-121692)](https://bugs.mojang.com/browse/MC-121692)
  Many commands break on unquoted special characters, quoting support inconsistent
* [![MC-124444](https://img.shields.io/badge/dynamic/json?label=MC-124444&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-124444)](https://bugs.mojang.com/browse/MC-124444)
  Gamerule maxCommandChainLength allows 0 and negative integer values
* [![MC-124446](https://img.shields.io/badge/dynamic/json?label=MC-124446&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-124446)](https://bugs.mojang.com/browse/MC-124446)
  No warning message when function is terminated because of maxCommandChainLength gamerule
* [![MC-124447](https://img.shields.io/badge/dynamic/json?label=MC-124447&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-124447)](https://bugs.mojang.com/browse/MC-124447)
  Modifying maxCommandChainLength inside of function or command blocks does not affect currently running chain
* [![MC-143266](https://img.shields.io/badge/dynamic/json?label=MC-143266&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-143266)](https://bugs.mojang.com/browse/MC-143266)
  Nested function calls reevaluate maxCommandChainLength before queueing commands
* [![MC-143269](https://img.shields.io/badge/dynamic/json?label=MC-143269&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-143269)](https://bugs.mojang.com/browse/MC-143269)
  Nested intermediate functions are skipped when maxCommandChainLength commands are already queued
* [![MC-159633](https://img.shields.io/badge/dynamic/json?label=MC-159633&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-159633)](https://bugs.mojang.com/browse/MC-159633)
  Command feedback messages are unnecessarily created during function execution
* [![MC-163943](https://img.shields.io/badge/dynamic/json?label=MC-163943&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-163943)](https://bugs.mojang.com/browse/MC-163943)
  Read-only scores can be mutated by swapping
* [![MC-171881](https://img.shields.io/badge/dynamic/json?label=MC-171881&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-171881)](https://bugs.mojang.com/browse/MC-171881)
  Cannot create negative zero in NBT
* [![MC-173120](https://img.shields.io/badge/dynamic/json?label=MC-173120&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-173120)](https://bugs.mojang.com/browse/MC-173120)
  Lack of escape sequences of newlines prevents quoted strings from containing newlines in a function
* [![MC-174587](https://img.shields.io/badge/dynamic/json?label=MC-174587&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-174587)](https://bugs.mojang.com/browse/MC-174587)
  Greedy strings cannot contain trailing spaces in a function
* [![MC-178997](https://img.shields.io/badge/dynamic/json?label=MC-178997&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-178997)](https://bugs.mojang.com/browse/MC-178997)
  Storing a tag with a mismatched type in a numeric array tag succeeds even though its elements are not changed
* [![MC-179181](https://img.shields.io/badge/dynamic/json?label=MC-179181&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-179181)](https://bugs.mojang.com/browse/MC-179181)
  Match element nodes in NBT paths throw an unexpected UnsupportedOperationException against non-compound lists
* [![MC-182368](https://img.shields.io/badge/dynamic/json?label=MC-182368&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-182368)](https://bugs.mojang.com/browse/MC-182368)
  Inconsistent constant results of commands
* [![MC-184512](https://img.shields.io/badge/dynamic/json?label=MC-184512&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-184512)](https://bugs.mojang.com/browse/MC-184512)
  commands.json does not include redirect paths to the root node
* [![MC-196890](https://img.shields.io/badge/dynamic/json?label=MC-196890&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-196890)](https://bugs.mojang.com/browse/MC-196890)
  "/data merge" deep-copies target compound tag
* [![MC-201769](https://img.shields.io/badge/dynamic/json?label=MC-201769&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-201769)](https://bugs.mojang.com/browse/MC-201769)
  Copying deeply nested NBT causes StackOverflowError
* [![MC-208005](https://img.shields.io/badge/dynamic/json?label=MC-208005&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-208005)](https://bugs.mojang.com/browse/MC-208005)
  Reference equality of NaNs is exposed
* [![MC-208974](https://img.shields.io/badge/dynamic/json?label=MC-208974&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-208974)](https://bugs.mojang.com/browse/MC-208974)
  Target NBT is changed despite being reported as unchanged
* [![MC-221421](https://img.shields.io/badge/dynamic/json?label=MC-221421&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-221421)](https://bugs.mojang.com/browse/MC-221421)
  A list tag can be modified during insertion into itself
* [![MC-225425](https://img.shields.io/badge/dynamic/json?label=MC-225425&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-225425)](https://bugs.mojang.com/browse/MC-225425)
  "/data modify" inserts source elements into target collection one by one
* [![MC-227538](https://img.shields.io/badge/dynamic/json?label=MC-227538&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-227538)](https://bugs.mojang.com/browse/MC-227538)
  Inserting elements into collection tags can be aborted in the middle
* [![MC-231408](https://img.shields.io/badge/dynamic/json?label=MC-231408&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-231408)](https://bugs.mojang.com/browse/MC-231408)
  A function tag collapses the same values
* [![MC-248261](https://img.shields.io/badge/dynamic/json?label=MC-248261&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-248261)](https://bugs.mojang.com/browse/MC-248261)
  "/execute store" makes the storage file dirty without any modifications
* [![MC-248769](https://img.shields.io/badge/dynamic/json?label=MC-248769&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-248769)](https://bugs.mojang.com/browse/MC-248769)
  "/data remove" always traverses the NBT path to the end
* [![MC-254353](https://img.shields.io/badge/dynamic/json?label=MC-254353&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-254353)](https://bugs.mojang.com/browse/MC-254353)
  A packet to remove the score is always broadcast when a score is reset
* [![MC-258195](https://img.shields.io/badge/dynamic/json?label=MC-258195&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-258195)](https://bugs.mojang.com/browse/MC-258195)
  Performance degradation of NBT modification
* [![MC-258479](https://img.shields.io/badge/dynamic/json?label=MC-258479&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-258479)](https://bugs.mojang.com/browse/MC-258479)
  NBT depth limit can be exceeded by "set_nbt"
* [![MC-259282](https://img.shields.io/badge/dynamic/json?label=MC-259282&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-259282)](https://bugs.mojang.com/browse/MC-259282)
  NBT strings that exceed the length limit can be created
* [![MC-259563](https://img.shields.io/badge/dynamic/json?label=MC-259563&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-259563)](https://bugs.mojang.com/browse/MC-259563)
  Command exception messages are unnecessarily created and stringified during function execution
* [![MC-261376](https://img.shields.io/badge/dynamic/json?label=MC-261376&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-261376)](https://bugs.mojang.com/browse/MC-261376)
  Elements in a numeric array tag at the deepest level can be retrieved, but not be set
* [![MC-263058](https://img.shields.io/badge/dynamic/json?label=MC-263058&query=%24.fields.resolution.name&url=https%3A%2F%2Fbugs.mojang.com%2Frest%2Fapi%2F2%2Fissue%2FMC-263058)](https://bugs.mojang.com/browse/MC-263058)
  A newly added data storage file cannot be loaded if it was previously failed to be loaded
