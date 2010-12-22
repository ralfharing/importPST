
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
import com.google.gdata.data.spreadsheet.*;
import com.google.gdata.client.spreadsheet.*;

public class fix_empty_headers{
    // command line flags
    private static String user = "";
    private static String password = "";
    private static boolean checkInbox = false;
    private static boolean checkLabels = false;
    private static boolean updateHeaders = false;
    private static boolean guessGoodValues = false;
    private static String domain = "";

    // folder names
    private static String mailbox = "[Gmail]/All Mail";
    private static String inboxName = "INBOX";
    private static String sentmailbox = "[Gmail]/Sent Mail";
    private static String trashbox = "[Gmail]/Trash";

    // list of labels
    private static HashSet<Folder> labels = new HashSet<Folder>();

    // list of all addresses
    private static HashSet<InternetAddress> addresses = new HashSet<InternetAddress>();

    // list of messages with corrected headers
    private static HashSet<Message> fixedMessages = new HashSet<Message>();

    // google docs constants
    private static String serviceName = "fix_empty_headers-v1";
    private static String sheetName = "fix_empty_headers_addresses";
    private static String tableName = "list of addresses";
    private static String headerColumn = "existing header";
    private static String emailColumn = "email address";
    private static String personalColumn = "personal name";

    // only create once so multiple logins don't flag requests as needing
    // captcha authentication
    private static SpreadsheetService sheetService = null;
    private static URL recordFeedUrl = null;

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
            }else if(args[flag].equalsIgnoreCase("-2")){
                updateHeaders = true;
            }else if(args[flag].equalsIgnoreCase("-g") && flag + 1 < args.length){
                guessGoodValues = true;
                domain = args[++flag];
            }
        }

        if(user.isEmpty() || password.isEmpty() || (checkInbox == checkLabels)){
            System.out.println("Usage : fix_empty_headers [-u user] [-p password] [-i/-l] [-g abc.com] [-2]");
            System.out.println("-u : required : username ");
            System.out.println("-p : required : password");
            System.out.println("-i/l : required : check emails in inbox or in user's labels");
            System.out.println("-g : optional : guess names/emails for bad addresses when creating spreadsheet");
            System.out.println("-2 : optional : use on second pass to actually update headers");
            System.exit(1);
        }
    }

    // look through all folders under root, ignore system folders,
    // and store user's labels
    private static void getLabels(Store store){
        try{
            Folder[] dfl = store.getDefaultFolder().list();
            for(Folder folder : dfl){
                String name = folder.getName();
                if(!(name.equalsIgnoreCase(inboxName) ||
                     name.equalsIgnoreCase("Junk E-mail") ||
                     name.equalsIgnoreCase("[Gmail]"))){
                    //System.out.println(folder);
                    labels.add(folder);
                }
            }
            System.out.println("LABELS: " + labels);
        }catch(MessagingException e){
            e.printStackTrace();
            System.exit(1);
        }
    }

    // Set up connection to Google Spreadsheet API once each pass. If you do
    // it too many times, it sees that as suspicious behavior and a Captcha
    // Request Exception is thrown.
    private static void createSpreadsheetService(TableEntry table){
        try{
            if(sheetService == null){
                // use spreadsheet api to get spreadsheet
                sheetService = new SpreadsheetService(serviceName);
                sheetService.setUserCredentials(user, password);
                FeedURLFactory factory = FeedURLFactory.getDefault();
                URL sheetfeedUrl = factory.getSpreadsheetsFeedUrl();
                SpreadsheetFeed sheetFeed = sheetService.getFeed(sheetfeedUrl, SpreadsheetFeed.class);
                com.google.gdata.data.spreadsheet.SpreadsheetEntry sheetEntry = null;
                for(com.google.gdata.data.spreadsheet.SpreadsheetEntry se : sheetFeed.getEntries()){
                    if(se.getTitle().getPlainText().equalsIgnoreCase(sheetName))
                        sheetEntry = se;
                        break;
                }

                URL tableFeedUrl = factory.getTableFeedUrl(sheetEntry.getKey());
                if(table != null){
                    // Only create the table in the first pass. In the second
                    // it is already there and we are reading it.
                    sheetService.insert(tableFeedUrl, table);
                }

                // use spreadsheet api to get table
                TableFeed tableFeed = sheetService.getFeed(tableFeedUrl, TableFeed.class);
                TableEntry tableEntry = null;
                for(TableEntry te : tableFeed.getEntries()){
                    if(te.getTitle().getPlainText().equalsIgnoreCase(tableName))
                        tableEntry = te;
                        break;
                }

                // Why does getId() return a full url? Issue opened at
                // http://code.google.com/a/google.com/p/apps-api-issues/issues/detail?id=2346
                // and discussed at
                // https://groups.google.com/forum/#topic/google-spreadsheets-api/m4X6E-V12-U
                //recordFeedUrl = factory.getRecordFeedUrl(sheetEntry.getKey(), tableEntry.getId());

                // Alternative solution, but also doesn't work.
                // Why does this return null? Issue opened at
                // http://code.google.com/a/google.com/p/apps-api-issues/issues/detail?id=2348
                // and discussed at
                // https://groups.google.com/forum/#topic/google-spreadsheets-api/m4X6E-V12-U
                //recordFeedUrl = new URL(tableEntry.getRecordsFeedLink().getHref());

                // Manually hack a solution.
                recordFeedUrl = new URL(tableEntry.getId().toString().replace("tables", "records"));
            }
        }catch(IOException e){
            e.printStackTrace();
            System.exit(1);
        }catch(ServiceException e){
            e.printStackTrace();
            System.exit(1);
        }
    }

    // Look up the address in the google spreadsheet created in the first
    // pass and set the 
    private static RecordEntry lookupAddress(InternetAddress address){
        try{
            // see note in createSpreadsheet() for why quotes and carriage
            // returns are replaced
            String lookupColumn = headerColumn;
            String lookupValue = address.toString().replace("\"", "").replaceAll("\\r\\n", "");

            try{
                // If the address we're looking up is valid, then a simpler
                // lookup is on the email address itself. Saw issues with
                // two valid addresses where only one was added to the
                // spreadsheet because the hashset during the first pass had
                // filtered out one. The .equals() method checked the email
                // address and not the full header.
                address.validate();
                lookupColumn = emailColumn;
                lookupValue = address.getAddress().replace("\"", "").replaceAll("\\r\\n", "");
            }catch(AddressException e){}

            createSpreadsheetService(null);
            RecordQuery query = new RecordQuery(recordFeedUrl);

            // put query and columns in double quotes since they can contain spaces
            query.setSpreadsheetQuery("\"" + lookupColumn + "\" = \"" + lookupValue + "\"");
            //System.out.println(query.getSpreadsheetQuery());
            //System.out.println(query.getUrl());
            RecordFeed recordFeed = sheetService.query(query, RecordFeed.class);

            // Why does getTotalResults() ignore the query and always return
            // the total number of rows? Issue opened at
            // http://code.google.com/a/google.com/p/apps-api-issues/issues/detail?id=2351
            // and discussed at
            // https://groups.google.com/forum/#topic/google-spreadsheets-api/2fo9xuMt5KA
            //if(recordFeed.getTotalResults() != 1){

            List<RecordEntry> entries = recordFeed.getEntries();
            if(entries.size() < 1){
                // There should always be at least one entry.
                System.out.println("ERROR: address not found: " + address);
                System.exit(1);
            }

            for(RecordEntry re : entries){
                // Usually just one entry, but if multiple any one will do.
                return re;
            }
        }catch(IOException e){
            e.printStackTrace();
            System.exit(1);
        }catch(ServiceException e){
            e.printStackTrace();
            System.exit(1);
        }
        // should never get here
        return null;
    }

    // Parse the addresses in a specific header line
    //
    // Use getHeader() API instead of simpler getSender() methods because
    // they make assumptions and will return values from other fields,
    // e.g. getReplyTo will return the contents of getFrom if there is no
    // reply-to header. Values will wind up being added in places that they
    // shouldn't be after cleaning.
    private static MimeMessage getHeaders(MimeMessage message, String headerType, MimeMessage fixedMessage){
        try{
            String header = message.getHeader(headerType, ",");
            if(header != null){
                InternetAddress[] addressList = InternetAddress.parseHeader(header, true);
                //System.out.println("# " + headerType + ": " + addressList.length);
                String newHeader = "";

                for(InternetAddress address : addressList){
                    // for each address in this header line either add it to
                    // set to be uploaded to the spreadsheet later (first pass)
                    // or look up the value in the spreadsheet and replace the
                    // current header (second pass)
                    if(updateHeaders){
                        RecordEntry lookup = lookupAddress(address);
                        for(Field field : lookup.getFields()){
                            if(field.getName().equalsIgnoreCase(emailColumn)){
                                address.setAddress(field.getValue());
                            }else if(field.getName().equalsIgnoreCase(personalColumn)){
                                address.setPersonal(field.getValue());
                            }
                        }

                        // append multiple addresses separate by comma
                        if(newHeader.equals("")){
                            newHeader = address.toString();
                        }else{
                            newHeader += ", " + address.toString();
                        }
                    }else{
                        // duplicates filtered out by HashSet automatically
                        // (but spreadsheet may wind up with duplicates due
                        // to later guesswork in filling out addresses)
                        addresses.add(address);

                        //System.out.println(headerType + ": " + address);
                        //System.out.println("\theader: " + header);
                        //System.out.println("\tpersonal: " + address.getPersonal());
                        //System.out.println("\temail: " + address.getAddress());
                    }
                }

                if(updateHeaders){
                    //System.out.println("old header: " + header);
                    //System.out.println("new header: " + newHeader);
                    fixedMessage.setHeader(headerType, newHeader);
                }
            }
        }catch(MessagingException e){
            e.printStackTrace();
            System.exit(1);
        }catch(UnsupportedEncodingException e){
            e.printStackTrace();
            System.exit(1);
        }
        return fixedMessage;
    }

    // Get all the addresses for the various different types of headers that
    // can contain addresses. I think there are still others like
    // Resent-Sender, Resent-From, Resent-Reply-To, Resent-To, Resent-Cc, and
    // Resent-Bcc which are not used frequently.
    private static void getMessagesFromLabel(Folder folder, Folder trash){
        try{
            if(updateHeaders){
                folder.open(Folder.READ_WRITE);
                trash.open(Folder.READ_WRITE);
            }else{
                folder.open(Folder.READ_ONLY);
            }

            Message messages[] = folder.getMessages();
            System.out.println("Reading " + messages.length + " messages in " + folder.getFullName());

            for(Message message : messages){
                // cast to MimeMessage to be able to use getHeader() later
                MimeMessage mm = (MimeMessage)message;

                // Don't waste memory if this is the first run through
                // where addresses are only being gathered and messages
                // aren't being fixed.
                MimeMessage fixedMessage = null;
                if(updateHeaders){
                    fixedMessage = new MimeMessage(mm);
                }

                // fixedMessage is returned with the header fixed
                fixedMessage = getHeaders(mm, "Sender", fixedMessage);
                fixedMessage = getHeaders(mm, "From", fixedMessage);
                fixedMessage = getHeaders(mm, "To", fixedMessage);
                fixedMessage = getHeaders(mm, "Cc", fixedMessage);
                fixedMessage = getHeaders(mm, "Bcc", fixedMessage);
                fixedMessage = getHeaders(mm, "Reply-To", fixedMessage);

                // add message to a set for later bulk delete/update
                if(updateHeaders){
                    fixedMessages.add(fixedMessage);
                }

                //Enumeration allHeaderLines = mm.getAllHeaderLines();
                //while(allHeaderLines.hasMoreElements()){
                //    System.out.println("allheaderlines: " + allHeaderLines.nextElement());
                //}
            }

            System.out.println("Finished reading messages from " + folder.getFullName());

            if(updateHeaders){
                System.out.println("Moving " + messages.length + " old messages to trash");

                // move all current messages to trash
                folder.copyMessages(messages, trash);

                // empty trash
                Message delMessages[] = trash.getMessages();
                for(Message delMessage : delMessages){
                    IMAPMessage dm = (IMAPMessage)delMessage;
                    dm.setFlag(Flags.Flag.DELETED, true);
                }
                System.out.println("Messages marked as deleted and emptying trash");
                trash.close(true);

                System.out.println("Uploading " + fixedMessages.size() + " fixed messages");
                // add new messages to folder
                folder.appendMessages(fixedMessages.toArray(new Message[0]));

                // empty message list to prepare for next folder
                fixedMessages.clear();
            }

            folder.close(false);
        }catch(MessagingException e){
            e.printStackTrace();
            System.exit(1);
        }
    }

    // Look through sent mail and either inbox or all labels.
    //
    // User should copy everything from PST's Sent Items to [Gmail]/Sent Mail.
    // Then create labels for each folder and copy the emails. Or copy them to
    // the Inbox only. Do not mix them. When changing the header later, the
    // message will be removed and a new copy added to the folder. If the
    // message is in the inbox and has a label (or has any two labels), the
    // old copy will be removed from both but only added to the current folder.
    //
    // The first time the program is run, the headers are parsed and an address
    // list created for later upload to a google spreadsheet. The second time,
    // the headers are parsed and fixed based on the contents of the google
    // spreadsheet.
    private static void parseBadHeaders(Store store){
        try{
            Folder trash = store.getFolder(trashbox);

            Folder sentmail = store.getFolder(sentmailbox);
            getMessagesFromLabel(sentmail, trash);

            if(checkInbox){
                Folder inbox = store.getFolder(inboxName);
                getMessagesFromLabel(inbox, trash);
            }else if(checkLabels){
                for(Folder label : labels){
                    getMessagesFromLabel(label, trash);
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
    // to fill in the personal and email manually. (unless they chose
    // to create guesses)
    //
    // Then when program is rerun, the spreadsheet will be the database
    // to lookup and fix all the malformed headers.
    public static void createSpreadsheet(){
        try{
            // use doclist api to create a new spreadsheet
            DocsService docService = new DocsService(serviceName);
            docService.setUserCredentials(user, password);
            SpreadsheetEntry newSheet = new SpreadsheetEntry();
            newSheet.setTitle(new PlainTextConstruct(sheetName));
            docService.insert(new URL("https://docs.google.com/feeds/default/private/full/"), newSheet);

            // create a new table to put in the spreadsheet
            TableEntry table = new TableEntry();
            table.setTitle(new PlainTextConstruct(tableName));
            table.setWorksheet(new Worksheet("Sheet 1"));
            table.setHeader(new com.google.gdata.data.spreadsheet.Header(1));

            // create the columns in the table
            Data tableData = new Data();
            tableData.setStartIndex(2);
            tableData.addColumn(new Column("A", headerColumn));
            tableData.addColumn(new Column("B", emailColumn));
            tableData.addColumn(new Column("C", personalColumn));
            table.setData(tableData);

            // call common service creation code
            createSpreadsheetService(table);

            // add new records to the table and save them in the spreadsheet
            for(InternetAddress address : addresses){
                RecordEntry record = new RecordEntry();

                // Double quotes are used as delimiters in the spreadsheet api
                // query language and lead to invalid results if in the column
                // name or query string. Carriage returns also cause problems
                // with invalid feed results. So strip them both out.
                // Issue opened at
                // http://code.google.com/a/google.com/p/apps-api-issues/issues/detail?id=2357
                // and discussed at
                // https://groups.google.com/forum/#topic/google-spreadsheets-api/IWtyOb7Z6vI
                String recAddress = address.toString().replace("\"", "").replaceAll("\\r\\n", "");
                String recEmail = address.getAddress().replace("\"", "").replaceAll("\\r\\n", "");
                String recPersonal = address.getPersonal();
                if(recPersonal != null){
                    // getAddress() will always return something, getPersonal() may not
                    recPersonal = recPersonal.replace("\"", "").replaceAll("\\r\\n", "");
                }
                //System.out.println();
                //System.out.println(recAddress);
                //System.out.println(recEmail);
                //System.out.println(recPersonal);
 
                record.addField(new Field(null, headerColumn, recAddress));
                try{
                    // If the address is valid, parse the components.
                    address.validate();
                    if(recEmail != null && !recEmail.trim().isEmpty()){
                        record.addField(new Field(null, emailColumn, recEmail));
                    }
                    if(recPersonal != null && !recPersonal.trim().isEmpty()){
                        record.addField(new Field(null, personalColumn, recPersonal));
                    }
                }catch(AddressException e){
                    // If address is invalid and the unknown senders were
                    // generated by importing from a PST, many times it will
                    // be of the format <Lastname, Firstname I> which can be
                    // used to generate a guess if the user wishes.
                    if(guessGoodValues){
                        if(recAddress.startsWith("<") && recAddress.endsWith(">")){
                            // So the personal name can be found by just stripping
                            // the carets.
                            recPersonal = recAddress.substring(1, recAddress.length() - 1);
                            record.addField(new Field(null, personalColumn, recPersonal));

                            // If there is a comma, it's likely a name in the format
                            // above. Split it on the comma and transpose it. Put a
                            // dot between them, replace spaces with dots, and add
                            // the domain.
                            // This will possibly give bad email addresses like
                            // first.i.last@abc.com when it should be fist.last@abc.com
                            // but it can be a reasonable first guess for manual
                            // editing later.
                            if(recAddress.contains(",")){
                                String[] splitName = recAddress.split(",", 2);
                                splitName[0] = splitName[0].trim().toLowerCase();
                                splitName[1] = splitName[1].trim().toLowerCase();
                                recEmail = splitName[1].substring(0, splitName[1].length() - 1);
                                recEmail += "." + splitName[0].substring(1, splitName[0].length());
                                recEmail += "@" + domain;
                                recEmail = recEmail.replace(" ", ".");
                                record.addField(new Field(null, emailColumn, recEmail));
                            }
                            // If there is no comma it's hard to tell if the format
                            // is <First Last> or if it's a mailing list, so don't guess
                            // an address for those.
                        }
                    }
                }

                sheetService.insert(recordFeedUrl, record);
            }
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
            //System.out.println(store);

            getLabels(store);
            parseBadHeaders(store);
            if(!updateHeaders){
                createSpreadsheet();
            }
        }catch(MessagingException e){
            e.printStackTrace();
            System.exit(1);
        }
    }
}