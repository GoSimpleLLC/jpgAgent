/*
 * Copyright (c) 2016, Adam Brusselback
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.gosimple.jpgagent.email;

import com.gosimple.jpgagent.Config;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

public class EmailUtil
{
    public static void sendEmailFromNoReply(String[] to, String subject, String body) {
        sendEmail(to, Config.INSTANCE.smtp_email, subject, body);
    }

    private static void sendEmail(String[] to, String from, String subject, String body) {
        try
        {
            final Session session;
            Properties emailProp = System.getProperties();
            emailProp.put("mail.smtp.host", Config.INSTANCE.smtp_host);
            emailProp.put("mail.smtp.port", Config.INSTANCE.smtp_port);

            if (Config.INSTANCE.smtp_ssl)
            {
                emailProp.put("mail.smtp.socketFactory.port", Config.INSTANCE.smtp_port);
                emailProp.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            }
            else
            {
                emailProp.put("mail.smtp.port", Config.INSTANCE.smtp_port);
            }

            if (null != Config.INSTANCE.smtp_user)
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
                MimeMessage message = new MimeMessage(session);
                message.setFrom(new InternetAddress(from));
                InternetAddress[] toAddress = new InternetAddress[to.length];

                // To get the array of addresses
                for (int i = 0; i < to.length; i++)
                {
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
            Config.INSTANCE.logger.error("An error occurred when sending email. Please check your configuration.");
            Config.INSTANCE.logger.error(e.getMessage());
        }
    }
}
