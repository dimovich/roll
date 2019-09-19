#!/usr/bin/env bash

find . -name "*~" -delete
rm -f .nrepl-port
rm -rf  resources/public/js
rm -rf .cpcache

