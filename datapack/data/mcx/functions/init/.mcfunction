scoreboard objectives add param dummy
scoreboard objectives add local dummy

scoreboard objectives add 14 dummy
scoreboard players set $00 14 14

scoreboard objectives add 4064 dummy
scoreboard players set $00 4064 4064

execute in mcx: run function mcx:init/1
