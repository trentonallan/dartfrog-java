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

- Robust request parsing of the request line and headers.
- Structured `HttpRequest` and `HttpResponse` objects for managing request and response data.
- Proper construction of response status lines and headers.
- Handling of `Content-Length` for request and response bodies.
- Support for persistent connections (`Connection: keep-alive` and `Connection: close`).

### Thread Management

The server utilizes a thread pool for efficient handling of concurrent connections:

- A fixed-size thread pool (`THREAD_POOL_SIZE` equal to the number of available processors) manages worker threads.
- Incoming client connections are submitted to the thread pool for asynchronous processing.
- This model improves performance by reusing threads and reducing the overhead of creating new threads for each connection.

### Compression

Dartfrog implements gzip compression with content negotiation:

- Uses Java's `GZIPOutputStream` for compressing response bodies.
- Compression is only applied if the client explicitly indicates support for `gzip` in the `Accept-Encoding` request header.
- The `Content-Encoding: gzip` header is included in compressed responses.

### File Handling

File operations are implemented using Java NIO for efficiency:

- `Files.readAllBytes` is used for reading file content for GET requests.
- `Files.probeContentType` attempts to automatically determine the correct `Content-Type` for served files.
- `Files.write` with `StandardOpenOption.CREATE` and `StandardOpenOption.TRUNCATE_EXISTING` is used for creating or overwriting files in POST requests.
- Parent directories are automatically created if they don't exist during POST requests.

## 🔮 Future Updates

This HTTP/1.1 server implementation is an educational project demonstrating fundamental networking concepts and HTTP request/response handling. It currently has several limitations compared to production-ready web servers. Future versions of Dartfrog may explore:

- **HTTP/2 Support**: Implementing multiplexing, server push, and header compression for improved performance.
- **WebSocket Protocol**: Enabling real-time bidirectional communication capabilities.
- **Enhanced Thread Management**: Further optimization of thread pool management and potential exploration of non-blocking I/O (NIO) for even greater scalability.
- **More Robust File Handling**: Support for range requests for large files and more sophisticated content type detection.
- **Admin Dashboard**: Providing runtime metrics and configuration options.



Thanks for reading! Feel free to reach out at allan.tr@northeastern.edu.
