# Dartfrog: HTTP/1.1 Server in Java

A multithreaded HTTP/1.1 server built from scratch in Java with zero external dependencies.

## Table of Contents
- [Features](#features)
- [Getting Started](#getting-started)
- [Architecture](#architecture)
- [API Endpoints](#api-endpoints)
- [Implementation Notes](#implementation-notes)
- [Limitations](#limitations)

## Features

- **Persistent connections** - Keeps TCP sockets alive across multiple requests
- **Request pipelining** - Handles multiple requests on a single connection
- **Gzip compression** - Automatic response compression with content negotiation
- **Thread pooling** - Fixed-size pool matches available CPU cores
- **File serving** - GET and POST operations with automatic Content-Type detection
- **RFC compliance** - Proper status codes, headers, and response formatting

Built entirely with Java standard library - no frameworks, no dependencies.

## Getting Started

### Prerequisites
```bash
Java 11+
```

### Running
```bash
# Default port (4221)
java -jar dartfrog.jar

# Custom file directory
java -jar dartfrog.jar --directory /path/to/files

# Custom port
java -Dport=8080 -jar dartfrog.jar
```

### Building from source
```bash
git clone https://github.com/trentonallan/dartfrog-java.git
cd dartfrog-java

javac -d build Main.java
jar cvfe dartfrog.jar Main -C build .
```

## Architecture

### Core Components

**Main server thread** accepts incoming TCP connections and submits them to the thread pool.

**Worker threads** handle client requests concurrently. Pool size matches `Runtime.getRuntime().availableProcessors()` for optimal CPU utilization.

**Request parser** converts raw HTTP bytes into structured `HttpRequest` objects containing method, path, headers, and body.

**Route handler** maps request paths to handler logic (root, echo, user-agent, files).

**Response formatter** builds `HttpResponse` objects with proper status codes, headers, and optional gzip compression.

### Connection Management

Connections persist by default (`Connection: keep-alive`) unless the client requests closure or an error occurs. Each worker thread processes requests sequentially on its assigned connection until termination.

## API Endpoints

### `GET /`
Returns 200 OK with empty body.

### `GET /echo/{string}`
Returns the path parameter as plain text. Supports gzip compression via `Accept-Encoding: gzip`.

**Example:**
```bash
curl http://localhost:4221/echo/hello
# Returns: hello
```

### `GET /user-agent`
Returns the `User-Agent` header value.

### `GET /files/{filename}`
Serves file content from the configured directory with automatic Content-Type detection.

### `POST /files/{filename}`
Creates or overwrites a file using the request body as content. Creates parent directories if needed.

## Implementation Notes

### HTTP/1.1 Protocol
- Request line and header parsing
- `Content-Length` handling for bodies
- Persistent connection support
- Proper status line formatting

### Compression
Uses `GZIPOutputStream` to compress responses when clients send `Accept-Encoding: gzip`. The `Content-Encoding: gzip` header indicates compressed responses.

### File Operations
Built on Java NIO:
- `Files.readAllBytes()` for reading
- `Files.probeContentType()` for MIME type detection
- `Files.write()` with `CREATE` and `TRUNCATE_EXISTING` for writing

### Thread Safety
Each connection is handled by a single thread sequentially. No shared mutable state between threads - thread safety comes from isolation rather than synchronization.

## Limitations

This is an educational project for learning HTTP protocol implementation and socket programming. Not production-ready.

**Known issues:**
- No request body size validation (potential DoS vector)
- Basic error handling (doesn't gracefully handle malformed requests)
- Limited logging and observability

## Author

**Trenton Allan**  
Northeastern University  
[allan.tr@northeastern.edu](mailto:allan.tr@northeastern.edu) | [GitHub](https://github.com/trentonallan)
