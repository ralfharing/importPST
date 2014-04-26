importPST
=========

Upon importing an old PST's emails into a gmail account, I noticed a curious problem. Many to/from/cc/bcc/sender header fields were mangled. They were mangled in so many different ways (missing entirely, wrong local part, wrong domain part) that I thought I'd have to write a tool to corral all the mangled bits, manually correlate them, and run through them one by one.

I ultimately abandoned the terrible older java version (called fixemptyheaders) after finding the [Google Apps Migration for Microsoft Outlook tool](https://tools.google.com/dlpage/outlookmigration). It is the best PST parsing tool I've found and does all the hard work of stuffing things into a google apps account. It seems silly that it doesn't work with regular gmail accounts, but google apps just requires a domain and those only cost pocket change nowadays. It did not import emails 100% error free, but it eliminated many pain points and formatting problems. Anything sent through the internet was fine, but many of the to/from/cc/bcc/sender headers of emails that never left the internal network were malformed, but relatively nicely malformed. I expected them to be First.Last@company.com or at worst Username@company.com. Instead many were Username@mydomain.com (bizarre, I know).

I wound up writing a python script that was kind of like the java program except it worked much better by
* parsing through the bad headers
* creating a dictionary on the local machine of the bad usernames
* allowing the user to correlate a username to a First.Last pair
* replacing the headers in a downloaded store of the messages
* uploading them back piecemeal to avoid timeouts
* downloading everything in bulk from the google apps account
* uploading it in bulk to the gmail account that was the intended target

In the best of all possible worlds, Google would open source their migration utility so the following changes could be made

1. support import to gmail addresses and not just google apps addresses
2. fix the header parsing as it's getting many right but also many wrong
3. support ost files as well as pst files

*Warning:* The Google Spreadsheets API for table and record feeds [has been deprecated](http://googleappsdeveloper.blogspot.com/2011/03/deprecating-tables-and-records-feeds-in.html) since this code was written.
