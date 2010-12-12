
import java.io.*;
import java.util.*;
import java.net.*;
import javax.mail.*;
import javax.mail.internet.*;
import com.sun.mail.imap.*;
import com.google.gdata.client.docs.DocsService;
import com.google.gdata.data.docs.SpreadsheetEntry;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.util.ServiceException;

public class fix_empty_headers{
    // command line flags
    private static String user = "";
    private static String password = "";
    private static boolean checkInbox = false;
    private static boolean checkLabels = false;

    // folder names
    private static String mailbox = "[Gmail]/All Mail";
    private static String inboxName = "INBOX";
    private static String sentmailbox = "[Gmail]/Sent Mail";
    private static String trashbox = "[Gmail]/Trash";

    // list of labels
    private static HashSet<Folder> labels = new HashSet<Folder>();

    // list of all addresses
    private static HashSet<Address> addresses = new HashSet<Address>();

    private static void validateArgs(String args[]){
        for(int flag = 0; flag < args.length; flag++){
            if(args[flag].equalsIgnoreCase("-u") && flag + 1 < args.length){
                user = args[++flag];
            }else if(args[flag].equalsIgnoreCase("-p") && flag + 1 < args.length){
                password = args[++flag];
            }else if(args[flag].equalsIgnoreCase("-i")){
                checkInbox = true;
            }else if(args[flag].equalsIgnoreCase("-l")){
                checkLabels = true;
            }
        }

        if(user.isEmpty() || password.isEmpty() || (checkInbox == checkLabels)){
            System.out.println("Usage: fix_empty_headers -u user -p password -i/-l");
            System.out.println("-i : check the inbox and sent mail only");
            System.out.println("-l : check all labels and sent mail only");
            System.exit(1);
        }
    }

    // look through all folders under root, ignore system folders,
    // and store user's labels
    private static void getLabels(Store store){
        try{
            //System.out.println(store);
            Folder[] dfl = store.getDefaultFolder().list();
            for(Folder folder:dfl){
                String name = folder.getName();
                if(!(name.equalsIgnoreCase(inboxName) ||
                     name.equalsIgnoreCase("Junk E-mail") ||
                     name.equalsIgnoreCase("[Gmail]"))){
                    //System.out.println(folder);
                    labels.add(folder);
                }
            }
            System.out.println(labels);
        }catch(MessagingException e){
            e.printStackTrace();
            System.exit(1);
        }
    }

    // use getHeader() instead of simpler getSender() methods
    // because they make assumptions and will return values
    // from other fields, e.g. getReplyTo will return the
    // contents of getFrom if there is no reply-to header. Values
    // will wind up being added in places that they shouldn't be
    // after cleaning.
    private static void getHeaders(MimeMessage message, String headerType){
        try{
            String header = message.getHeader(headerType, ",");
            if(header != null){
                InternetAddress[] addressList = InternetAddress.parseHeader(header, true);
                //System.out.println("# " + headerType + ": " + addressList.length);
                for(InternetAddress address:addressList){
                    //System.out.println(headerType + ": " + address);
                    //System.out.println("\t" + address.getPersonal());
                    //System.out.println("\t" + address.getAddress());
                    // duplicates filtered out by HashSet
                    addresses.add(address);
                }
            }
        }catch(MessagingException e){
            e.printStackTrace();
            System.exit(1);
        }
    }

