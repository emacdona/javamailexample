package com.example.javamail

import com.icegreen.greenmail.junit.GreenMailRule
import com.icegreen.greenmail.util.GreenMailUtil
import com.icegreen.greenmail.util.ServerSetupTest
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.slf4j.LoggerFactory
import java.util.*
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class MailTest {
    val log = LoggerFactory.getLogger(MailTest::class.java)

    @Rule @JvmField
    val greenMail = GreenMailRule(arrayOf(ServerSetupTest.SMTP, ServerSetupTest.POP3))

    fun sendMessage(): MimeMessage {
        val smtpProps = Properties();
        smtpProps.put("mail.smtp.host", "localhost");
        smtpProps.put("mail.smtp.port", greenMail.getSmtp().getPort())
        smtpProps.put("mail.smtp.from", "ed-envelope@example.com")

        val session = Session.getInstance(smtpProps)

        val msg = MimeMessage(session)
        msg.setFrom("ed@example.com")
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse("jane@example.com"))
        msg.setSubject("Subject")
        msg.setText("some body")

        Transport.send(
                msg,
                InternetAddress.parse("jane-envelope@example.com"),
                "ed",
                "password")

        return msg
    }

    @Test
    fun test(){
        log.info("Testing email...")

        // Let greenmail know how we plan to authenticate when sending
        greenMail.setUser("ed", "password")

        // Set up the user we will assert actually gets the email
        greenMail.setUser("jane-envelope@example.com", "password")

        // Set up the user we will assert actually DOES NOT get the email
        greenMail.setUser("jane@example.com", "password")

        // SEND THE MESSAGE
        val msg = sendMessage()


        val retrievedMsg = greenMail.getReceivedMessages()[0]

        // Test Header fields (note, To/From email addresses are NOT envelope addresses)
        Assert.assertEquals("some body", GreenMailUtil.getBody(retrievedMsg))
        Assert.assertEquals("Subject", retrievedMsg.getSubject())
        Assert.assertEquals(1, retrievedMsg.getFrom().size )
        Assert.assertEquals(1, retrievedMsg.getRecipients(Message.RecipientType.TO).size )
        Assert.assertEquals( "ed@example.com",
                retrievedMsg.getFrom().first().toString())
        Assert.assertEquals( "jane@example.com",
                retrievedMsg.getRecipients(Message.RecipientType.TO).first().toString())


        log.info("Whole Message: \n{}\n\n", GreenMailUtil.getWholeMessage(msg))

        // Now, prove that the ENVELOPE recipient was the true recipient of the email, not the FROM: header recipient
        // Sadly, I could find no way to prove that the SENDER of the email was ENVELOPE sender
        val popProps = Properties();
        popProps.put("mail.pop3.host", "localhost");
        popProps.put("mail.pop3.port", greenMail.getPop3().getPort())

        val popSession = Session.getInstance(popProps)
        val store = popSession.getStore("pop3")
        var inbox: Folder

        store.connect("jane-envelope@example.com", "password")
        inbox = store.getFolder("INBOX")
        inbox.open(Folder.READ_ONLY)
        Assert.assertEquals(1, inbox.getMessages().size)
        Assert.assertEquals("jane@example.com", inbox.getMessages().first().getAllRecipients().first().toString())
        store.close()

        store.connect("jane@example.com", "password")
        inbox = store.getFolder("INBOX")
        inbox.open(Folder.READ_ONLY)
        // No messages for jane@example.com!
        Assert.assertEquals(0, inbox.getMessages().size)
        store.close()
    }
}
