data modify storage mcx: pos set value [0.0d, 0.0d, 0.0d]

scoreboard players operation $02 local = $00 param
scoreboard players operation $01 local = $02 local
scoreboard players operation $02 local /= $00 4064
scoreboard players operation $00 local = $02 local

scoreboard players operation $00 local /= $00 14
execute store result storage mcx: pos[0] double 1.0 run scoreboard players add $00 local 177

scoreboard players operation $01 local %= $00 4064
execute store result storage mcx: pos[1] double 1.0 run scoreboard players remove $01 local 2032

scoreboard players operation $02 local %= $00 14
execute store result storage mcx: pos[2] double 1.0 run scoreboard players add $02 local 177

data modify entity @s Pos set from storage mcx: pos
