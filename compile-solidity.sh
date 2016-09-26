#!/usr/bin/env bash
cd resources/public/contracts/src
solc --optimize --bin --abi --combined-json bin,abi emojillionaire.sol -o ../build/ > ../build/emojillionaire.json