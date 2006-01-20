! Load all contrib libs, compile them, and save a new image.
IN: scratchpad
USING: alien compiler kernel memory parser sequences words ;

{ 
    "coroutines"
    "dlists"
    "splay-trees"
} [ "contrib/" swap ".factor" append3 run-file clear ] each

{ "cairo"
  "math"
  "concurrency"
  "crypto"
  "aim"
  "httpd"
  "units"
  "sqlite"
  "win32"
  "x11"
  ! "factory" has a C component, ick.
  "postgresql"
  "parser-combinators"
  "cont-responder"
  "space-invaders"
} [ "contrib/" swap "/load.factor" append3 run-file clear ] each
