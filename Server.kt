package server

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.lang.Exception
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

const val IP_ADDRESS = "127.0.0.1"
const val PORT = 28657
const val FILE_LOCATION_PATH = "/src/server/data/"
const val ID_FILE = "fileID.txt"

@Synchronized
fun getFile(request: String, location: String, output: DataOutputStream) {
    if (request.split(" ").size > 2) {
        var filename = ""
        when (request.split(" ")[1]) {
            "BY_NAME" -> filename = request.split(" ")[2]
            "BY_ID" -> filename = getIdMap(location + ID_FILE)[request.split(" ")[2].trim().toInt()] ?: ""
        }
        if (File(location + filename).exists() && filename in getIdMap(location + ID_FILE).values) {
            try {
                val file = File(location + filename).readBytes()
                output.writeUTF("200")
                output.writeInt(file.size)
                output.write(file)
            } catch (e: Exception) {
                output.writeUTF("404")
            }
        } else {
            output.writeUTF("404")
        }
    } else {
        output.writeUTF("404")
    }
}

@Synchronized
fun addFile(request: String, location: String, input: DataInputStream, output: DataOutputStream) {
    if (request.trim().split(" ").size < 3) {
        val filename: String
        if (request.trim().split(" ").size == 1) {
            filename = "file${newKey(location + ID_FILE)}.gen"
            if (File(location + filename).exists()) {
                File(location + filename).delete()
            }
        } else {
            filename = request.trim().split(" ")[1]
        }
        if (File(location + filename).exists()) {
            output.writeUTF("403")
        } else {
            try {
                val length = input.readInt()
                val content = ByteArray(length)
                input.readFully(content, 0, content.size)
                File(location + filename).writeBytes(content)
                val idMap = getIdMap(location + ID_FILE)
                val fileId = newKey(location + ID_FILE)
                idMap[fileId] = filename
                saveIdMap(location + ID_FILE, idMap)
                output.writeUTF("200 $fileId")
            } catch (e: Exception) {
                output.writeUTF("403")
            }
        }
    } else {
        output.writeUTF("403")
    }
}

@Synchronized
fun deleteFile(request: String, location: String, output: DataOutputStream) {
    if (request.split(" ").size > 2) {
        var filename = ""
        when (request.split(" ")[1]) {
            "BY_NAME" -> filename = request.split(" ")[2]
            "BY_ID" -> filename = getIdMap(location + ID_FILE)[request.split(" ")[2].trim().toInt()] ?: ""
        }
        if (File(location + filename).exists() && filename in getIdMap(location + ID_FILE).values && filename != ID_FILE) {
            try {
                if (File(location + filename).delete()) {
                    removeID(location + ID_FILE, filename)
                    output.writeUTF("200")
                } else {
                    output.writeUTF("404")
                }
            } catch (e: Exception) {
                output.writeUTF("404")
            }
        } else {
            output.writeUTF("404")
        }
    } else {
        output.writeUTF("404")
    }
}

fun getIdMap(location: String): MutableMap<Int, String> {
    var content = ""
    if (File(location).exists()) {
        content = File(location).readText()
    }
    val idMap = mutableMapOf<Int, String>()
    for (entry in content.trim('{').trim('}').split(",")) {
        if (entry != "" && entry.split("=").size == 2) {
            idMap[entry.split("=")[0].trim().toInt()] = entry.split("=")[1]
        }
    }
    return idMap
}

fun saveIdMap(location: String, idMap: MutableMap<Int, String>) {
    File(location).writeText(idMap.toString())
}

fun newKey(location: String): Int {
    return (getIdMap(location).keys.maxOrNull() ?: 0) + 1
}

fun removeID(location: String, filename: String) {
    val idMap = getIdMap(location)
    idMap.entries.removeIf { it.value == filename }
    saveIdMap(location, idMap)
}

fun main() {
    val clientFiles = System.getProperty("user.dir") + client.FILE_LOCATION_PATH
    Files.createDirectories(Paths.get(clientFiles))
    val fileLocation = System.getProperty("user.dir") + FILE_LOCATION_PATH
    Files.createDirectories(Paths.get(fileLocation))
    val idFile = fileLocation + ID_FILE
    val idMap = mutableMapOf<Int, String>()
    if (!File(idFile).exists()) {
        saveIdMap(idFile, idMap)
    }
    val server = ServerSocket(PORT, 50, InetAddress.getByName(IP_ADDRESS))
    val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    println("Server started!")
    var socket = server.accept()
    var input = DataInputStream(socket.getInputStream())
    var output = DataOutputStream(socket.getOutputStream())
    var request = input.readUTF()
    while (request != "exit") {
        executor.submit {
            when (request.split(" ")[0]) {
                "GET" -> getFile(request, fileLocation, output)
                "PUT" -> addFile(request, fileLocation, input, output)
                "DELETE" -> deleteFile(request, fileLocation, output)
                else -> output.writeUTF("No such method: $request")
            }
        }
        socket = server.accept()
        input = DataInputStream(socket.getInputStream())
        output = DataOutputStream(socket.getOutputStream())
        request = input.readUTF()
    }
    executor.shutdown()
    executor.awaitTermination(2, TimeUnit.SECONDS)
    socket.close()
    server.close()
}