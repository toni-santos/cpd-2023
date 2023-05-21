# CPD - Assignment 2 - G12

### Group Members

- António Oliveira Santos, up202008004
- Pedro Alexandre Ferreira e Silva, up202004985
- Pedro Miguel Magalhães Nunes, up202004714

### Running

You may run the program by executing the `run.sh` script, which will open **x** number of clients in different terminals and a terminal with the server, as follows:

````sh
./run.sh <number-of-clients>
````

This script may not work, in this case you may run the following commands on different terminal windows:

````sh
# Server launch
gradle Server

# Client launch
gradle Client
````