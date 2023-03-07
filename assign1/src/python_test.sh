#!/bin/bash

test_line() {
    for j in {0..10}
    do
        for i in 0 1 2 3 4 5 6
        do
            amnt=$(( i * 400 + 600))
            # echo "python-line: multiplying $amnt x $amnt matrix"
            # ./main.py $amnt $amnt line python
            echo "pypy-line: multiplying $amnt x $amnt matrix"
            pypy ./main.py $amnt $amnt line pypy
            # echo "python-mult: multiplying $amnt x $amnt matrix"
            # ./main.py $amnt $amnt mult python
            echo "pypy-mult: multiplying $amnt x $amnt matrix"
            pypy ./main.py $amnt $amnt mult pypy
        done
    done
}

test_line