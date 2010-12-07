
import java.util.*;
import javax.mail.*;
import javax.mail.internet.*;
import com.sun.mail.imap.*;

public class fix_empty_headers{
    static String user = "";
    static String password = "";

    public static void main(String args[]){
        for(int flag = 0; flag < args.length; flag++){
            if(args[flag].equals("-u")){
                user = args[++flag];
            }else if(args[flag].equals("-p")){
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
            System.out.println(store.getDefaultFolder().list());
            Folder[] pn = store.getPersonalNamespaces();
            for(Folder folder:pn){
                System.out.println('1');
                System.out.println(folder);
                System.out.println(folder.getFullName());
            }
            Folder[] sn = store.getSharedNamespaces();
            for(Folder folder:sn){
                System.out.println('2');
                System.out.println(folder);
                System.out.println(folder.getName());
            }
            Folder[] un = store.getUserNamespaces(user);
            for(Folder folder:un){
                System.out.println('3');
                System.out.println(folder);
                System.out.println(folder.getName());
            }

            Folder inbox = store.getFolder("Inbox");
            inbox.open(Folder.READ_ONLY);
                System.out.println(inbox.getFullName());
                System.out.println(inbox.getURLName());
            Message messages[] = inbox.getMessages();
            for(Message message:messages){
                IMAPMessage im = (IMAPMessage)message;
                System.out.println();
                System.out.println("message: " + im);
                System.out.println("sender: " + im.getSender());
                System.out.println("from: " + im.getFrom().length);
                System.out.println("from: " + im.getFrom()[0]);
                System.out.println("recipients: " + im.getAllRecipients());
                System.out.println("to: " + im.getRecipients(Message.RecipientType.TO));
                System.out.println("cc: " + im.getRecipients(Message.RecipientType.CC));
                System.out.println("bcc: " + im.getRecipients(Message.RecipientType.BCC));
                System.out.println("headerlines: " + im.getAllHeaderLines());
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