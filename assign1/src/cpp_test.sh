#!/bin/bash

test_line() {
    for j in {0..10}
    do
        ./matrixproduct 4 4
    done
}

make
test_line