
import java.util.*;
import javax.mail.*;
import javax.mail.internet.*;
import com.sun.mail.imap.*;

public class fix_empty_headers{
    static String user = "";
    static String password = "";
    static String mailbox = "[Gmail]/All Mail";

    public static void main(String args[]){
        for(int flag = 0; flag < args.length; flag++){
            if(args[flag].equals("-u") && flag + 1 < args.length){
                user = args[++flag];
            }else if(args[flag].equals("-p") && flag + 1 < args.length){
                password = args[++flag];
            }
        }
        
        if(user.isEmpty() || password.isEmpty()){
            System.out.println("Usage: fix_empty_headers -u user -p password");
            System.exit(1);
        }

        Properties props = System.getProperties();
        props.setProperty("mail.store.protocol", "imaps");
        try{
            Session session = Session.getDefaultInstance(props, null);
            Store store = session.getStore("imaps");
            store.connect("imap.gmail.com", user, password);
            System.out.println(store);
            System.out.println(store.getDefaultFolder().getURLName());
            Folder[] dfl = store.getDefaultFolder().list();
            for(Folder folder:dfl){
                System.out.println(folder);
                System.out.println(folder.getFullName());
                if(folder.list().length > 0){
                    for(Folder child:folder.list()){
                        System.out.println(child);
                        System.out.println(child.getFullName());
                    }
                }
            }

            // parses "All Mail"
            // this does not include messages in spam, trash, or drafts
            Folder allmail = store.getFolder(mailbox);
            allmail.open(Folder.READ_ONLY);
            System.out.println(allmail.getFullName());
            System.out.println(allmail.getURLName());

            Message messages[] = allmail.getMessages();
            for(Message message:messages){
                IMAPMessage im = (IMAPMessage)message;
                System.out.println();
                System.out.println("message: " + im.getSubject());

                // TODO: use getHeader() instead of simpler getSender-type
                // methods because they make assumptions and will return 
                // values from other fields, e.g. getReplyTo will return the
                // contents of getFrom if there is no reply-to header. Values
                // will wind up being added in places that they shouldn't be
                // after cleaning.
                System.out.println("sender: " + im.getSender());
                if(im.getFrom() != null){
                    System.out.println("from: " + im.getFrom().length);
                    for(Address fromAddress:im.getFrom()){
                        System.out.println("from: " + fromAddress);
                    }
                }
                if(im.getRecipients(Message.RecipientType.TO) != null){
                    System.out.println("to: " + im.getRecipients(Message.RecipientType.TO).length);
                    for(Address toAddress:im.getRecipients(Message.RecipientType.TO)){
                        System.out.println("to: " + toAddress);
                    }
                }
                if(im.getRecipients(Message.RecipientType.CC) != null){
                    System.out.println("cc: " + im.getRecipients(Message.RecipientType.CC).length);
                    for(Address ccAddress:im.getRecipients(Message.RecipientType.CC)){
                        System.out.println("cc: " + ccAddress);
                    }
                }
                if(im.getRecipients(Message.RecipientType.BCC) != null){
                    System.out.println("bcc: " + im.getRecipients(Message.RecipientType.BCC).length);
                    for(Address bccAddress:im.getRecipients(Message.RecipientType.BCC)){
                        System.out.println("bcc: " + bccAddress);
                    }
                }
                if(im.getReplyTo() != null){
                    System.out.println("reply-to: " + im.getReplyTo().length);
                    for(Address replyToAddress:im.getReplyTo()){
                        System.out.println("reply-to: " + replyToAddress);
                    }
                }
                Enumeration allHeaderLines = im.getAllHeaderLines();
                while(allHeaderLines.hasMoreElements()){
                    System.out.println("allheaderlines: " + allHeaderLines.nextElement());
                }
//                Enumeration allHeaders = im.getAllHeaders();
//                while(allHeaders.hasMoreElements()){
//                    System.out.println("allheaders: " + allHeaders.nextElement());
//                }
            }
        }catch(NoSuchProviderException e){
            e.printStackTrace();
            System.exit(1);
        }catch(MessagingException e){
            e.printStackTrace();
            System.exit(2);
        }
    }
}