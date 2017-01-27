#!/bin/bash
cd $(dirname $(readlink -f $0))
server_port=$1
lein ring server-headless $server_port

