! :folding=indent:collapseFolds=1:

! $Id$
!
! Copyright (C) 2003, 2004 Slava Pestov.
! 
! Redistribution and use in source and binary forms, with or without
! modification, are permitted provided that the following conditions are met:
! 
! 1. Redistributions of source code must retain the above copyright notice,
!    this list of conditions and the following disclaimer.
! 
! 2. Redistributions in binary form must reproduce the above copyright notice,
!    this list of conditions and the following disclaimer in the documentation
!    and/or other materials provided with the distribution.
! 
! THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES,
! INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
! FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
! DEVELOPERS AND CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
! SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
! PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
! OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
! WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
! OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
! ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

IN: streams
DEFER: <extend-stream>

IN: stdio
USE: combinators
USE: errors
USE: kernel
USE: lists
USE: namespaces
USE: stack
USE: streams

: flush ( -- )
    "stdio" get fflush ;

: read ( -- string )
    "stdio" get freadln ;

: read1 ( count -- string )
    "stdio" get fread1 ;

: read# ( count -- string )
    "stdio" get fread# ;

: write ( string -- )
    "stdio" get fwrite ;

: write-attr ( string style -- )
    #! Write an attributed string to standard output.
    "stdio" get fwrite-attr ;

: write-icon ( resource -- )
    #! Write an icon. Eg, /library/icons/File.png
    "icon" swons unit "" swap write-attr ;

: print ( string -- )
    "stdio" get fprint ;

: edit ( string -- )
    "stdio" get fedit ;

: terpri ( -- )
    #! Print a newline to standard output.
    "\n" write ;

: close ( -- )
    "stdio" get fclose ;

: with-stream ( stream quot -- )
    [ swap "stdio" set  [ close rethrow ] catch ] with-scope ;

: with-string ( quot -- str )
    #! Execute a quotation, and push a string containing all
    #! text printed by the quotation.
    1024 <string-output-stream> [
        call "stdio" get stream>str
    ] with-stream ;

: <stdio-stream> ( stream -- stream )
    #! We disable fclose on stdio so that various tricks like
    #! with-stream can work.
    <extend-stream> [
        ( string -- )
        [ write "\n" write flush ] "fprint" set

        [ ] "fclose" set
    ] extend ;