    // Get all the addresses for the various different types
    // of headers that can contain addresses. I think there
    // are still others like Resent-Sender, Resent-From,
    // Resent-Reply-To, Resent-To, Resent-Cc, and Resent-Bcc.
    private static void getAddresses(Folder folder){
        try{
            folder.open(Folder.READ_ONLY);
            Message messages[] = folder.getMessages();
            System.out.println("# of messages in " + folder.getFullName() + ": " + messages.length);

            for(Message message:messages){
                // cast to MimeMessage to be able to use getHeader() later
                MimeMessage mm = (MimeMessage)message;
                getHeaders(mm, "Sender");
                getHeaders(mm, "From");
                getHeaders(mm, "To");
                getHeaders(mm, "Cc");
                getHeaders(mm, "Bcc");
                getHeaders(mm, "Reply-To");
                System.out.println(addresses);

                //Enumeration allHeaderLines = mm.getAllHeaderLines();
                //while(allHeaderLines.hasMoreElements()){
                    //System.out.println("allheaderlines: " + allHeaderLines.nextElement());
                //}
            }

            folder.close(false);
        }catch(MessagingException e){
            e.printStackTrace();
            System.exit(1);
        }
    }

/*
            IMAPMessage im = (IMAPMessage)message;

            MimeMessage mm = new MimeMessage(im);
            if(mm.getHeader("To", ",") != null){
//                    System.out.println("to: " + im.getHeader("To", ","));
                InternetAddress[] toList = InternetAddress.parseHeader(mm.getHeader("To", ","), true);
                System.out.println("to: " + toList.length);
                for(InternetAddress toAddress:toList){
                    System.out.println("to: " + toAddress);
                    System.out.println("to: " + toAddress.getPersonal());
                    System.out.println("to: " + toAddress.getAddress());
                    //if(toAddress.getPersonal() != null && toAddress.getPersonal().equals("<Last, First>")){
                        //toAddress.setPersonal("First Last");
                        //System.out.println("to2: " + toAddress);
                    //}
                    if(toAddress.getAddress() != null && toAddress.getAddress().equals("Last, First")){
                        toAddress.setPersonal(toAddress.getAddress());
                        toAddress.setAddress("first.last@a.com");
                        System.out.println("to3: " + toAddress);
                    }
                    if(toAddress.getAddress() != null && toAddress.getAddress().equals("Last2, First2")){
                        toAddress.setPersonal(toAddress.getAddress());
                        toAddress.setAddress("first2.last2@a.com");
                        System.out.println("to3: " + toAddress);
                    }
                }

                mm.setRecipients(Message.RecipientType.TO, toList);

                IMAPMessage[] imList = {im};
                allmail.copyMessages(imList, trash);

                Message delMessages[] = trash.getMessages();
                System.out.println("\t# of messages: " + delMessages.length);
                for(Message delMessage:delMessages){
                    IMAPMessage dm = (IMAPMessage)delMessage;
                    System.out.println("\tmessage: " + dm.getSubject());
                    System.out.println("\tdeleted: " + dm.isSet(Flags.Flag.DELETED));
                    System.out.println("\t# del: " + trash.getDeletedMessageCount());
                    dm.setFlag(Flags.Flag.DELETED, true);
                    System.out.println("\t# del: " + trash.getDeletedMessageCount());
                    System.out.println("\tdeleted: " + dm.isSet(Flags.Flag.DELETED));
                }
                trash.expunge();
                MimeMessage[] mmList = {mm};
                allmail.appendMessages(mmList);
            }
*/

    // Look through sent mail and either inbox or all labels.
    //
    // User should copy everything from PST's Sent Items to 
    // [Gmail]/Sent Mail. Then create labels for each folder
    // and copy the emails. Or copy them to the Inbox only.
    // Do not mix them. When changing the header, the message
    // must be removed and a new copy added to the folder.
    // If the message is in the inbox and has a label, the
    // new copy would only wind up with one of the two.
    private static void parseFolders(Store store){
        try{
            Folder sentmail = store.getFolder(sentmailbox);
            //TODO: if (just get addresses) 1st pass
            getAddresses(sentmail);
            //TODO: if (actually modify headers) 2nd pass
            //TODO: load user's modified address list to use
            //TODO: parseMessages(sentmail);

            if(checkInbox){
                Folder inbox = store.getFolder(inboxName);
                getAddresses(inbox);
                //TODO: parseMessages(inbox);
            }else if(checkLabels){
                for(Folder label:labels){
                    getAddresses(label);
                    //TODO: parseMessages(label);
                }
            }
        }catch(MessagingException e){
            e.printStackTrace();
            System.exit(1);
        }
    }

