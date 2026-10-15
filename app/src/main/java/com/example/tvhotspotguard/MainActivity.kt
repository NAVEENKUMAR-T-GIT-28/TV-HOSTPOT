package com.example.tvhotspotguard

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var host: EditText
    private lateinit var port: EditText
    private lateinit var status: TextView
    private lateinit var list: LinearLayout
    private lateinit var connectButton: Button
    private val devices = mutableListOf<Client>()
    private val protected = setOf(
        "4e:71:01:ea:b5:12", // I2403
        "e8:b0:c5:24:5d:c9", // LAPTOP-V2CFLSTE
        "32:b8:42:c0:6d:c9"  // Critical control phone — never block
    )

    data class Client(val ip: String, val mac: String, val iface: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setBackgroundColor(Color.rgb(245, 247, 250))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        content.addView(text("TV Hotspot Guard", 26, "#101827", true))
        content.addView(text("ADB client manager • Android TV", 14, "#64748B", false))
        content.addView(space(18))
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.WHITE)
        }
        card.addView(text("TV connection", 18, "#101827", true))
        host = EditText(this).apply { hint = "TV IP address"; setSingleLine(); setText("192.168.43.1") }
        port = EditText(this).apply { hint = "ADB port"; setSingleLine(); inputType = 2; setText("5555") }
        card.addView(host, params())
        card.addView(port, params())
        connectButton = button("Connect & scan") { connectAndScan() }
        card.addView(connectButton, params())
        content.addView(card)
        content.addView(space(12))
        status = text("Not connected. Connect to your TV's ADB endpoint.", 14, "#334155", false)
        content.addView(status)
        content.addView(space(14))
        content.addView(text("Connected clients", 20, "#101827", true))
        content.addView(text("Blocking is manual. Protected devices are always excluded.", 13, "#64748B", false))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(list)
        content.addView(space(16))
        content.addView(text("Protected MAC addresses", 16, "#101827", true))
        protected.forEach { content.addView(text("✓  $it", 13, "#166534", true)) }
        content.addView(space(12))
        content.addView(text("Safety: this app never issues a block command for a protected MAC. The vendor XbhBlock middleware and /data/local/tmp/classes.dex must already exist on the TV. Confirm action values on your device before use.", 12, "#92400E", false))
        setContentView(root)
    }

    private fun connectAndScan() {
        val ip = host.text.toString().trim()
        val adbPort = port.text.toString().trim().toIntOrNull()
        if (!ip.matches(Regex("^[0-9.]{7,15}$")) || adbPort == null || adbPort !in 1..65535) {
            status.text = "Enter a valid TV IP and port."
            return
        }
        connectButton.isEnabled = false
        status.text = "Connecting to $ip:$adbPort …"
        executor.execute {
            try {
                val adb = AdbShell(ip, adbPort)
                val output = adb.exec("cat /proc/net/arp")
                val found = parseArp(output)
                runOnUiThread {
                    devices.clear(); devices.addAll(found)
                    renderDevices()
                    status.text = "ADB connected • ${found.size} client(s) found in ARP table. ARP may not show every hotspot client."
                    connectButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Connection failed: ${e.message ?: e.javaClass.simpleName}. Check ADB TCP access and TV IP."
                    connectButton.isEnabled = true
                }
            }
        }
    }

    private fun parseArp(raw: String): List<Client> {
        val result = linkedMapOf<String, Client>()
        raw.lineSequence().drop(1).forEach { line ->
            val p = line.trim().split(Regex("\\s+"))
            if (p.size >= 6) {
                val mac = p[3].lowercase(Locale.US)
                val iface = p[5]
                if (iface == "wlan1" && mac.matches(Regex("(?:[0-9a-f]{2}:){5}[0-9a-f]{2}")) && mac != "00:00:00:00:00:00" && p[2] == "0x2") {
                    result[mac] = Client(p[0], mac, iface)
                }
            }
        }
        return result.values.sortedBy { it.ip }
    }

    private fun renderDevices() {
        list.removeAllViews()
        if (devices.isEmpty()) {
            list.addView(text("No eligible wlan1 ARP entries found.", 14, "#64748B", false))
            return
        }
        devices.forEach { d ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setBackgroundColor(Color.WHITE)
            }
            row.addView(text(d.ip, 17, "#101827", true))
            row.addView(text(d.mac, 14, "#475569", false))
            if (d.mac in protected) {
                row.addView(text("PROTECTED — block/unblock controls disabled", 12, "#166534", true))
            } else {
                val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                val block = button("Block") { confirmAction(d, 1) }
                val unblock = button("Unblock") { confirmAction(d, 0) }
                actions.addView(block, LinearLayout.LayoutParams(0, dp(46), 1f))
                actions.addView(space(8))
                actions.addView(unblock, LinearLayout.LayoutParams(0, dp(46), 1f))
                row.addView(actions)
            }
            val lp = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
            list.addView(row, lp)
        }
    }

    private fun confirmAction(client: Client, action: Int) {
        if (client.mac in protected) {
            status.text = "Safety block: protected device cannot be changed."
            return
        }
        val verb = if (action == 1) "BLOCK" else "UNBLOCK"
        AlertDialog.Builder(this)
            .setTitle("$verb ${client.mac}?")
            .setMessage("This runs the TV's vendor-specific XbhBlock middleware for ${client.ip}. Ensure the middleware is installed and action values are correct.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton(verb) { _, _ -> runAction(client, action) }
            .show()
    }

    private fun runAction(client: Client, action: Int) {
        if (client.mac in protected) { status.text = "Safety block: protected device cannot be changed."; return }
        status.text = "Running $action for ${client.mac} …"
        executor.execute {
            try {
                val ip = host.text.toString().trim()
                val adbPort = port.text.toString().trim().toInt()
                val shell = AdbShell(ip, adbPort)
                val command = "CLASSPATH=/data/local/tmp/classes.dex app_process /data/local/tmp XbhBlock ${client.mac} $action"
                val output = shell.exec(command)
                runOnUiThread { status.text = "Middleware response for ${client.mac}:\n${output.ifBlank { "(empty output)" }}" }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Action failed: ${e.message ?: e.javaClass.simpleName}" }
            }
        }
    }

    private fun text(value: String, size: Int, color: String, bold: Boolean): TextView = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(Color.parseColor(color))
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(3), 0, dp(3))
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; setOnClickListener { action() }
        setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(249, 115, 22))
    }
    private fun params() = LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(6) }
    private fun space(height: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    /** Minimal legacy ADB host client for unauthenticated TCP ADB daemons. */
    private class AdbShell(private val host: String, private val port: Int) {
        private val socket = Socket()
        private lateinit var input: DataInputStream
        private lateinit var output: DataOutputStream
        private var localId = 1
        private var remoteId = 0

        fun exec(command: String): String {
            socket.connect(InetSocketAddress(host, port), 5000)
            socket.soTimeout = 8000
            input = DataInputStream(socket.getInputStream())
            output = DataOutputStream(socket.getOutputStream())
            try {
                send("CNXN", 0x01000000, 4096, "host::features=cmd\u0000".toByteArray())
                var packet = readPacket()
                if (packet.command == "AUTH") throw IllegalStateException("TV ADB requires authentication/pairing; this app supports legacy unauthenticated TCP ADB only.")
                if (packet.command != "CNXN") throw IllegalStateException("Unexpected ADB handshake: ${packet.command}")
                val service = "shell:$command\u0000".toByteArray()
                send("OPEN", localId, 0, service)
                val buffer = ByteArrayOutputStream()
                var loops = 0
                while (loops++ < 300) {
                    packet = readPacket()
                    when (packet.command) {
                        "OKAY" -> { remoteId = packet.arg0 }
                        "WRTE" -> {
                            buffer.write(packet.payload)
                            send("OKAY", localId, packet.arg0, ByteArray(0))
                        }
                        "CLSE" -> {
                            send("CLSE", localId, packet.arg0, ByteArray(0))
                            break
                        }
                        "FAIL" -> throw IllegalStateException(String(packet.payload))
                    }
                }
                return buffer.toString(Charsets.UTF_8.name()).take(16000)
            } finally { try { socket.close() } catch (_: Exception) {} }
        }

        private data class Packet(val command: String, val arg0: Int, val arg1: Int, val payload: ByteArray)
        private fun send(name: String, a0: Int, a1: Int, payload: ByteArray) {
            val cmd = name.toByteArray(Charsets.US_ASCII).let { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).int }
            val checksum = payload.fold(0) { sum, b -> sum + (b.toInt() and 0xff) }
            output.writeIntLE(cmd); output.writeIntLE(a0); output.writeIntLE(a1)
            output.writeIntLE(payload.size); output.writeIntLE(checksum); output.writeIntLE(cmd xor -1)
            output.write(payload); output.flush()
        }
        private fun readPacket(): Packet {
            val cmdInt = input.readIntLE()
            val a0 = input.readIntLE(); val a1 = input.readIntLE(); val length = input.readIntLE()
            input.readIntLE(); val magic = input.readIntLE()
            if (magic != (cmdInt xor -1) || length < 0 || length > 1024 * 1024) throw IllegalStateException("Invalid ADB packet")
            val payload = ByteArray(length); input.readFully(payload)
            val nameBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(cmdInt).array()
            return Packet(String(nameBytes, Charsets.US_ASCII), a0, a1, payload)
        }
        private fun DataOutputStream.writeIntLE(v: Int) { write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()) }
        private fun DataInputStream.readIntLE(): Int = Integer.reverseBytes(readInt())
    }
}
