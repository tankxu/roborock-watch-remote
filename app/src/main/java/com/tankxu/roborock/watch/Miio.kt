package com.tankxu.roborock.watch

import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 小米 miIO 本地协议客户端(UDP 54321 + AES-128-CBC + MD5)。
 * 手表直接发给扫地机, 不经云、不经电脑。线程安全(synchronized)。
 */
class Miio(
    @Volatile var ip: String,
    tokenHex: String,
    private val did: Long,
    private val port: Int = 54321
) {
    private val token = hexToBytes(tokenHex)
    private val key = md5(token)
    private val iv = md5(key + token)
    private var didBytes: ByteArray? = null
    private var stamp = 0L
    private var t0 = 0L
    private var mid = 100
    private val sock = DatagramSocket().apply { soTimeout = 3000 }   // 手表WiFi延迟大, 放宽
    private val lock = Any()

    val connected: Boolean get() = didBytes != null

    private fun md5(b: ByteArray): ByteArray = MessageDigest.getInstance("MD5").digest(b)

    private fun aesEnc(data: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")   // 对 16 字节块等同 PKCS7
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return c.doFinal(data)
    }

    private fun helloPacket() = ByteArray(32).also {
        it[0] = 0x21; it[1] = 0x31; it[2] = 0x00; it[3] = 0x20
        for (i in 4 until 32) it[i] = 0xff.toByte()
    }

    private fun u32(b: ByteArray, off: Int): Long =
        ((b[off].toLong() and 0xff) shl 24) or ((b[off + 1].toLong() and 0xff) shl 16) or
            ((b[off + 2].toLong() and 0xff) shl 8) or (b[off + 3].toLong() and 0xff)

    /** 向某 IP 握手; did 匹配才算是目标扫地机 */
    private fun hello(target: String): Boolean = try {
        val addr = InetAddress.getByName(target)
        val h = helloPacket()
        sock.send(DatagramPacket(h, h.size, addr, port))
        val buf = ByteArray(1024)
        val resp = DatagramPacket(buf, buf.size)
        sock.receive(resp)
        if (u32(resp.data, 8) != did) {
            false
        } else {
            didBytes = resp.data.copyOfRange(8, 12)
            stamp = u32(resp.data, 12)
            t0 = System.currentTimeMillis()
            ip = target
            true
        }
    } catch (e: Exception) {
        false
    }

    /** 扫本机所在 /24 网段, 找 did 匹配的设备(防 DHCP 换 IP) */
    private fun scan(localIp: String): String? = try {
        val pre = localIp.substringBeforeLast('.')
        val ss = DatagramSocket().apply { soTimeout = 50 }
        val h = helloPacket()
        for (i in 1..254) {
            try { ss.send(DatagramPacket(h, h.size, InetAddress.getByName("$pre.$i"), port)) } catch (_: Exception) {}
        }
        ss.soTimeout = 1500
        var found: String? = null
        val deadline = System.currentTimeMillis() + 2500
        val buf = ByteArray(1024)
        while (System.currentTimeMillis() < deadline) {
            try {
                val p = DatagramPacket(buf, buf.size)
                ss.receive(p)
                if (u32(p.data, 8) == did) { found = p.address.hostAddress; break }
            } catch (_: Exception) { break }
        }
        ss.close()
        found
    } catch (e: Exception) {
        null
    }

    /** 连接: 先试已知 IP, 不行扫网段 */
    fun connect(localIp: String?): Boolean = synchronized(lock) {
        repeat(3) { if (hello(ip)) return true }     // 手表WiFi抖动, 单次可能丢包, 重试
        if (localIp != null) {
            val f = scan(localIp)
            if (f != null && hello(f)) return true
        }
        didBytes = null
        false
    }

    /** 发命令; 未连接会自动尝试连接。返回是否成功 */
    fun send(method: String, params: JSONArray, localIp: String? = null): Boolean = synchronized(lock) {
        if (didBytes == null && !connect(localIp)) return false
        return try {
            mid++
            val cur = stamp + (System.currentTimeMillis() - t0) / 1000
            val payload = JSONObject().put("id", mid).put("method", method).put("params", params)
                .toString().toByteArray(Charsets.UTF_8)
            val enc = aesEnc(payload)
            val len = 32 + enc.size
            val header = ByteArray(16)
            header[0] = 0x21; header[1] = 0x31
            header[2] = ((len shr 8) and 0xff).toByte(); header[3] = (len and 0xff).toByte()
            System.arraycopy(didBytes!!, 0, header, 8, 4)
            header[12] = ((cur shr 24) and 0xff).toByte(); header[13] = ((cur shr 16) and 0xff).toByte()
            header[14] = ((cur shr 8) and 0xff).toByte(); header[15] = (cur and 0xff).toByte()
            val chk = md5(header + token + enc)
            val packet = header + chk + enc
            sock.send(DatagramPacket(packet, packet.size, InetAddress.getByName(ip), port))
            try { sock.receive(DatagramPacket(ByteArray(1024), 1024)) } catch (_: Exception) {}
            true
        } catch (e: Exception) {
            didBytes = null
            false
        }
    }

    fun arr(vararg items: Any): JSONArray = JSONArray().apply { items.forEach { put(it) } }

    companion object {
        fun hexToBytes(s: String): ByteArray =
            ByteArray(s.length / 2) { ((Character.digit(s[it * 2], 16) shl 4) + Character.digit(s[it * 2 + 1], 16)).toByte() }
    }
}
