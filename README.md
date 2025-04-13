# Dartfrog: A High-Performance HTTP/1.1 Server in Java

Dartfrog is a lightweight, multi-threaded HTTP/1.1 server implementation written in pure Java with no external dependencies. It features support for persistent connections, content compression, concurrent request handling, and a flexible file-serving system.

## 📚 Table of Contents

- [Features](#-features)
- [Architecture](#-architecture)
- [Getting Started](#-getting-started)
- [API Endpoints](#-api-endpoints)
- [Implementation Details](#-implementation-details)
- [Future Updates](#-future-updates)

## ⭐️ Features

Dartfrog implements core HTTP/1.1 functionality:

- **Persistent Connections**: Keep TCP connections alive for multiple requests, improving performance
- **Concurrent Request Handling**: Process multiple client connections simultaneously
- **Content Compression**: Automatically compress responses using gzip when supported by clients
- **File Serving**: Read and write files with proper error handling
- **Content Negotiation**: Honor client preferences for compression via Accept-Encoding
- **Standards Compliant**: Proper HTTP/1.1 status codes, headers, and response formats
- **Zero Dependencies**: Written in pure Java with no external libraries

## 🏗 Architecture

Dartfrog uses a thread-per-connection model with the following components:

1. **Main Server Thread**: Accepts new TCP connections and delegates them to handler threads
2. **Connection Handlers**: Each client connection gets a dedicated thread that processes multiple requests
3. **Request Parser**: Extracts HTTP method, headers, and body
4. **Route Handler**: Maps URL paths to appropriate handlers
5. **Response Formatter**: Constructs HTTP responses with proper headers and compression

The server maintains connection state for persistent connections while isolating each client's data to ensure thread safety.

## 🚀 Getting Started

### Prerequisites

- Java 11 or higher
- Maven (if building from source)

### Installation

#### Running the pre-built JAR:

```bash
# Run the server on the default port (4221)
java -jar dartfrog.jar

# Run with a specific file directory
java -jar dartfrog.jar --directory /path/to/files

# Run with custom port (using JVM arguments)
java -Dport=8080 -jar dartfrog.jar
```

#### Building from source:

```bash
# Clone the repository
git clone https://github.com/trentonallan/dartfrog-java.git
cd dartfrog-java

# Build the JAR
javac -d build Main.java
jar cvfe dartfrog.jar Main -C build .

# Run the server
java -jar dartfrog.jar
```

## 🔌 API Endpoints

Dartfrog supports the following endpoints:

### Root Endpoint
```
GET /
```
Returns a 200 OK response with an empty body.

### Echo Endpoint
```
GET /echo/{string}
```
Returns the {string} value as plain text.

Supports gzip compression when requested via Accept-Encoding header.

### User-Agent Endpoint
```
GET /user-agent
```
Returns the User-Agent header value as plain text.

### Files Endpoint
```
GET /files/{filename}
```
Returns the contents of the specified file from the configured directory.

```
POST /files/{filename}
```
Creates or overwrites a file with the specified name, using the request body as content.

## 🔧 Implementation Details

### HTTP Protocol Implementation

Dartfrog implements core HTTP/1.1 features such as:

- Request parsing and validation
- Response construction
- Header processing
- Content-Length handling
- Connection persistence

### Thread Management

The server employs a thread-per-connection model where:

- Each TCP connection gets a dedicated Java thread
- Connection state is maintained for the life of the connection
- Thread-local storage prevents cross-connection contamination
- Socket timeouts prevent resource leaks from abandoned connections

### Compression

The gzip compression implementation:

- Uses Java's built-in GZIPOutputStream
- Only activates when clients support it (via Accept-Encoding header)
- Includes proper Content-Encoding headers
- Updates Content-Length to reflect compressed size
- Has fallback mechanisms if compression fails

### File Handling

File operations are implemented using Java NIO for better performance:

- Non-blocking I/O where appropriate
- Memory-mapped files for large file transfers
- Proper exception handling for missing files and permission issues
- Support for binary files with correct Content-Type headers

## 🔮 Future Updates

Future versions of Dartfrog may include:

- **HTTP/2 Support**: Multiplexing, server push, and header compression
- **WebSocket Protocol**: Enable real-time bidirectional communication
- **Thread Pooling**: Replace thread-per-connection with NIO and worker pools
- **Admin Dashboard**: Runtime metrics and configuration



Thanks for reading! Feel free to reach out at allan.tr@northeastern.edu.
