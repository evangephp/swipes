package com.swipes.adb

import android.content.Context
import android.os.Build
import android.sun.security.x509.AlgorithmId
import android.sun.security.x509.CertificateAlgorithmId
import android.sun.security.x509.CertificateExtensions
import android.sun.security.x509.CertificateIssuerName
import android.sun.security.x509.CertificateSerialNumber
import android.sun.security.x509.CertificateSubjectName
import android.sun.security.x509.CertificateValidity
import android.sun.security.x509.CertificateVersion
import android.sun.security.x509.CertificateX509Key
import android.sun.security.x509.KeyIdentifier
import android.sun.security.x509.SubjectKeyIdentifierExtension
import android.sun.security.x509.X500Name
import android.sun.security.x509.X509CertImpl
import android.sun.security.x509.X509CertInfo
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit

/** adb client identity (RSA key + self-signed cert) persisted in app-private storage. */
class AdbManager private constructor(context: Context) : AbsAdbConnectionManager() {

    private val privateKey: PrivateKey
    private val certificate: Certificate

    init {
        api = Build.VERSION.SDK_INT
        setTimeout(10, TimeUnit.SECONDS)
        val dir = File(context.filesDir, "adb").apply { mkdirs() }
        val keyFile = File(dir, "key.pk8")
        val certFile = File(dir, "cert.der")
        if (keyFile.exists() && certFile.exists()) {
            privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
            certificate = certFile.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
        } else {
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, SecureRandom()) }.generateKeyPair()
            privateKey = pair.private
            certificate = selfSign(pair.public, pair.private)
            keyFile.writeBytes(privateKey.encoded)
            certFile.writeBytes(certificate.encoded)
        }
    }

    override fun getPrivateKey() = privateKey

    override fun getCertificate() = certificate

    override fun getDeviceName() = "Swipes"

    companion object {
        @Volatile private var instance: AdbManager? = null

        fun get(context: Context): AdbManager =
            instance ?: synchronized(this) {
                instance ?: AdbManager(context.applicationContext).also { instance = it }
            }

        private fun selfSign(publicKey: java.security.PublicKey, privateKey: PrivateKey): Certificate {
            val algorithm = "SHA512withRSA"
            val name = X500Name("CN=Swipes")
            val notBefore = Date()
            val notAfter = Date(notBefore.time + TimeUnit.DAYS.toMillis(365L * 30))
            val info = X509CertInfo().apply {
                set("version", CertificateVersion(2))
                set("serialNumber", CertificateSerialNumber(SecureRandom().nextInt() and Int.MAX_VALUE))
                set("algorithmID", CertificateAlgorithmId(AlgorithmId.get(algorithm)))
                set("subject", CertificateSubjectName(name))
                set("issuer", CertificateIssuerName(name))
                set("key", CertificateX509Key(publicKey))
                set("validity", CertificateValidity(notBefore, notAfter))
                set("extensions", CertificateExtensions().apply {
                    set("SubjectKeyIdentifier", SubjectKeyIdentifierExtension(KeyIdentifier(publicKey).identifier))
                })
            }
            return X509CertImpl(info).apply { sign(privateKey, algorithm) }
        }
    }
}
