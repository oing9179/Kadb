package com.oingdev.kadb.message

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.math.BigInteger
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.*
import java.security.interfaces.RSAPublicKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.Cipher

/**
 * This class encapsulates the ADB cryptography functions and provides
 * an interface for the storage and retrieval of keys.
 *
 * Modified from [AdbCrypto.java](https://github.com/cgutman/AdbLib/blob/master/src/com/cgutman/adblib/AdbCrypto.java)
 * @author Cameron Gutman
 */
class AdbKeyPair private constructor(val keyPair: KeyPair, val user: String, val host: String) {
    /**
     * Gets the RSA public key in ADB format.
     *
     * The key is base64 encoded with a user@host suffix and terminated with a NUL.
     * @return Byte array containing the RSA public key in ADB format.
     * @throws IOException If the key cannot be retrived
     */
    val publicKeyPayload: ByteArray = kotlin.run {
        val convertedKey =
                convertRsaPublicKeyToAdbFormat(keyPair.public as RSAPublicKey)
        val keyString = Base64.getEncoder().encodeToString(convertedKey) +
                " $user@$host\u0000"
        keyString.toByteArray(StandardCharsets.UTF_8)
    }

    /**
     * Signs the ADB SHA1 payload with the private key of this object.
     * @param payload SHA1 payload to sign
     * @return Signed SHA1 payload
     * @throws GeneralSecurityException If signing fails
     */
    @Throws(GeneralSecurityException::class)
    fun sign(payload: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keyPair.private)
        cipher.update(SIGNATURE_PADDING)
        return cipher.doFinal(payload)
    }

    /**
     * Saves the AdbKeyPair's key pair to the specified files.
     * @param privateKey The file to store the encoded private key
     * @param publicKey The file to store the encoded public key
     * @throws IOException If the files cannot be written
     */
    @Throws(IOException::class)
    fun saveAdbKeyPair(privateKey: File, publicKey: File) {
        val privOut = FileOutputStream(privateKey)
        val pubOut = FileOutputStream(publicKey)

        privOut.write(keyPair.private.encoded)
        pubOut.write(keyPair.public.encoded)

        privOut.close()
        pubOut.close()
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is AdbKeyPair) return false
        return keyPair == other.keyPair &&
                user == other.user &&
                host == other.host
    }

    override fun hashCode(): Int {
        return Objects.hash(Objects.hashCode(keyPair), user, host)
    }

    companion object {
        /** The ADB RSA key length in bits  */
        private const val KEY_LENGTH_BITS = 2048

        /** The ADB RSA key length in bytes  */
        private const val KEY_LENGTH_BYTES = KEY_LENGTH_BITS / 8

        /** The ADB RSA key length in words  */
        private const val KEY_LENGTH_WORDS = KEY_LENGTH_BYTES / 4

        /** The RSA signature padding as an int array  */
        private val SIGNATURE_PADDING_AS_INT = intArrayOf(
                0x00, 0x01, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                0xff, 0xff, 0xff, 0xff, 0x00, 0x30, 0x21, 0x30,
                0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a,
                0x05, 0x00, 0x04, 0x14
        )

        /** The RSA signature padding as a byte array  */
        private val SIGNATURE_PADDING = ByteArray(SIGNATURE_PADDING_AS_INT.size) {
            SIGNATURE_PADDING_AS_INT[it].toByte()
        }

        /**
         * Converts a standard RSAPublicKey object to the special ADB format
         * @param pubkey RSAPublicKey object to convert
         * @return Byte array containing the converted RSAPublicKey object
         */
        @Suppress("JoinDeclarationAndAssignment")
        private fun convertRsaPublicKeyToAdbFormat(pubkey: RSAPublicKey): ByteArray {
            /*
             * ADB literally just saves the RSAPublicKey struct to a file.
             *
             * typedef struct RSAPublicKey {
             * int len; // Length of n[] in number of uint32_t
             * uint32_t n0inv;  // -1 / n[0] mod 2^32
             * uint32_t n[RSANUMWORDS]; // modulus as little endian array
             * uint32_t rr[RSANUMWORDS]; // R^2 as little endian array
             * int exponent; // 3 or 65537
             * } RSAPublicKey;
             */

            /* ------ This part is a Java-ified version of RSA_to_RSAPublicKey from adb_host_auth.c ------ */
            val r32: BigInteger
            val r: BigInteger
            var rr: BigInteger
            var rem: BigInteger
            var n: BigInteger
            val n0inv: BigInteger

            r32 = BigInteger.ZERO.setBit(32)
            n = pubkey.modulus
            r = BigInteger.ZERO.setBit(KEY_LENGTH_WORDS * 32)
            rr = r.modPow(BigInteger.valueOf(2), n)
            rem = n.remainder(r32)
            n0inv = rem.modInverse(r32)

            val myN = IntArray(KEY_LENGTH_WORDS)
            val myRr = IntArray(KEY_LENGTH_WORDS)
            var res: Array<BigInteger>
            for (i in 0 until KEY_LENGTH_WORDS) {
                res = rr.divideAndRemainder(r32)
                rr = res[0]
                rem = res[1]
                myRr[i] = rem.toInt()

                res = n.divideAndRemainder(r32)
                n = res[0]
                rem = res[1]
                myN[i] = rem.toInt()
            }
            /* ------------------------------------------------------------------------------------------- */

            val buffer = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)

            buffer.putInt(KEY_LENGTH_WORDS)
            buffer.putInt(n0inv.negate().toInt())
            for (i in myN)
                buffer.putInt(i)
            for (i in myRr)
                buffer.putInt(i)
            buffer.putInt(pubkey.publicExponent.toInt())

            return buffer.array()
        }

        /**
         * Creates a new AdbKeyPair object from a key pair loaded from files.
         * @param privateKey File containing the RSA private key
         * @param publicKey File containing the RSA public key
         * @return New AdbKeyPair object
         * @throws IOException If the files cannot be read
         * @throws NoSuchAlgorithmException If an RSA key factory cannot be found
         * @throws InvalidKeySpecException If a PKCS8 or X509 key spec cannot be found
         */
        @Throws(IOException::class, NoSuchAlgorithmException::class, InvalidKeySpecException::class)
        fun loadAdbKeyPair(privateKey: File, publicKey: File): AdbKeyPair {
            val privKeyLength = privateKey.length().toInt()
            val pubKeyLength = publicKey.length().toInt()
            val privKeyBytes = ByteArray(privKeyLength)
            val pubKeyBytes = ByteArray(pubKeyLength)

            val privIn = FileInputStream(privateKey)
            val pubIn = FileInputStream(publicKey)

            privIn.read(privKeyBytes)
            pubIn.read(pubKeyBytes)

            privIn.close()
            pubIn.close()

            val keyFactory = KeyFactory.getInstance("RSA")
            val privateKeySpec = PKCS8EncodedKeySpec(privKeyBytes)
            val publicKeySpec = X509EncodedKeySpec(pubKeyBytes)

            val keyPair = KeyPair(
                    keyFactory.generatePublic(publicKeySpec),
                    keyFactory.generatePrivate(privateKeySpec)
            )
            return AdbKeyPair(keyPair, "unknown", "unknown")
        }

        /**
         * Creates a new AdbKeyPair object by generating a new key pair.
         * @return A new AdbKeyPair object
         * @throws NoSuchAlgorithmException If an RSA key factory cannot be found
         */
        @Throws(NoSuchAlgorithmException::class)
        fun generateAdbKeyPair(
                user: String = System.getProperty("user.name"),
                host: String = InetAddress.getLocalHost().hostName
        ): AdbKeyPair {
            val rsaKeyPairGenerator = KeyPairGenerator.getInstance("RSA")
            rsaKeyPairGenerator.initialize(KEY_LENGTH_BITS)
            return AdbKeyPair(rsaKeyPairGenerator.genKeyPair(), user, host)
        }
    }
}