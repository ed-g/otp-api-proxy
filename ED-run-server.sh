#!/bin/bash
cd $(dirname $(readlink -f $0))
lein ring server-headless

