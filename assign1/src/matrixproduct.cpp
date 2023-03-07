#include <stdio.h>
#include <iostream>
#include <iomanip>
#include <time.h>
#include <cstdlib>
#include <fstream>
#include <string>
#include <papi.h>

using namespace std;

#define SYSTEMTIME clock_t
#define PATH "./results/cpp/"

void write_results(int size, int block_size, int alg, double time, long long* papi) {
	ofstream f;
	string str_alg;
	string str_size = to_string(size);
	switch(alg) {
		case 1:
		str_alg = "mult";
		break;
		case 2:
		str_alg = "line";
		break;
		case 3:
		str_alg = "block";
		str_size = to_string(size) + "-" + to_string(block_size);
		break;
	}
	char str_time[100];
	sprintf(str_time, "%.6f", (double)(time)/CLOCKS_PER_SEC);
	string file_path = "./results/cpp/cpp_results.csv"; 
	f.open(file_path, fstream::in | fstream::out | fstream::app);
	
	if (!f) {
		exit(1);
	}

	f << str_alg << "," << str_size << "," << str_time << "," << papi[0] << "," << papi[1] << "," << papi[2] << "," << papi[3] << "," << endl;

	f.close();
}

 
double OnMult(int m_ar, int m_br) 
{
	
	SYSTEMTIME Time1, Time2;
	
	char st[100];
	double temp;
	int i, j, k;

	double *pha, *phb, *phc;
	

		
    pha = (double *)malloc((m_ar * m_ar) * sizeof(double));
	phb = (double *)malloc((m_ar * m_ar) * sizeof(double));
	phc = (double *)malloc((m_ar * m_ar) * sizeof(double));

	for(i=0; i<m_ar; i++)
		for(j=0; j<m_ar; j++)
			pha[i*m_ar + j] = (double)1.0;



	for(i=0; i<m_br; i++)
		for(j=0; j<m_br; j++)
			phb[i*m_br + j] = (double)(i+1);



    Time1 = clock();

	for(i=0; i<m_ar; i++)
	{	for( j=0; j<m_br; j++)
		{	temp = 0;
			for( k=0; k<m_ar; k++)
			{	
				temp += pha[i*m_ar+k] * phb[k*m_br+j];
			}
			phc[i*m_ar+j]=temp;
		}
	}


    Time2 = clock();
	sprintf(st, "Time: %3.3f seconds\n", (double)(Time2 - Time1) / CLOCKS_PER_SEC);
	cout << st;

	// display 10 elements of the result matrix tto verify correctness
	cout << "Result matrix: " << endl;
	for(i=0; i<1; i++)
	{	for(j=0; j<min(10,m_br); j++)
			cout << phc[j] << " ";
	}
	cout << endl;

    free(pha);
    free(phb);
    free(phc);
	
	return (double)(Time2 - Time1);
	
}

// add code here for line x line matriz multiplication
double OnMultLine(int m_ar, int m_br)
{
	SYSTEMTIME Time1, Time2;
	
	char st[100];
	double temp;
	int i, j, k;

	double *pha, *phb, *phc;
	

		
    pha = (double *)malloc((m_ar * m_ar) * sizeof(double));
	phb = (double *)malloc((m_ar * m_ar) * sizeof(double));
	phc = (double *)malloc((m_ar * m_ar) * sizeof(double));

	for(i=0; i<m_ar; i++)
		for(j=0; j<m_ar; j++)
			pha[i*m_ar + j] = (double)1.0;



	for(i=0; i<m_br; i++)
		for(j=0; j<m_br; j++)
			phb[i*m_br + j] = (double)(i+1);



    Time1 = clock();

	for(i=0; i<m_ar; i++)
	{	for( k=0; k<m_ar; k++)
		{	temp = 0;
			for( j=0; j<m_br; j++)
			{	
				temp = pha[i*m_ar+k] * phb[k*m_br+j];
				phc[i*m_ar+j]+=temp;
			}
		}
	}


    Time2 = clock();
	sprintf(st, "Time: %3.3f seconds\n", (double)(Time2 - Time1) / CLOCKS_PER_SEC);
	cout << st;

	// display 10 elements of the result matrix tto verify correctness
	cout << "Result matrix: " << endl;
	for(i=0; i<1; i++)
	{	for(j=0; j<min(10,m_br); j++)
			cout << phc[j] << " ";
	}
	cout << endl;

    free(pha);
    free(phb);
    free(phc);
	
	return (double)(Time2 - Time1);
    
}

