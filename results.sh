#!/bin/bash

outputfolder=./tmp/output
file="$outputfolder/$1/$1.d.3.seg"
cat $file
#read -n 1 -s -r -p "Press any key to continue"