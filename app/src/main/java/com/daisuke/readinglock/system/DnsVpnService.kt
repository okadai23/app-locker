package com.daisuke.readinglock.system

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.daisuke.readinglock.AppGraph
import com.daisuke.readinglock.domain.DnsFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * DNS だけを横取りするローカル VPN。§5.2
 *
 * tun には 10.111.222.3/32 宛(= addDnsServer で配った解決先)だけを流す。読んだ IPv4/UDP
 * パケットから DNS 問い合わせを取り出し、ブロック対象なら NXDOMAIN を合成して書き戻す。
 * それ以外は protect() した素の UDP ソケットで上流に投げ、返答をそのまま中継する。
 * TCP DNS は素通し(v1 では許容)。
 */
class DnsVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunnel: ParcelFileDescriptor? = null
    private var output: FileOutputStream? = null

    @Volatile private var blocked: Set<String> = emptySet()
    @Volatile private var achieved = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // VPN 確立中はシステムにバインドされ stopService では死なないため、明示アクションで落とす
        if (intent?.action == ACTION_STOP) {
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        if (tunnel == null) establish()
        return START_STICKY
    }

    override fun onRevoke() {
        // 他アプリが VPN を張った。自動再接続はしない。§9-4
        CoroutineScope(Dispatchers.IO).launch { AppGraph.settings.addWarning(Permissions.VPN) }
        Notifications.warning(this, "サイトブロックが停止しました", "他のVPNアプリとは併用できません")
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        shutdown()
        scope.cancel()
        super.onDestroy()
    }

    /** tun を閉じれば読み取りループも EOF で止まる。scope は onDestroy で畳む */
    private fun shutdown() {
        isRunning = false
        runCatching { tunnel?.close() }
        tunnel = null
    }

    private fun establish() {
        val pfd = runCatching {
            Builder()
                .setSession("読書ロック")
                .addAddress(TUN_ADDRESS, 32)
                .addDnsServer(DNS_PROXY)
                .addRoute(DNS_PROXY, 32) // DNS 宛だけ。全トラフィックは通さない
                .setBlocking(true)
                .establish()
        }.getOrNull()
        if (pfd == null) {
            stopSelf()
            return
        }
        tunnel = pfd
        output = FileOutputStream(pfd.fileDescriptor)
        isRunning = true

        scope.launch {
            AppGraph.db.blockedDomains().observeAll().collect {
                blocked = DnsFilter.activeDomains(it, System.currentTimeMillis())
            }
        }
        scope.launch {
            // 達成後は次の 4:00 まで素通し。§3
            AppGraph.engine.today.collect { achieved = it.unlocked }
        }
        scope.launch { readLoop(FileInputStream(pfd.fileDescriptor)) }
    }

    private suspend fun readLoop(input: FileInputStream) {
        val buffer = ByteArray(MTU)
        while (scope.isActive) {
            val length = runCatching { input.read(buffer) }.getOrDefault(-1)
            if (length <= 0) break
            val packet = buffer.copyOf(length)
            scope.launch { handle(packet) }
        }
    }

    private fun handle(request: ByteArray) {
        val query = IpV4Udp.dnsPayload(request) ?: return
        val question = DnsFilter.question(query)
        val response = if (question != null && !achieved && DnsFilter.isBlocked(question.name, blocked)) {
            DnsFilter.nxDomain(query, question)
        } else {
            forward(query) ?: return
        }
        val reply = IpV4Udp.reply(request, response) ?: return
        synchronized(this) { runCatching { output?.write(reply) } }
    }

    private fun forward(query: ByteArray): ByteArray? = runCatching {
        DatagramSocket().use { socket ->
            protect(socket)
            socket.soTimeout = UPSTREAM_TIMEOUT_MS
            socket.send(DatagramPacket(query, query.size, InetAddress.getByName(UPSTREAM), 53))
            val buffer = ByteArray(MTU)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            buffer.copyOf(response.length)
        }
    }.getOrNull()

    companion object {
        private const val TUN_ADDRESS = "10.111.222.1"
        private const val DNS_PROXY = "10.111.222.3"
        // ponytail: 上流は固定。端末の DNS を引くには ACCESS_NETWORK_STATE が要る。困ったら差し替える
        private const val UPSTREAM = "8.8.8.8"
        private const val UPSTREAM_TIMEOUT_MS = 5_000
        private const val MTU = 4096

        @Volatile
        var isRunning = false
            private set

        /** 許可済みなら null。null 以外は startActivityForResult に渡す */
        fun prepareIntent(context: Context): Intent? = VpnService.prepare(context)

        fun start(context: Context) {
            runCatching { context.startService(Intent(context, DnsVpnService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(Intent(context, DnsVpnService::class.java).setAction(ACTION_STOP))
            }
        }

        private const val ACTION_STOP = "com.daisuke.readinglock.STOP_VPN"
    }
}

/** tun から読んだ IPv4/UDP パケットの解釈と応答の組み立て */
private object IpV4Udp {

    /** DNS(dst 53)の UDP ペイロード。IPv4/UDP でなければ null */
    fun dnsPayload(packet: ByteArray): ByteArray? {
        val ihl = headerLength(packet) ?: return null
        if (readShort(packet, ihl + 2) != 53) return null
        val length = readShort(packet, ihl + 4)
        val end = (ihl + length).coerceAtMost(packet.size)
        if (end <= ihl + 8) return null
        return packet.copyOfRange(ihl + 8, end)
    }

    /** 送信元/宛先を入れ替えた応答パケット */
    fun reply(request: ByteArray, payload: ByteArray): ByteArray? {
        val ihl = headerLength(request) ?: return null
        val out = ByteArray(ihl + 8 + payload.size)
        request.copyInto(out, 0, 0, ihl + 8)
        payload.copyInto(out, ihl + 8)

        writeShort(out, 2, out.size) // total length
        out[8] = 64 // TTL
        for (i in 0 until 4) { // src <-> dst
            out[12 + i] = request[16 + i]
            out[16 + i] = request[12 + i]
        }
        writeShort(out, 10, 0)
        writeShort(out, 10, checksum(out, ihl))

        for (i in 0 until 2) { // ポートを入れ替え
            out[ihl + i] = request[ihl + 2 + i]
            out[ihl + 2 + i] = request[ihl + i]
        }
        writeShort(out, ihl + 4, 8 + payload.size)
        writeShort(out, ihl + 6, 0) // UDP チェックサムは IPv4 では省略可
        return out
    }

    /** IPv4 かつ UDP なら IP ヘッダ長 */
    private fun headerLength(packet: ByteArray): Int? {
        if (packet.size < 28) return null
        if ((packet[0].toInt() shr 4) and 0x0F != 4) return null
        if (packet[9].toInt() and 0xFF != 17) return null
        val ihl = (packet[0].toInt() and 0x0F) * 4
        return if (ihl < 20 || packet.size < ihl + 8) null else ihl
    }

    private fun checksum(b: ByteArray, length: Int): Int {
        var sum = 0
        var i = 0
        while (i + 1 < length) {
            sum += ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < length) sum += (b[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    private fun readShort(b: ByteArray, i: Int): Int =
        ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    private fun writeShort(b: ByteArray, i: Int, value: Int) {
        b[i] = (value shr 8).toByte()
        b[i + 1] = value.toByte()
    }
}
