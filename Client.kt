package client

import server.IP_ADDRESS
import server.PORT
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.lang.Exception
import java.net.InetAddress
import java.net.Socket
//
const val FILE_LOCATION_PATH = "/src/client/data/"

enum class Menu(val menuText: String, val action: (DataInputStream, DataOutputStream) -> Unit) {
    GET("get a file", {input, output -> getFile(input, output)}),
    PUT("create a file", {input, output -> addFile(input, output)}),
    DELETE("delete a file", {input, output -> deleteFile(input, output)});

    companion object {
        fun selectionMenu(): String {
            val menuString = StringBuilder("Enter action (")
            Menu.values().forEachIndexed { index, menu -> menuString.append("${index + 1} - ${menu.menuText}, ") }
            return menuString.dropLast(2).toString() + "): "
        }
    }
}

fun getResponse(req: String, input: DataInputStream, output: DataOutputStream): String {
    if (req == "PUT") {
        val clientFilename = print("Enter name of the file: ").run { readln() }
        val serverFilename = print("Enter name of the file to be saved on server: ").run { readln() }
        val filePath = System.getProperty("user.dir") + FILE_LOCATION_PATH + clientFilename
        if (File(filePath).exists()) {
            val content = File(filePath).readBytes()
            output.writeUTF("$req $serverFilename")
            output.writeInt(content.size)
            output.write(content)
        } else {
            output.writeUTF("NO SUCH CLIENT FILE")
        }

    } else {
        when (print("Do you want to ${req.lowercase()} the file by name or by id (1 - name, 2 - id): ").run { readln() }) {
            "1" -> {
                val filename = print("Enter filename: ").run { readln() }
                output.writeUTF("$req BY_NAME $filename")
            }
            "2" -> {
                val id = print("Enter id: ").run { readln() }
                output.writeUTF("$req BY_ID $id")
            }
        }
    }
    return println("The request was sent.").run { input.readUTF().trimStart() }
}

fun getFile(input: DataInputStream, output: DataOutputStream) {
    when (val response = getResponse(Menu.GET.name, input, output)) {
        "200" -> {
            val file = ByteArray(input.readInt())
            input.readFully(file, 0, file.size)
            val filename = print("The file was downloaded! Specify a name for it: ").run { readln() }
            File(System.getProperty("user.dir") + FILE_LOCATION_PATH + filename).writeBytes(file)
            println("File saved on the hard drive!")
        }
        "404" -> println("The response says that this file is not found!")
        else -> println("No such code: $response")
    }
}

fun addFile(input: DataInputStream, output: DataOutputStream) {
    val response = getResponse(Menu.PUT.name, input, output)
    when (response.split(" ").first()) {
        "200" -> println("Response says that file is saved! ID = ${response.split(" ")[1]}")
        "403" -> println("The response says that creating the file was forbidden!")
        else -> println("No such code: $response")
    }
}

fun deleteFile(input: DataInputStream, output: DataOutputStream) {
    when (val response = getResponse(Menu.DELETE.name, input, output)) {
        "200" -> println("The response says that this file was deleted successfully!")
        "404" -> println("The response says that this file is not found!")
        else -> println("No such code: $response")
    }
}

fun main() {
    val socket = Socket(InetAddress.getByName(IP_ADDRESS), PORT)
    val input = DataInputStream(socket.getInputStream())
    val output = DataOutputStream(socket.getOutputStream())
    try {
        Menu.values()[(print(Menu.selectionMenu()).run { readln() }).toInt() - 1].action(input, output)
    } catch (e: Exception) {
        if (e.message?.split(" ")?.last() == "\"exit\"") {
            output.writeUTF("exit")
            println("The request was sent.")
        } else {
            println("No such option.")
        }
    }
    socket.close()
}