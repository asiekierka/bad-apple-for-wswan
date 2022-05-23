#!/bin/sh
sox "$1" -V -r 12000 -b 8 -G audio.wav dither -a
sox audio.wav audio.raw