// add code here for block x block matriz multiplication
double OnMultBlock(int m_ar, int m_br, int bkSize)
{
	SYSTEMTIME Time1, Time2;
	
	char st[100];
	int i, j, k, bi, bj, bk;

	double *pha, *phb, *phc;
	

		
    pha = (double *)malloc((m_ar * m_ar) * sizeof(double));
	phb = (double *)malloc((m_ar * m_ar) * sizeof(double));
	phc = (double *)malloc((m_ar * m_ar) * sizeof(double));

	for(i=0; i<m_ar; i++)
		for(j=0; j<m_ar; j++)
			pha[i*m_ar + j] = (double)1.0;



	for(i=0; i<m_br; i++)
		for(j=0; j<m_br; j++)
			phb[i*m_br + j] = (double)(i+1);



    Time1 = clock();

	if (bkSize > m_ar || (m_ar % bkSize != 0)) {
		printf("Matrix size must be bigger than block size and divisible by it.");
		return -1.0;
	}

	for (i = 0; i < m_ar; i += bkSize)
		for (k = 0; k < m_br; k += bkSize)
			for (j = 0; j < m_ar; j += bkSize)
				for (bi = i; bi < i + bkSize; bi++)
					for (bk = k; bk < k + bkSize; bk++)
						for (bj = j; bj < j + bkSize; bj++)
							phc[bi*m_ar+bj] += pha[bi*m_ar+bk] * phb[bk*m_br+bj];

    Time2 = clock();
	sprintf(st, "Time: %3.3f seconds\n", (double)(Time2 - Time1) / CLOCKS_PER_SEC);
	cout << st;

	// display 10 elements of the result matrix tto verify correctness
	cout << "Result matrix: " << endl;
	for(i=0; i<1; i++)
	{	for(j=0; j<min(10,m_br); j++)
			cout << phc[j] << " ";
	}
	cout << endl;

    free(pha);
    free(phb);
    free(phc);
    
	return (double)(Time2 - Time1);
}

void Benchmark(int initialSize, int finalSize, int increment, int blockSize, int opt, int EventSet) {

	if (blockSize != 0) printf("\n## Block size: %i\n", blockSize);
	for (int n = initialSize; n <= finalSize; n += increment) {
		printf("\nMatrix size: %i\n", n);
		double time;
		int ret;
		long long values[2];


		// Start counting
		ret = PAPI_start(EventSet);
		if (ret != PAPI_OK) cout << "ERROR: Start PAPI" << endl;

		switch (opt) {
			case 1:
				time = OnMult(n, n);
				break;
			case 2:
				time = OnMultLine(n, n);
				break;
			case 3:
				time = OnMultBlock(n, n, blockSize);
				break;
		}

		ret = PAPI_stop(EventSet, values);
  		if (ret != PAPI_OK) cout << "ERROR: Stop PAPI" << endl;
  		printf("L1 DCM: %lld \n",values[0]);
  		printf("L2 DCM: %lld \n",values[1]);

		ret = PAPI_reset( EventSet );
		if ( ret != PAPI_OK )
			std::cout << "FAIL reset" << endl;

		write_results(n, blockSize, opt, time, values);
	}
}

void FullBenchmark(int EventSet) {
	printf("\n### TRADITIONAL MULTIPLICATION ###\n");
	Benchmark(600, 3000, 400, 0, 1, EventSet);

	printf("\n### LINE MULTIPLICATION ###\n");
	Benchmark(600, 3000, 400, 0, 2, EventSet);
	Benchmark(4096, 10240, 2048, 0, 2, EventSet);

	printf("\n### BLOCK MULTIPLICATION ###\n");
	Benchmark(4096, 10248, 2048, 128, 3, EventSet);
	Benchmark(4096, 10248, 2048, 256, 3, EventSet);
	Benchmark(4096, 10248, 2048, 512, 3, EventSet);
}


void handle_error (int retval)
{
  printf("PAPI error %d: %s\n", retval, PAPI_strerror(retval));
  exit(1);
}

void init_papi() {
  int retval = PAPI_library_init(PAPI_VER_CURRENT);
  if (retval != PAPI_VER_CURRENT && retval < 0) {
    printf("PAPI library version mismatch!\n");
    exit(1);
  }
  if (retval < 0) handle_error(retval);

  std::cout << "PAPI Version Number: MAJOR: " << PAPI_VERSION_MAJOR(retval)
            << " MINOR: " << PAPI_VERSION_MINOR(retval)
            << " REVISION: " << PAPI_VERSION_REVISION(retval) << "\n";
}


