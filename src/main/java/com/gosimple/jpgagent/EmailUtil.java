package com.gosimple.jpgagent;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

class EmailUtil
{
    public static void sendEmailFromNoReply(String[] to, String subject, String body) {
        sendEmail(to, Config.INSTANCE.smtp_email, subject, body);
    }

    private static void sendEmail(String[] to, String from, String subject, String body) {
        final Session session;
        Properties emailProp = System.getProperties();
        emailProp.put("mail.smtp.host", Config.INSTANCE.smtp_host);
        emailProp.put("mail.smtp.port", Config.INSTANCE.smtp_port);

        if(Config.INSTANCE.smtp_ssl)
        {
            emailProp.put("mail.smtp.socketFactory.port", Config.INSTANCE.smtp_port);
            emailProp.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        }
        else
        {
            emailProp.put("mail.smtp.port", Config.INSTANCE.smtp_port);
        }

        if(null != Config.INSTANCE.smtp_user)
        {
            emailProp.put("mail.smtp.auth", "true");
            Authenticator authenticator = new Authenticator()
            {
                @Override
                protected PasswordAuthentication getPasswordAuthentication()
                {
                    return new PasswordAuthentication(Config.INSTANCE.smtp_user, Config.INSTANCE.smtp_password);
                }
            };
            session = Session.getDefaultInstance(emailProp, authenticator);
        }
        else
        {
            emailProp.put("mail.smtp.auth", "false");
            session = Session.getDefaultInstance(emailProp);
        }


        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            InternetAddress[] toAddress = new InternetAddress[to.length];

            // To get the array of addresses
            for( int i = 0; i < to.length; i++ ) {
                toAddress[i] = new InternetAddress(to[i]);
            }

            for (InternetAddress toAddres : toAddress)
            {
                message.addRecipient(Message.RecipientType.TO, toAddres);
            }

            message.setSubject(subject);
            message.setContent(body, "text/html; charset=utf-8");
            Transport.send(message);
        }
        catch (Exception e) {
            Config.INSTANCE.logger.error(e.getMessage());
        }
    }
}
