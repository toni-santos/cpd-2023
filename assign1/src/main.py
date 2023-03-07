#!/usr/bin/env python
import time, argparse, os

parser = argparse.ArgumentParser(description='Matrix multiplication algorithm performance tester')
parser.add_argument('m_ar', type=int, help='matrix a size', nargs=1)
parser.add_argument('m_br', type=int, help='matrix b size', nargs=1)
parser.add_argument('alg', type=str, help='algorithm to be used', choices=['mult', 'line'], nargs=1)
parser.add_argument('comp', type=str, help='compiler used', choices=['python', 'pypy'], nargs=1)


def OnMult(m_ar, m_br):
    pha = []
    phb = []
    phc = []

    for i in range(m_ar):
        for j in range(m_ar):
            pha.insert(i*m_ar + j, 1)

    for i in range(m_br):
        for j in range(m_br):
            phb.insert(i*m_br + j, i+1)

    t0 = time.time()

    for i in range(m_ar):
        for j in range(m_br):
            temp = 0
            for k in range(m_ar):
                temp += pha[i*m_ar+k] * phb[k*m_br+j]
            phc.insert(i*m_ar+j, temp);

    t1 = time.time()
    tf = t1-t0
    print("mult: total time was "+ str(tf) + " seconds;\n")

    saveTime(tf, 'mult', m_ar)

def OnMultLine(m_ar, m_br):
    pha = []
    phb = []
    phc = []

    for i in range(m_ar):
        for j in range(m_ar):
            pha.insert(i*m_ar + j, 1)

    for i in range(m_br):
        for j in range(m_br):
            phb.insert(i*m_br + j, i+1)

    t0 = time.time()

    for i in range(m_ar):
        for k in range(m_ar):
            temp = 0
            for j in range(m_br):
                temp += pha[i*m_ar+k] * phb[k*m_br+j]
            phc.insert(i*m_ar+j, temp);

    t1 = time.time()
    tf = t1-t0
    print("line: total time was "+ str(tf) + " seconds;\n")

    saveTime(tf, 'line', m_ar)

def saveTime(time, func, size):
    args = parser.parse_args()
    comp = args.comp[0]

    file = str(size)+'.txt'
    filepath = './results/'+ comp +'/' + func + '/'
    path = filepath + file

    with open(path, 'a+') as file:
        file.write(str(time)+'\n')


def __main__():
    args = parser.parse_args()

    m_ar = args.m_ar[0]
    m_br = args.m_br[0]
    alg = args.alg[0]

    if alg == 'mult':
        OnMult(m_ar, m_br)
    else:
        OnMultLine(m_ar, m_br)

if __name__ == "__main__":
    __main__()