int main (int argc, char *argv[])
{

	char c;
	int lin, col, blockSize;
	int op, op2;
	
	int EventSet = PAPI_NULL;
  	int ret;

	if (argc < 3) {
		printf("Cmd line args!");
		exit(-1);
	}

	ret = PAPI_library_init( PAPI_VER_CURRENT );
	if ( ret != PAPI_VER_CURRENT )
		std::cout << "FAIL" << endl;


	ret = PAPI_create_eventset(&EventSet);
		if (ret != PAPI_OK) cout << "ERROR: create eventset" << endl;


	ret = PAPI_add_event(EventSet,PAPI_L1_DCM );
	if (ret != PAPI_OK) cout << "ERROR: PAPI_L1_DCM" << endl;


	ret = PAPI_add_event(EventSet,PAPI_L2_DCM);
	if (ret != PAPI_OK) cout << "ERROR: PAPI_L2_DCM" << endl;

	ret = PAPI_add_event(EventSet,PAPI_TOT_CYC);
	if (ret != PAPI_OK) cout << "ERROR: PAPI_TOT_CYC" << endl;

	ret = PAPI_add_event(EventSet, PAPI_TOT_INS);
	if (ret != PAPI_OK) cout << "ERROR: PAPI_TOT_INS" << endl;

	op=stoi(argv[1]);
	do {
		cout << endl << "1. Multiplication" << endl;
		cout << "2. Line Multiplication" << endl;
		cout << "3. Block Multiplication" << endl;
		cout << "4. Benchmarks" << endl;
		cout << "Selection?: ";
		if (!(op <= 4 && op >= 1)) cin >> op;
		if (op == 0) break;
		else if (op != 4) {
			printf("Dimensions: lins=cols ? ");
			cin >> lin;
			col = lin;
		}

		switch (op) {
			case 1: 
				OnMult(lin, col);
				break;
			case 2:
				OnMultLine(lin, col);  
				break;
			case 3:
				cout << "Block Size? ";
				cin >> blockSize;
				OnMultBlock(lin, col, blockSize);  
				break;
			case 4:
				op2 = stoi(argv[2]);
				do {
					cout << "Which benchmark would you like to run?" << endl;
					cout << "1. Multiplication Benchmark" << endl;
					cout << "2. Line Multiplication Benchmark" << endl;
					cout << "3. Block Multiplication Benchmark" << endl;
					cout << "4. Full Benchmark" << endl;
					cout << "0. Previous Menu" << endl;
					cout << "Selection?: ";
					if (!(op2 <= 4 && op2 >= 1)) cin >> op2;

					if (op2 == 0) break;

					switch (op2) {
						case 1:
							printf("Running traditional multiplication benchmark.\n");
							Benchmark(600, 3000, 400, 0, 1, EventSet);
							break;
						case 2:
							printf("Running line multiplication benchmark.\n");
							Benchmark(600, 3000, 400, 0, 2, EventSet);
							Benchmark(4096, 10240, 2048, 0, 2, EventSet);
							break;
						case 3:
							printf("Running block multiplication benchmark.\n");
							Benchmark(4096, 10248, 2048, 128, 3, EventSet);
							Benchmark(4096, 10248, 2048, 256, 3, EventSet);
							Benchmark(4096, 10248, 2048, 512, 3, EventSet);
							break;
						case 4:
							printf("Running full benchmark. Please be patient.\n");
							FullBenchmark(EventSet);
							printf("\nEnd of benchmark. Thank you for your patience.\n");
							break;
					}
					cout << endl;
				} while (op2 != 0);

		}

	} while (op != 0);

	ret = PAPI_remove_event( EventSet, PAPI_L1_DCM );
	if ( ret != PAPI_OK )
		std::cout << "FAIL remove event" << endl; 

	ret = PAPI_remove_event( EventSet, PAPI_L2_DCM );
	if ( ret != PAPI_OK )
		std::cout << "FAIL remove event" << endl; 

	ret = PAPI_remove_event( EventSet, PAPI_TOT_CYC );
	if ( ret != PAPI_OK )
		std::cout << "FAIL remove event" << endl; 

	ret = PAPI_remove_event( EventSet, PAPI_TOT_INS );
	if ( ret != PAPI_OK )
		std::cout << "FAIL remove event" << endl; 

	ret = PAPI_destroy_eventset( &EventSet );
	if ( ret != PAPI_OK )
		std::cout << "FAIL destroy" << endl;

}