#!/bin/bash
set -euo pipefail
source /home/tanvi/Projects/Ashok/BharatLabelV3/freelabels-swing_v3.1/freelabels-swing/parts/freelabels/run/environment.sh
set -x
cp --archive --link --no-dereference . "/home/tanvi/Projects/Ashok/BharatLabelV3/freelabels-swing_v3.1/freelabels-swing/parts/freelabels/install"
