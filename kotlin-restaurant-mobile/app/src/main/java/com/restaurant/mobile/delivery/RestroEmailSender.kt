package com.restaurant.mobile.delivery

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Autonomous Direct Gmail SMTP Dispatch Engine.
 * Sends authentic email OTPs and order updates directly from the Android device
 * without requiring an external hosted server.
 */
object RestroEmailSender {
    private const val TAG = "RestroEmailSender"
    private const val SMTP_HOST = "smtp.gmail.com"
    private const val SMTP_PORT = 587
    private const val SENDER_EMAIL = "techagarwal33@gmail.com"
    private const val SENDER_PASS = "akpgqmqacornedlb"
    private const val SENDER_NAME = "Restro Food App"

    suspend fun sendOtpEmail(recipientEmail: String, otpCode: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val subject = "Your Restro Verification Code: $otpCode"
        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="utf-8"></head>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #0F172A; color: #F8FAFC; padding: 24px; margin: 0;">
                <div style="max-width: 480px; margin: 0 auto; background: #1E293B; border-radius: 16px; padding: 32px; border: 1px solid #334155; text-align: center;">
                    <h1 style="color: #FF5A36; font-size: 28px; margin: 0 0 8px 0; font-weight: 800;">Restro</h1>
                    <p style="color: #94A3B8; font-size: 14px; margin-top: 0;">Authentication &amp; Verification Service</p>
                    <div style="margin: 28px 0; padding: 20px; background: #0F172A; border-radius: 12px; border: 1px dashed #FF5A36;">
                        <span style="font-size: 36px; font-weight: 800; letter-spacing: 8px; color: #38BDF8;">$otpCode</span>
                    </div>
                    <p style="color: #CBD5E1; font-size: 14px; line-height: 1.6;">Use this 6-digit one-time password to verify your account. This code is valid for <strong>5 minutes</strong>.</p>
                    <div style="margin-top: 24px; padding-top: 20px; border-top: 1px solid #334155;">
                        <p style="color: #64748B; font-size: 12px; margin: 0;">If you did not request this verification code, please ignore this email.</p>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        sendHtmlEmail(recipientEmail, subject, htmlContent)
    }

    suspend fun sendOrderConfirmationEmail(
        recipientEmail: String,
        orderId: String,
        restaurantName: String,
        totalRupees: Long
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val subject = "Order Confirmed: #$orderId at $restaurantName"
        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="utf-8"></head>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #0F172A; color: #F8FAFC; padding: 24px; margin: 0;">
                <div style="max-width: 480px; margin: 0 auto; background: #1E293B; border-radius: 16px; padding: 32px; border: 1px solid #334155; text-align: center;">
                    <h1 style="color: #10B981; font-size: 26px; margin: 0 0 8px 0;">Order Confirmed!</h1>
                    <p style="color: #94A3B8; font-size: 14px; margin-top: 0;">Order #$orderId has been received by <strong>$restaurantName</strong>.</p>
                    <div style="margin: 24px 0; padding: 16px; background: #0F172A; border-radius: 12px; text-align: left;">
                        <p style="margin: 4px 0; color: #CBD5E1;"><strong>Restaurant:</strong> $restaurantName</p>
                        <p style="margin: 4px 0; color: #CBD5E1;"><strong>Total Amount:</strong> ₹$totalRupees</p>
                        <p style="margin: 4px 0; color: #34D399;"><strong>Status:</strong> Preparing in Kitchen</p>
                    </div>
                    <p style="color: #94A3B8; font-size: 13px;">You can track live GPS delivery updates directly within the Restro app.</p>
                </div>
            </body>
            </html>
        """.trimIndent()

        sendHtmlEmail(recipientEmail, subject, htmlContent)
    }

    suspend fun sendHtmlEmail(recipient: String, subject: String, htmlBody: String): Result<Boolean> = withContext(Dispatchers.IO) {
        var rawSocket: Socket? = null
        var sslSocket: SSLSocket? = null
        try {
            rawSocket = Socket()
            rawSocket.connect(InetSocketAddress(SMTP_HOST, SMTP_PORT), 10000)
            rawSocket.soTimeout = 12000

            var reader = BufferedReader(InputStreamReader(rawSocket.getInputStream(), "UTF-8"))
            var writer = BufferedWriter(OutputStreamWriter(rawSocket.getOutputStream(), "UTF-8"))

            fun sendLine(line: String) {
                writer.write(line + "\r\n")
                writer.flush()
            }

            fun readResponse(): String {
                val sb = StringBuilder()
                while (true) {
                    val line = reader.readLine() ?: break
                    sb.append(line).append("\n")
                    if (line.length >= 4 && line[3] == ' ') break
                }
                return sb.toString()
            }

            readResponse() // 220 banner

            sendLine("EHLO localhost")
            readResponse()

            sendLine("STARTTLS")
            val startTlsResp = readResponse()
            if (!startTlsResp.startsWith("220")) {
                throw IllegalStateException("STARTTLS failed: $startTlsResp")
            }

            val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
            sslSocket = sslFactory.createSocket(rawSocket, SMTP_HOST, SMTP_PORT, true) as SSLSocket
            sslSocket.startHandshake()

            reader = BufferedReader(InputStreamReader(sslSocket.inputStream, "UTF-8"))
            writer = BufferedWriter(OutputStreamWriter(sslSocket.outputStream, "UTF-8"))

            sendLine("EHLO localhost")
            readResponse()

            sendLine("AUTH LOGIN")
            readResponse()

            sendLine(Base64.encodeToString(SENDER_EMAIL.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            readResponse()

            sendLine(Base64.encodeToString(SENDER_PASS.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            val authResp = readResponse()
            if (!authResp.startsWith("235")) {
                throw IllegalStateException("Authentication failed: $authResp")
            }

            sendLine("MAIL FROM:<$SENDER_EMAIL>")
            readResponse()

            sendLine("RCPT TO:<${recipient.trim()}>")
            val rcptResp = readResponse()
            if (!rcptResp.startsWith("250")) {
                throw IllegalStateException("Recipient rejected: $rcptResp")
            }

            sendLine("DATA")
            readResponse()

            val mimeData = buildString {
                append("From: \"$SENDER_NAME\" <$SENDER_EMAIL>\r\n")
                append("To: <${recipient.trim()}>\r\n")
                append("Subject: $subject\r\n")
                append("MIME-Version: 1.0\r\n")
                append("Content-Type: text/html; charset=UTF-8\r\n")
                append("\r\n")
                append(htmlBody)
                append("\r\n.\r\n")
            }
            writer.write(mimeData)
            writer.flush()

            val dataResp = readResponse()
            if (!dataResp.startsWith("250")) {
                throw IllegalStateException("Data dispatch failed: $dataResp")
            }

            sendLine("QUIT")
            runCatching { readResponse() }

            Log.i(TAG, "Email successfully dispatched to $recipient via Gmail SMTP (techagarwal33@gmail.com)")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send email to $recipient: ${e.message}", e)
            Result.failure(e)
        } finally {
            runCatching { sslSocket?.close() }
            runCatching { rawSocket?.close() }
        }
    }
}
