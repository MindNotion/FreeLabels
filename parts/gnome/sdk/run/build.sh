#!/bin/bash
set -euo pipefail
source /home/tanvi/Projects/Ashok/BharatLabelV3/freelabels-swing_v3.1/freelabels-swing/parts/gnome/sdk/run/environment.sh
set -x
make -j"12"
make -j"12" install DESTDIR="/home/tanvi/Projects/Ashok/BharatLabelV3/freelabels-swing_v3.1/freelabels-swing/parts/gnome/sdk/install"
