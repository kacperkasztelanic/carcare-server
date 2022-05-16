#!/bin/bash

rm -rf target/www
mkdir -p target
cp ../client/build/client.zip target
cd target
unzip client.zip
rm client.zip
