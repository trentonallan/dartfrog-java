Based on the actual code, here's an improved README:

---

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

- **Persistent connections** - Keeps TCP sockets alive across multiple requests with `Connection: keep-alive`
- **Request pipelining** - Handles multiple sequential requests on a single connection
- **Gzip compression** - Automatic response compression when clients send `Accept-Encoding: gzip`
- **Thread pooling** - Fixed pool size matches CPU cores (`Runtime.getRuntime().availableProcessors()`)
- **File serving** - GET operations with automatic Content-Type detection via `Files.probeContentType()`
- **File creation** - POST operations with directory creation and atomic file writes
- **Timeout handling** - 30-second socket timeout prevents hung connections
- **Clean request/response objects** - Immutable records with builder pattern for responses

Built entirely with Java standard library without frameworks or dependencies.

## Getting Started

### Prerequisites
```bash
Java 11+
```

### Running
```bash
# Default configuration (port 4221, current directory)
java -jar dartfrog.jar

# Custom file directory
java -jar dartfrog.jar --directory /path/to/files

# Custom port
java -jar dartfrog.jar --port 8080

# Both options
java -jar dartfrog.jar --directory /srv/files --port 8080
```

### Building from source
```bash
git clone https://github.com/trentonallan/dartfrog-java.git
cd dartfrog-java

# Compile
javac Main.java

# Run directly
java Main --directory ./files

# Or build JAR
jar cvfe dartfrog.jar Main Main.class Main\$*.class

# Run JAR
java -jar dartfrog.jar
```

## Architecture

### Core Components

**Main server thread** accepts incoming TCP connections on a `ServerSocket` and submits each client socket to the thread pool for processing.

**Worker threads** (`ExecutorService` with fixed pool) handle client requests concurrently. Each thread processes multiple requests sequentially over a persistent connection until the client closes or timeout occurs.

**Request parser** (`parseRequest()`) reads request line and headers into an immutable `HttpRequest` record. For POST requests, reads body based on `Content-Length` header.

**Router** (`routeRequest()`) dispatches requests by path prefix matching:
- `/` → 200 OK
- `/echo/{text}` → echo handler
- `/user-agent` → returns User-Agent header
- `/files/{name}` → file handler (GET/POST)

**Response builder** constructs `HttpResponse` objects using builder pattern, handles gzip compression, and formats proper HTTP response with status line and headers.

### Connection Flow

1. Main thread accepts connection and sets 30s timeout
2. Worker thread handles connection in loop:
   - Parse request (blocking read on `BufferedReader`)
   - Route to handler
   - Send response
   - Check `Connection` header and error status to determine keep-alive
3. Loop exits on timeout, socket error, or `Connection: close`
4. Socket closed and connection logged

### Thread Safety

No shared mutable state between threads. Each connection is owned by a single worker thread. File operations use atomic NIO methods (`Files.write()` with `CREATE` and `TRUNCATE_EXISTING`).

## API Endpoints

### `GET /`
Returns 200 OK with empty body.

### `GET /echo/{string}`
Returns the path segment after `/echo/` as plain text.

**Example:**
```bash
curl http://localhost:4221/echo/hello
# Returns: hello

curl -H "Accept-Encoding: gzip" http://localhost:4221/echo/compressed
# Returns: compressed (gzipped)
```

### `GET /user-agent`
Returns the `User-Agent` header value as plain text. Returns 400 if header is missing.

### `GET /files/{filename}`
Serves file content from the configured directory.
- Automatically detects Content-Type via `Files.probeContentType()`
- Falls back to `application/octet-stream` if type unknown
- Returns 404 if file doesn't exist

**Example:**
```bash
curl http://localhost:4221/files/test.txt
# Returns file content with appropriate Content-Type
```

### `POST /files/{filename}`
Creates or overwrites a file using request body as content.
- Automatically creates parent directories
- Uses atomic write operations (`CREATE` + `TRUNCATE_EXISTING`)
- Returns 201 on success, 400 if body is missing

**Example:**
```bash
curl -X POST -d "file content" http://localhost:4221/files/new.txt
# Creates new.txt with "file content"
```

## Implementation Notes

### HTTP/1.1 Protocol
- Request line parsing with method/path/version extraction
- Header parsing (case-insensitive keys, trimmed values)
- `Content-Length` handling for POST bodies
- `Connection: keep-alive` support with persistent sockets
- Proper status codes (200, 201, 400, 404, 405, 500)

### Compression
Gzip compression applied when:
1. Client sends `Accept-Encoding: gzip` header
2. Response body exists
3. Handler enables compression (currently only for file GET requests)

Implementation uses `GZIPOutputStream` to compress body in-memory, then sets `Content-Encoding: gzip` header.

### File Operations
**Reading (GET)**:
```java
byte[] fileContent = Files.readAllBytes(filePath);
String contentType = Files.probeContentType(filePath);
```

**Writing (POST)**:
```java
Files.createDirectories(filePath.getParent());
Files.write(filePath, body, 
    StandardOpenOption.CREATE, 
    StandardOpenOption.TRUNCATE_EXISTING);
```

### Error Handling
- `SocketTimeoutException` → close connection gracefully
- `SocketException` → client disconnected, log and close
- `IOException` during request processing → 500 response
- Malformed request line → return null, close connection
- Missing `Content-Length` for POST → ignore body

### Request/Response Model
Uses Java records for immutability:
```java
record HttpRequest(String method, String path, 
                   Map<String, String> headers, byte[] body)

record HttpResponse(int statusCode, 
                    Map<String, String> headers, byte[] body)
```

Response builder provides fluent API:
```java
new HttpResponse.Builder(200)
    .withHeader("Content-Type", "text/plain")
    .withBody("Hello".getBytes())
    .build();
```

## Limitations

This is an educational project for learning HTTP protocol implementation and socket programming. **Not production-ready.**

### Missing Features
- **No HTTPS/TLS** - all traffic is plaintext
- **No HTTP/2** - no multiplexing or header compression
- **No chunked transfer encoding** - all responses require `Content-Length`
- **No request size limits** - vulnerable to memory exhaustion
- **No rate limiting** - no protection against abuse
- **Limited compression** - only applies to file GET requests, not all responses
- **No virtual hosts** - single file directory for entire server
- **No URL decoding** - paths with encoded characters won't work correctly
- **No range requests** - can't resume downloads

### Known Issues
- **POST body validation** - accepts any `Content-Length` value without checking available memory
- **Malformed request handling** - closes connection without 400 response
- **File path traversal** - no validation that requested file is within configured directory
- **Content-Type detection** - `Files.probeContentType()` is OS-dependent and may fail
- **Connection counting** - no limit on concurrent connections (bounded only by thread pool)
- **Logging** - basic console output, no structured logging or log levels

### Improvements for Production
- Add request body size limits
- Implement path traversal protection (`..` detection)
- Add proper logging framework
- Implement graceful shutdown (drain thread pool)
- Add metrics/monitoring (connection count, request rates)
- Handle chunked transfer encoding
- Add configuration file support
- Implement HEAD, PUT, DELETE methods
- Add request/response middleware pipeline

## Author

**Trenton Allan**  
Northeastern University  
[allan.tr@northeastern.edu](mailto:allan.tr@northeastern.edu) | [GitHub](https://github.com/trentonallan)