    // Create a google spreadsheet to store the addresses that are found.
    // Record header address, and if possible personal name and email.
    // If the header is malformed, only record header and user will need
    // to fill in the personal and email manually.
    // Then when program is rerun, the spreadsheet will be the database
    // to lookup and fix all the malformed headers.
    public static void createSpreadsheet(){
        try{
            DocsService service = new DocsService("fix_empty_headers-v1");
            service.setUserCredentials(user, password);
            SpreadsheetEntry newEntry = new SpreadsheetEntry();
            newEntry.setTitle(new PlainTextConstruct("fix_empty_headers_addresses"));
            service.insert(new URL("https://docs.google.com/feeds/default/private/full/"), newEntry);
            // TODO: use spreadsheet api to add addresses to spreadsheet
        }catch(MalformedURLException e){
            e.printStackTrace();
            System.exit(1);
        }catch(IOException e){
            e.printStackTrace();
            System.exit(1);
        }catch(ServiceException e){
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static void main(String args[]){
        validateArgs(args);

        Properties props = System.getProperties();
        props.setProperty("mail.store.protocol", "imaps");
        try{
            Session session = Session.getDefaultInstance(props, null);
            Store store = session.getStore("imaps");
            store.connect("imap.gmail.com", user, password);

            getLabels(store);
            parseFolders(store);
            createSpreadsheet();
System.exit(0);

            // parses "All Mail"
            // this does not include messages in spam or trash
            Folder allmail = store.getFolder(mailbox);
            allmail.open(Folder.READ_WRITE);
            System.out.println(allmail.getFullName());
            System.out.println(allmail.getURLName());

            Folder trash = store.getFolder(trashbox);
            trash.open(Folder.READ_WRITE);
            System.out.println(trash.getFullName());
            System.out.println(trash.getURLName());

            Message messages[] = allmail.getMessages();
            System.out.println("# of messages: " + messages.length);
            for(Message message:messages){
                IMAPMessage im = (IMAPMessage)message;
                if(im.getSubject() != null){
                    if(!im.getSubject().equals("test message")){
                        continue;
                    }
                }else{
                    continue;
                }

                System.out.println();
                System.out.println("message: " + im.getSubject());

                // use getHeader() instead of simpler getSender() methods
                // because they make assumptions and will return values
                // from other fields, e.g. getReplyTo will return the
                // contents of getFrom if there is no reply-to header. Values
                // will wind up being added in places that they shouldn't be
                // after cleaning.
//                System.out.println("sender: " + im.getSender());
                if(im.getHeader("Sender") != null){
                    System.out.println("sender: " + im.getHeader("Sender").length);
                    for(String senderAddress:im.getHeader("Sender")){
                        System.out.println("sender: " + senderAddress);
                    }
                }
//                if(im.getFrom() != null){
//                    System.out.println("from: " + im.getFrom().length);
//                    for(Address fromAddress:im.getFrom()){
//                        System.out.println("from: " + fromAddress);
//                    }
//                }
                if(im.getHeader("From") != null){
                    System.out.println("from: " + im.getHeader("From").length);
                    for(String fromAddress:im.getHeader("From")){
                        System.out.println("from: " + fromAddress);
                    }
                }
//                if(im.getRecipients(Message.RecipientType.TO) != null){
//                    System.out.println("to: " + im.getRecipients(Message.RecipientType.TO).length);
//                    for(Address toAddress:im.getRecipients(Message.RecipientType.TO)){
//                        System.out.println("to: " + toAddress);
//                    }
//                }
                MimeMessage mm = new MimeMessage(im);
                if(mm.getHeader("To", ",") != null){
//                    System.out.println("to: " + im.getHeader("To", ","));
                    InternetAddress[] toList = InternetAddress.parseHeader(mm.getHeader("To", ","), true);
                    System.out.println("to: " + toList.length);
                    for(InternetAddress toAddress:toList){
                        System.out.println("to: " + toAddress);
                        System.out.println("to: " + toAddress.getPersonal());
                        System.out.println("to: " + toAddress.getAddress());
                        //if(toAddress.getPersonal() != null && toAddress.getPersonal().equals("<Last, First>")){
                            //toAddress.setPersonal("First Last");
                            //System.out.println("to2: " + toAddress);
                        //}
                        if(toAddress.getAddress() != null && toAddress.getAddress().equals("Last, First")){
                            toAddress.setPersonal(toAddress.getAddress());
                            toAddress.setAddress("first.last@a.com");
                            System.out.println("to3: " + toAddress);
                        }
                        if(toAddress.getAddress() != null && toAddress.getAddress().equals("Last2, First2")){
                            toAddress.setPersonal(toAddress.getAddress());
                            toAddress.setAddress("first2.last2@a.com");
                            System.out.println("to3: " + toAddress);
                        }
                    }

                    mm.setRecipients(Message.RecipientType.TO, toList);
//                    MimeMessage[] mmList = {mm};
//                    allmail.appendMessages(mmList);

                    IMAPMessage[] imList = {im};
                    allmail.copyMessages(imList, trash);

                    Message delMessages[] = trash.getMessages();
                    System.out.println("\t# of messages: " + delMessages.length);
                    for(Message delMessage:delMessages){
                        IMAPMessage dm = (IMAPMessage)delMessage;
                        System.out.println("\tmessage: " + dm.getSubject());
                        System.out.println("\tdeleted: " + dm.isSet(Flags.Flag.DELETED));
                        System.out.println("\t# del: " + trash.getDeletedMessageCount());
                        dm.setFlag(Flags.Flag.DELETED, true);
                        System.out.println("\t# del: " + trash.getDeletedMessageCount());
                        System.out.println("\tdeleted: " + dm.isSet(Flags.Flag.DELETED));
                    }
                    trash.expunge();
                    MimeMessage[] mmList = {mm};
                    allmail.appendMessages(mmList);
                }
//                if(im.getRecipients(Message.RecipientType.CC) != null){
//                    System.out.println("cc: " + im.getRecipients(Message.RecipientType.CC).length);
//                    for(Address ccAddress:im.getRecipients(Message.RecipientType.CC)){
//                        System.out.println("cc: " + ccAddress);
//                    }
//                }
                if(im.getHeader("Cc") != null){
                    System.out.println("cc: " + im.getHeader("Cc").length);
                    for(String ccAddress:im.getHeader("Cc")){
                        System.out.println("cc: " + ccAddress);
                    }
                }
//                if(im.getRecipients(Message.RecipientType.BCC) != null){
//                    System.out.println("bcc: " + im.getRecipients(Message.RecipientType.BCC).length);
//                    for(Address bccAddress:im.getRecipients(Message.RecipientType.BCC)){
//                        System.out.println("bcc: " + bccAddress);
//                    }
//                }
                if(im.getHeader("Bcc") != null){
                    System.out.println("bcc: " + im.getHeader("Bcc").length);
                    for(String bccAddress:im.getHeader("Bcc")){
                        System.out.println("bcc: " + bccAddress);
                    }
                }
//                if(im.getReplyTo() != null){
//                    System.out.println("reply-to: " + im.getReplyTo().length);
//                    for(Address replyToAddress:im.getReplyTo()){
//                        System.out.println("reply-to: " + replyToAddress);
//                    }
//                }
                if(im.getHeader("Reply-To") != null){
                    System.out.println("reply-to: " + im.getHeader("Reply-To").length);
                    for(String replyToAddress:im.getHeader("Reply-To")){
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
        }catch(UnsupportedEncodingException e){
            e.printStackTrace();
            System.exit(3);
        }
    }
}