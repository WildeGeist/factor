! Copyright (C) 2004 Chris Double.
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
!
! An httpd responder that demonstrates using XMLHttpRequest to send
! asynchronous requests back to the server.
!
IN: live-updater-responder
USE: cont-html
USE: html
USE: cont-responder
USE: cont-utils
USE: stack
USE: stdio
USE: namespaces
USE: streams
USE: parser
USE: lists
USE: errors
USE: strings
USE: logic
USE: kernel
USE: prettyprint
USE: vocabularies
USE: combinators

: get-live-updater-js* ( stream -- string )
  #! Read all lines from the stream, creating a string of the result.
  dup freadln dup [ % "\n" % get-live-updater-js* ] [ drop fclose ] ifte ;

: get-live-updater-js ( filename -- string )
  #! Return the liveUpdater javascript code as a string.
  <filecr> <% get-live-updater-js* %> ;

: write-live-anchor-tag ( text -- id )
  #! Write out the HTML for the clickable anchor. This
  #! will have no actionable HREF assigned to it. Instead
  #! an onclick is set via DHTML later to make it run a
  #! quotation on the server. The randomly generated id
  #! for the anchor is returned.
  <a id= get-random-id dup href= "#" a> [ 
    swap write
  ] </a> ;  

: register-live-anchor-quot ( div-id div-quot -- kid )
  #! Register the 'quot' with the cont-responder so
  #! that when it is run it will produce an HTML
  #! fragment which is the output generated by calling 
  #! 'quot'. That HTML fragment will be wrapped in a 
  #! 'div' with the given id.  
  <namespace> [
    "div-quot" set
    "div-id" set
  ] extend [ 
    [
      t "disable-initial-redirect?" set
      [ 
        <div id= "div-id" get div> [ "div-quot" get call ] </div>    
      ] show 
    ] bind 
  ] cons t swap register-continuation ;
  
: write-live-anchor-script ( div-id div-quot anchor-id -- )
  #! Write the javascript that will attach the onclick
  #! event handler to the anchor with the 'anchor-id'. The
  #! onclick, when clicked, will retrieve from the server
  #! the HTML generated by the output of 'div-quot' wrapped
  #! in a 'div' tag with the 'div-id'. That 'div' tag will
  #! replace whatever HTML DOM object currently has that same
  #! id.
  <script language= "JavaScript" script> [
    "document.getElementById('" write
    write
    "').onclick=liveUpdaterUri('" write
    register-live-anchor-quot write
    "');" write
  ] </script> ;
  
: live-anchor ( id quot text -- )
  #! Write out the HTML for an anchor that when clicked
  #! will replace the DOM object on the current page with
  #! the given 'id' with the result of the output of calling
  #! 'quot'. 
  write-live-anchor-tag
  write-live-anchor-script ;

: write-live-search-tag ( -- id )
  #! Write out the HTML for the input box. This
  #! will have no actionable keydown assigned to it. Instead
  #! a keydown is set via DHTML later to make it run a
  #! quotation on the server. The randomly generated id
  #! for the input box is returned.
  <input id= get-random-id dup type= "text" input/> ;

: register-live-search-quot ( div-id div-quot -- kid )
  #! Register the 'quot' with the cont-responder so
  #! that when it is run it will produce an HTML
  #! fragment which is the output generated by calling 
  #! 'quot'. That HTML fragment will be wrapped in a 
  #! 'div' with the given id. The 'quot' is called with
  #! a string on top of the stack. This is the input string
  #! entered in the live search input box.
  <namespace> [
    "div-quot" set
    "div-id" set
  ] extend [ 
    [
      t "disable-initial-redirect?" set
      #! Retrieve the search query value from the POST parameters.
      [ "s" get ] bind
      [ 
        #! Don't need the URL as the 'show' won't be resumed.
        drop
        <div id= "div-id" get div> [ "div-quot" get call ] </div>    
      ] show 
    ] bind 
  ] cons t swap register-continuation ;

: write-live-search-script ( div-id div-quot id-id -- )
  #! Write the javascript that will attach the keydown handler
  #! to the input box with the give id. Whenever a keydown is
  #! received the 'div-quot' will be executed on the server,
  #! with the input boxes text on top of the stack. The 
  #! output of the quot will be an HTML fragment, it is wrapped in
  #! a 'div' with the id 'div-id' and will 
  #! replace whatever HTML DOM object currently has that same
  #! id.
  <script language= "JavaScript" script> [
    "liveSearch('" write
    write
    "', '" write
    register-live-search-quot write
    "');" write
  ] </script> ;

: live-search ( div-id div-quot -- )
  #! Write an input text field. The keydown of this
  #! text field will run 'div-quot' on the server with
  #! the value of the text field on the stack. The output
  #! of div-quot will replace the HTML DOM object with the
  #! given id.
  write-live-search-tag
  write-live-search-script ;

: live-search-see-word ( string -- )
  #! Given a string that is a factor word, show the
  #! source to that word.
  <namespace> [
    "responder" "inspect" put
    <pre> [
        "stdio" get <html-stream> [   
          see
        ] with-stream              
    ] </pre>
  ] bind ;

: live-search-apropos-word ( string -- )
  #! Given a string that is a factor word, show the
  #! aporpos of that word.
  <namespace> [
    "responder" "inspect" put
    <pre> [
        "stdio" get <html-stream> [   
          apropos.
        ] with-stream              
    ] </pre>
  ] bind ;
      
: live-updater-responder ( -- )
  [
    drop
    <html> [
      <head> [ 
        <title> [ "Live Updater Example" write ] </title>
        <script language= "JavaScript" script> [ 
          "js/liveUpdater.js" get-live-updater-js write 
        ] </script>
      ] </head>
      <body> [
       "millis" [ millis write ] "Display Server millis" live-anchor
       <div id= "millis" div> [ 
         "The millisecond time from the server will appear here" write 
       ] </div>        
       <br/>
       "Enter a word to see:" paragraph
       "search" [ live-search-see-word ] live-search
       <br/>
       <div id= "search" div> [
         "" write
       ] </div>
       "Enter a word to apropos:" paragraph
       "apropos" [ live-search-apropos-word ] live-search
       <br/>
       <div id= "apropos" div> [
         "" write
       ] </div>
     ] </body>
    ] </html>
  ] show ;

"live-updater" [ live-updater-responder ] install-cont-responder
