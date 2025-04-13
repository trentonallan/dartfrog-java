# Dartfrog: A High-Performance HTTP/1.1 Server in Java

Dartfrog is a robust and efficient HTTP/1.1 server implementation written in pure Java with no external dependencies. It's designed for performance and clarity, featuring asynchronous request handling via a thread pool.

## 📚 Table of Contents

- [Features](#-features)
- [Architecture](#-architecture)
- [Getting Started](#-getting-started)
- [API Endpoints](#-api-endpoints)
- [Implementation Details](#-implementation-details)
- [Future Updates](#-future-updates)

## ⭐️ Features

Dartfrog implements core HTTP/1.1 functionality:

- **Persistent Connections**: Keep TCP connections alive for multiple requests, improving performance.
- **Asynchronous Request Handling**: Utilizes a thread pool for efficient handling of concurrent client connections.
- **Content Compression (gzip)**: Automatically compresses responses when supported by clients, with content negotiation via `Accept-Encoding`.
- **Versatile File Management**: Provides efficient file serving capabilities (GET) with automatic Content-Type detection and allows for file creation/modification (POST) via request bodies.
- **Content Negotiation**: Honors client preferences for compression via the `Accept-Encoding` header.
- **Standards Compliant**: Adheres to HTTP/1.1 standards for status codes, headers, and response formats.
- **Robust Error Handling**: Includes comprehensive error handling and logging.
- **Clear, Modular Design**: Promotes maintainability and extensibility through well-organized code.

## 🏗 Architecture

Dartfrog uses a multi-threaded architecture with the following key components:

1. **Main Server Thread**: Accepts incoming TCP connections and delegates them to worker threads from the thread pool.
2. **Worker Threads (Thread Pool)**: A pool of threads that concurrently handle multiple client requests, improving responsiveness.
3. **Request Parser**: Parses the HTTP request line and headers into a structured `HttpRequest` object.
4. **Route Handler**: Maps incoming request paths to specific handler logic.
5. **Response Formatter**: Constructs `HttpResponse` objects with appropriate headers and body, including handling content compression.

The server maintains connection persistence where requested and ensures thread safety through the design of request handling.

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



Thank you for reading! Feel free to reach out at allan.tr@northeastern.edu.
