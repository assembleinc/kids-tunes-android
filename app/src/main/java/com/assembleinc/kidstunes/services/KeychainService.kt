package com.assembleinc.kidstunes.services

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.*
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.security.auth.x500.X500Principal

/**
 * Created by Assemble, Inc. on 2019-05-23.
 */
class KeychainService(context: Context) {

    // region Member variables

    private val alias = byteArrayOf(66, 57, 50, 49, 52, 57, 52, 48, 45, 54, 50, 52, 56, 45, 52, 53, 48, 48, 45, 57, 56,
        49, 65, 45, 48, 54, 52, 53, 69, 69, 70, 51, 55, 55, 52, 68)

    private val sharedPreferences: SharedPreferences

    // endregion


    // region Android Keychain

    init {
        createKeys()
        sharedPreferences = context.getSharedPreferences(KEYCHAIN_PREFERENCES, Context.MODE_PRIVATE)
    }

    // endregion


    // region

    private fun getAlias(): String {
        return String(alias, StandardCharsets.UTF_8)
    }

    private fun getKeyStore(): KeyStore? {
        var keyStore: KeyStore?

        try {
            keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore!!.load(null)
        } catch (e: Exception) {
            keyStore = null
        }

        return keyStore
    }

    private fun createKeys() {
        val start = Calendar.getInstance()
        val end = Calendar.getInstance()
        end.add(Calendar.YEAR, 1)

        try {
            if (!getKeyStore()!!.containsAlias(getAlias())) {
                val generator = KeyPairGenerator.getInstance(KEY_ALGORITHM_RSA, KEYSTORE_PROVIDER)

                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    getAlias(), KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setCertificateSubject(X500Principal("CN=Sample Name, O=Android Authority"))
                    .setCertificateSerialNumber(BigInteger.ONE)
                    .setCertificateNotBefore(start.time)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                    .setCertificateNotAfter(end.time)
                    .build()

                generator.initialize(keyGenParameterSpec)
                generator.generateKeyPair()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun encryptString(textToCipher: String): String? {
        var encryptedString: String? = null

        try {
            val keyStore = getKeyStore()
            keyStore?.let {
                val privateKeyEntry = it.getEntry(getAlias(), null) as KeyStore.PrivateKeyEntry

                val inCipher = Cipher.getInstance(PADDING_RSA_ECB_PKCS1)
                inCipher.init(Cipher.ENCRYPT_MODE, privateKeyEntry.certificate.publicKey)

                val outputStream = ByteArrayOutputStream()
                val cipherOutputStream = CipherOutputStream(outputStream, inCipher)
                cipherOutputStream.write(textToCipher.toByteArray(StandardCharsets.UTF_8))
                cipherOutputStream.close()

                encryptedString = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
            }
        } catch (e: Exception) {
            encryptedString = null
        }

        return encryptedString
    }


    fun decryptString(cipherText: String): String? {
        var cipherInputStream: CipherInputStream? = null
        var decryptedString: String?

        try {
            val privateKeyEntry = getKeyStore()!!.getEntry(getAlias(), null) as KeyStore.PrivateKeyEntry

            val output = Cipher.getInstance(PADDING_RSA_ECB_PKCS1)
            output.init(Cipher.DECRYPT_MODE, privateKeyEntry.privateKey)
            cipherInputStream = CipherInputStream(
                ByteArrayInputStream(Base64.decode(cipherText, Base64.DEFAULT)), output
            )

            val values = ArrayList<Byte>()
            var nextByte = cipherInputStream.read()
            while (-1 != nextByte) {
                values.add(nextByte.toByte())
                nextByte = cipherInputStream.read()
            }

            val bytes = ByteArray(values.size)
            for (i in bytes.indices) {
                bytes[i] = values[i]
            }

            decryptedString = String(bytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            decryptedString = null
        } finally {
            try {
                cipherInputStream?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }

        return decryptedString
    }

    fun save(key: String, value: String) {
        sharedPreferences.edit().putString(key, encryptString(value)).apply()
    }

    fun fetch(key: String): String? {
        val value = sharedPreferences.getString(key, null)
        return if (null != value) decryptString(value) else value
    }

    fun delete(key: String) {
        sharedPreferences.edit().remove(key).apply()
    }

    // endregion

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALGORITHM_RSA = "RSA"
        private const val PADDING_RSA_ECB_PKCS1 = "RSA/ECB/PKCS1Padding"
        private val CANONICAL_NAME = KeychainService::class.java.canonicalName
        private val KEYCHAIN_PREFERENCES = "$CANONICAL_NAME.keychain_preferences"
        val KEY_MUSIC_USER_TOKEN = "$CANONICAL_NAME.music_user_token"
    }

}