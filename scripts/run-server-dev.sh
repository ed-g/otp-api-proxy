#!/bin/bash
cd $(dirname $0)/..
echo 4100 > target/repl-port
lein ring server-headless 4000

