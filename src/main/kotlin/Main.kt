import java.io.*
import java.net.*
import java.util.Vector
import kotlin.concurrent.thread

fun main(args: Array<String>) {
    val socket = ServerSocket(9123)
    println("Opened server on http://localhost:9123")

    socket.use {
        while (true) {
            val conn = socket.accept()
            thread(isDaemon = true) {
                handleConnection(conn)
            }
        }
    }
}

fun handleConnection(conn: Socket) {
    conn.use {
        val request = readRequest(conn.inputStream)
        val response = handleRequest(request)
        val data = encodeResponse(response)
        conn.outputStream.write(data)

        val ip = conn.inetAddress.toString()
        println("${response.statusCode} ${request.path.padEnd(15)} $ip")
    }
}

fun readRequest(stream: InputStream): Request {
    val reader = BufferedReader(InputStreamReader(stream))
    val lines = reader.lines().iterator()
    val requestLine = lines.next()

    val method = requestLine.takeWhile { it != ' ' }

    val versionString = requestLine.reversed().takeWhile { it != ' ' }.reversed()
    if (!versionString.startsWith("HTTP/"))  throw Exception("Invalid Version String: $versionString")
    val version = versionString.removePrefix("HTTP/")

    val path = requestLine
        .removePrefix(method)
        .removeSuffix(versionString)
        .removeSurrounding(" ")

    val headers = Vector<Header>()
    for (line in reader.lines()) {
        if (line == "") {
            break
        }
        val name = line.takeWhile { it != ':'}
        val value = line.removePrefix("$name: ")
        headers.add(Header(name, value))
    }

    return Request(
        version = version,
        method = method,
        path = path,
        headers = headers,
        body = null,
    )
}


fun handleRequest(request: Request): Response {
    val file = if (request.path == "/api/source") {
        File("src/main/kotlin/Main.kt")
    } else {
        var path = request.path
        if (path == "/" || path == "") path = "index.html"
        File("static/${path}")
    }

    if (!file.exists()) {
        return Response(
            statusCode = 404,
            body = "Not Found",
        )
    }

    return Response(
        statusCode = 200,
        body = file.readText(),
    )
}


fun encodeResponse(response: Response): ByteArray {
    val hasBody = response.body != null
    if (response.body != null) {
        val length = response.body.length.toString()
        response.headers.add(Header("Content-Length", length))
    }

    var data = "HTTP/1.0 ${response.statusCode} OK\r\n"

    for (header in response.headers) {
        data += "${header.name}: ${header.value}"
    }
    data += "\r\n"

    if (hasBody) {
        data += "\r\n" + response.body + "\r\n"
    }

    return data.toByteArray()
}


data class Request (
    val version: String,
    val method: String,
    val path: String,
    val headers: Vector<Header>,
    val body: String?,
)

data class Response (
    val statusCode: Int,
    val headers: Vector<Header> = Vector<Header>(),
    val body: String? = null,
)

data class Header (
    val name: String,
    val value: String,
)
