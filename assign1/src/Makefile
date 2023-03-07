CXX=g++
CPPFLAGS= -Wall -O2
PAPI= -lpapi
TARGET = matrixproduct
all: $(TARGET)
$(TARGET): $(TARGET).cpp
			$(CXX) $(CPPFLAGS) -o $(TARGET) $(TARGET).cpp $(PAPI)
clean:
			$(RM) $(TARGET) 