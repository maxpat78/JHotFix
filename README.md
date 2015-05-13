JHotFix
=======

A simple Java tool to let the Windows 8.1 and Office 2013 user store locally the monthly hot fixes from MS download center and later re-use them.

It queries the MS server for the updates released after a certain date (default 3 weeks behind), analyzes the proper HTML pages with the help of jsoup library, and finally downloads the files.

It is designed to work with italian HTML pages only, but can be easily adapted to other languages.

