#!/usr/bin/perl --
use strict;
use warnings;
use utf8;
use feature qw(say);

# read from __DATA__ section in this file.
local $/ = undef;
my $text = <DATA>;

############################################
# change spaces and line feeds to single space,
# it's required for F-droid android client.
# also remove head/tail spaces in whole text.

$text =~ s/[\x00-\x20]+/ /g;
$text =~ s/\A //;
$text =~ s/ \z//;

############################################
# trim spaces before/after open/close block tags. also <br>,<br/>,</br>

# HTML block elements and "br". joined with '|'
my $blockElements = join "|", qw(
    address article aside blockquote canvas dd div dl dt 
    fieldset figcaption figure footer form 
    h1 h2 h3 h4 h5 h6 header hr li 
    main nav noscript ol p pre section table tfoot ul video 
    br
);

# RegEx for block tag that may have attributes, and spaces before/after tag.
my $trimElementRe = qr!\s*(</?(?:$blockElements)\b(?:[^>/"]+|"[^"]*")*/?>)\s*!i;

## verbose debugging.
#say $trimElementsRe;
#while( $text =~ /$trimElementRe/g){
#    next if $& eq $1;
#    say "[$&] => [$1]";
#}

$text =~ s/$trimElementRe/$1/g;

############################################

# write to .txt file. $0 means path of the this script file.
my $file = $0;
$file =~ s/\.pl$/\.txt/ or die "can't make output filename. $0";
open(my $fh,">:utf8",$file) or die "$file $!";
say $fh $text;
close($fh) or die "$file $!";

# apt-cyg install tidy libtidy5
system qq(tidy -q -e $file);

__DATA__

<p><i>Neo Feed</i> is a custom Google Now Feed replacement for launchers. It's actually a normal RSS-reader that can be embedded in place of Google's „Discover“ feature, to be pinned as a widget to the left of your home screen for easy access, and lets you add your own RSS feeds. At this moment it's an „early bird“ version and not yet considered a „public release“.</p>
<p>While primarily focused on <i>Neo-Launcher</i>, <i>Neo Feed</i> currently supports a.o. the following launchers:</p>
<ul>
<li>Neo Launcher</li>
<li>LibreChair</li>
<li>Shade Launcher</li>
<li>And any launcher with custom feed provider support.</li>
</ul>
