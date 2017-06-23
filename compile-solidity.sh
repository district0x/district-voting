#!/usr/bin/env bash
cd resources/public/contracts/src

solc --overwrite --optimize --bin --abi DistrictVoting.sol -o ../build/

cd ../build
wc -c DistrictVoting.bin | awk '{print "DistrictVoting: " $1}'

