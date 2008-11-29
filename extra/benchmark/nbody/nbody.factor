! Copyright (C) 2008 Slava Pestov.
! See http://factorcode.org/license.txt for BSD license.
USING: accessors float-arrays fry kernel locals make math
math.constants math.functions math.vectors prettyprint
sequences hints arrays ;
IN: benchmark.nbody

: solar-mass 4 pi sq * ; inline
: days-per-year 365.24 ; inline

TUPLE: body
{ location float-array }
{ velocity float-array }
{ mass float } ;

: <body> ( -- body ) body new ; inline

: <jupiter> ( -- body )
    <body>
        F{ 4.84143144246472090e+00 -1.16032004402742839e+00 -1.03622044471123109e-01 } >>location
        F{ 1.66007664274403694e-03 7.69901118419740425e-03 -6.90460016972063023e-05 } days-per-year v*n >>velocity
        9.54791938424326609e-04 solar-mass * >>mass ;

: <saturn> ( -- body )
    <body>
        F{ 8.34336671824457987e+00 4.12479856412430479e+00 -4.03523417114321381e-01 } >>location
        F{ -2.76742510726862411e-03 4.99852801234917238e-03 2.30417297573763929e-05 } days-per-year v*n >>velocity
        2.85885980666130812e-04 solar-mass * >>mass ;

: <uranus> ( -- body )
    <body>
        F{ 1.28943695621391310e+01 -1.51111514016986312e+01 -2.23307578892655734e-01 } >>location
        F{ 2.96460137564761618e-03 2.37847173959480950e-03 -2.96589568540237556e-05 } days-per-year v*n >>velocity
        4.36624404335156298e-05 solar-mass * >>mass ;

: <neptune> ( -- body )
    <body>
        F{ 1.53796971148509165e+01 -2.59193146099879641e+01 1.79258772950371181e-01 } >>location
        F{ 2.68067772490389322e-03 1.62824170038242295e-03 -9.51592254519715870e-05 } days-per-year v*n >>velocity
        5.15138902046611451e-05 solar-mass * >>mass ;

: <sun> ( -- body )
    <body>
        solar-mass >>mass
        F{ 0 0 0 } >>location
        F{ 0 0 0 } >>velocity ;
    
: offset-momentum ( body offset -- body )
    vneg solar-mass v/n >>velocity ; inline

TUPLE: nbody-system { bodies array read-only } ;

: init-bodies ( bodies -- )
    [ first ] [ F{ 0 0 0 } [ [ velocity>> ] [ mass>> ] bi v*n v+ ] reduce ] bi
    offset-momentum drop ; inline

: <nbody-system> ( -- system )
    [ <sun> , <jupiter> , <saturn> , <uranus> , <neptune> , ] { } make nbody-system boa
    dup bodies>> init-bodies ; inline

:: each-pair ( bodies pair-quot: ( other-body body -- ) each-quot: ( body -- ) -- )
    bodies [| body i |
        body each-quot call
        bodies i 1+ tail-slice [
            body pair-quot call
        ] each
    ] each-index ; inline

: update-position ( body dt -- )
    [ dup velocity>> ] dip '[ _ _ v*n v+ ] change-location drop ;

: mag ( dt body other-body -- mag d )
    [ location>> ] bi@ v- [ norm-sq dup sqrt * / ] keep ; inline

:: update-velocity ( other-body body dt -- )
    dt body other-body mag
    [ [ body ] 2dip '[ other-body mass>> _ * _ n*v v- ] change-velocity drop ]
    [ [ other-body ] 2dip '[ body mass>> _ * _ n*v v+ ] change-velocity drop ] 2bi ;

: advance ( system dt -- )
    [ bodies>> ] dip
    [ '[ _ update-velocity ] [ drop ] each-pair ]
    [ '[ _ update-position ] each ]
    2bi ; inline

: inertia ( body -- e )
    [ mass>> ] [ velocity>> norm-sq ] bi * 0.5 * ;

: newton's-law ( other-body body -- e )
    [ [ mass>> ] bi@ * ] [ [ location>> ] bi@ distance ] 2bi / ;

: energy ( system -- x )
    [ 0.0 ] dip bodies>> [ newton's-law - ] [ inertia + ] each-pair ; inline

: nbody ( n -- )
    <nbody-system>
    [ energy . ] [ '[ _ 0.01 advance ] times ] [ energy . ] tri ;

HINTS: update-position body float ;
HINTS: update-velocity body body float ;
HINTS: inertia body ;
HINTS: newton's-law body body ;
HINTS: nbody fixnum ;

: nbody-main ( -- )
    1000000 nbody ;
