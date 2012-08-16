# I basically stopped working on cleaning this up as soon as I had it working.
# It is very hacked together. Note instances of "MYDOMAIN" and "COMPANY"
# which are hardcoded in various places. The list of labels is also hardcoded.
# It also assumes usernames were in a specific format, xxxyyzz where xxx are
# the first three chars of the last name, yy are the first two chars of the
# first name, and zz are digits.
#
# Basically, it presupposes that you've run the Outlook Migration Tool.

import email
import re
import imaplib
import pickle
import os

pattern = {'final': '^[0-3]?\d (?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) 20\d\d \d\d:\d\d:\d\d [\-\+]\d\d\d\d$',
           'shortYear': '^[0-3]?\d (?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) \d\d ',
           'noSeconds': ' \d\d:\d\d [\-\+]\d\d\d\d$',
           'noTimezone': ' \d\d:\d\d:\d\d$'}

endingPatterns = [(' GMT', ' GMT', ' +0000'), (' EDT', ' EDT', ' -0400'), (' +0200 (CEST)', ' (CEST)', ''), (' -0400 (EDT)', ' (EDT)', ''), (' -0700 (PDT)', ' (PDT)', ''), (' -0800 (PST)', ' (PST)', ''), (' -0400 (GMT-04:00)', ' (GMT-04:00)', ''), (' CST', ' CST', ' -0600'), (' -0500 (EST)', ' (EST)', ''), (' -0400 (Eastern Daylight Time)', ' (Eastern Daylight Time)', ''), (' EST', ' EST', ' -0500'), (' -0500 (CDT)', ' (CDT)', ''), (' -0400 (CLT)', ' (CLT)', ''), (' -0400 (EST)', ' (EST)', ''), (' +0000 (GMT)', ' (GMT)', ''), (' -0500 (Eastern Standard Time)', ' (Eastern Standard Time)', ''), (' -0400 (CET)', ' (CET)', ''), (' -0600 (MDT)', ' (MDT)', ''), (' UT', ' UT', ''), (' +0100 (BST)', ' (BST)', ''), (' +0100 (CET)', ' (CET)', '')]

headers = ['from', 'to', 'cc', 'bcc']
#headers = ['from', 'to', 'cc', 'bcc', 'sender']
labels = ['label1', 'label2', 'label3', 'INBOX', '[Gmail]/Sent Mail']

account = 'user@gmail.com'
password = 'password'

# upload an email message to the given label
def addMessage(email, date, label):
    matches = re.findall(pattern['final'], date, re.I)
    if len(matches) == 1 and matches[0] == date:
        # date checks out as ok
        date = '"' + date.replace(' ', '-', 2) + '"'
        mail.append(label, '', date, str(email))
        return True
    return False

# modify wonky date formats to one that will be uploadable
def fixDate(date):
    # fix time zone abbreviations
    for pair in endingPatterns:
        if date.endswith(pair[0]):
            date = date.replace(pair[1], pair[2])
            break

    # replace two digit years with four digit
    if re.findall(pattern['shortYear'], date, re.I):
        if date[1] == ' ':
            date = date[:6] + '20' + date[6:]
        else:
            date = date[:7] + '20' + date[7:]

    # add seconds when not present
    if re.findall(pattern['noSeconds'], date, re.I):
        date = date[:-6] + ':00' + date[-6:]

    # add time zone when not present
    if re.findall(pattern['noTimezone'], date, re.I):
        date += ' +0000'

    return date

# parse existing messages and separate bad email headers from good ones
def gatherAddresses():
    badEmails = set()
    goodEmails = set()
    ret, numMsgs = mail.select('[Gmail]/All Mail')
    # loop through all messages
    for msgNum in range(1, int(numMsgs[0]) + 1):
        ret, data = mail.fetch(msgNum, '(RFC822)')
        msg = email.message_from_string(data[0][1])
        # loop through to/from/cc/bcc headers
        for header in headers:
            if msg.has_key(header):
                msgHeader = msg[header]
                # add all headers
                goodEmails.add(msgHeader)
                if 'MYDOMAIN' in msgHeader:
                    badEmails.add(msgHeader)
    # remove mangled emails from good list
    goodEmails = goodEmails - badEmails
    outFile = open('badEmails', 'w')
    pickle.dump(badEmails, outFile)
    outFile.close()
    outFile = open('goodEmails', 'w')
    pickle.dump(goodEmails, outFile)
    outFile.close()
#    print badEmails
#    print goodEmails

# download local copies of messages with bad headers, fix them using the
# local dict and set, and delete the bad copies in the account
def cacheFixedHeaders():
    # directory to store the pickled fixed emails in
    directory = account.split('@')[0]
    os.mkdir(directory)
    # presupposes a pickled dictionary in the format "Username : First.Last"
    f = open('userdict')
    userdict = pickle.load(f)
    f.close()
    # presupposes a pickled set of usernames that had no first.lastname
    f = open('userset')
    userset = pickle.load(f)
    f.close()

    # loop through all labels
    for label in labels:
        print label
        mail.select(label)
        msgNums = set()
        badHeaders = set()

        # loop through all headers
        for header in headers:
            # find list of messages with bad headers
            ret, data = mail.search(None, header, 'MYDOMAIN')
            if data[0] != '':
                for msgNum in data[0].split():
                    # keep track of simple list of bad messages
                    msgNums.add(msgNum)
                    # and longer list of specifically which headers are bad
                    badHeaders.add((msgNum, header))
        # if no bad messages, skip
        if len(msgNums) == 0:
            continue

        # fetch local copies of all messages and record with imap
        # message numbers
        msgDict = {}
        for num in msgNums:
            ret1, data1 = mail.fetch(num, '(RFC822)')
            msgDict[num] = email.message_from_string(data1[0][1])

        # loop through all bad headers
        for badHeaderPair in badHeaders:
            msg = msgDict[badHeaderPair[0]]
            # header may not be present despite search() above,
            # e.g. 'to' search returns things that were 'cc'
            if not msg.has_key(badHeaderPair[1]):
                continue
            badString = msg[badHeaderPair[1]]
            fixedString = badString
            for part in badString.split('@MYDOMAIN'):
                # make sure the part of the header is long enough
                if len(part) >= 7:
                    username = part[-7:].lower()
                    # check if username had no first.lastname correlation
                    if username in userset:
                        fixedString = fixedString.replace(part[-7:] + '@MYDOMAIN', part[-7:] + '@COMPANY')
                    # check if username is in the support dictionary
                    elif userdict.has_key(username):
                        name = userdict[username]
                        fixedString = fixedString.replace(part[-7:] + '@MYDOMAIN', name + '@COMPANY')
                    # check if username is 6 instead of 7 chars (rare)
                    elif userdict.has_key(username[1:]):
                        name = userdict[username[1:]]
                        fixedString = fixedString.replace(part[-6:] + '@MYDOMAIN', name + '@COMPANY')
                    # check if username is 5 instead of 7 chars (rare)
                    elif userdict.has_key(username[2:]):
                        name = userdict[username[2:]]
                        fixedString = fixedString.replace(part[-5:] + '@MYDOMAIN', name + '@COMPANY')
            # fix the one header and store in msg dictionary
            msg.replace_header(badHeaderPair[1], fixedString)
            msgDict[badHeaderPair[0]] = msg

        # set Sent Mail label to a nice filename
        if label == '[Gmail]/Sent Mail':
            label = 'Sent Mail'
        # store the fixed messages as a backup
        # importing so many messages can timeout
        outFile = open(directory + '/' + label, 'w')
        pickle.dump(msgDict, outFile)
        outFile.close()

        # move all bad messages to the Trash and delete
        mail.copy(','.join(msgNums), '[Gmail]/Trash')
        mail.select('[Gmail]/Trash')
        mail.store('1:*', '+FLAGS', '\\Deleted')
        mail.expunge()

# take the local fixed messages and upload them to the appropriate
# label, fixing the dates along the way
def uploadFixedHeaders():
    for label in labels:
        print label
        mail.select(label)
        # set Sent Mail label to a nice filename
        if label == '[Gmail]/Sent Mail':
            label = 'Sent Mail'
        path = account.split('@')[0] + '/' + label
        # if file doesn't exist, move to next label
        if not os.path.isfile(path):
            print '\t' + path + ' is not a file'
            continue
        # load stored fixed messages
        f = open(path)
        msgDict = pickle.load(f)
        f.close()
        if label == 'Sent Mail':
            label = '[Gmail]/Sent Mail'

        # loop through fixed messages and upload them
        for key in msgDict.keys():
            newMsg = msgDict[key]
            date = newMsg['date'].strip()
            if date.startswith(('Mon,', 'Tue,', 'Wed,', 'Thu,', 'Fri,', 'Sat,', 'Sun,')):
                date = date[4:].strip()
            # try adding it first, only modify date if it fails
            if not addMessage(newMsg, date, label):
                date = fixDate(date)
                if not addMessage(msg, date, label):
                    # a new date format not seen before
                    print msg
            # keep size down somewhat on labels that are 100s of MB
            del msgDict[key]

# assuming there exists an initial user.csv file where the user
# has created a list of their most common contacts' usernames
# and the corresponding human-readable email local part ...
# split it into a dict of known ones and a set of unknowns
def convertCSVtoDict():
    os.remove('userdict')
    os.remove('userset')
    # csv format is "username,First.Last\n"
    csv = open('user.csv')
    userdict = {}
    userset = set()
    for line in csv:
        if len(line) == 9:
            # empty/unknown first.last combination
            userset.add(line[:7])
        else:
            username, name = line.split(',')
            # remove the '\n'
            userdict[username] = name[:-1]
    csv.close()
    outFile = open('userdict', 'w')
    pickle.dump(userdict, outFile)
    outFile.close()
    outFile = open('userset', 'w')
    pickle.dump(userset, outFile)
    outFile.close()

# loops through the csv and counts the total usernames,
# good usernames, and how complete it is
def countCSV():
    o = open('user.csv')
    i = 0
    j = 0
    for line in o:
        j += 1
        if len(line) > 9:
            i += 1
    print i, '/', j, '(', 100*i/j, '%)'

# loops through all the labels in the account and downloads all messages.
# used at the end to do a bulk export from the google apps account to the
# gmail one that was the intended storage all along.
def downloadMsgs():
    # directory to store the pickled fixed emails in
    directory = account.split('@')[0]
    os.mkdir(directory)

    # loop through all labels
    for label in labels:
        print label
        ret, data = mail.select(label)

        # if no messages, skip
        if data[0] == '0':
            continue

        # fetch local copies of all messages and record with imap
        # message numbers
        msgDict = {}
        for num in range(1, int(data[0]) + 1):
            ret1, data1 = mail.fetch(num, '(RFC822)')
            msgDict[num] = email.message_from_string(data1[0][1])

        # set Sent Mail label to a nice filename
        if label == '[Gmail]/Sent Mail':
            label = 'Sent Mail'
        # store the fixed messages as a backup
        # importing so many messages can timeout
        outFile = open(directory + '/' + label, 'w')
        pickle.dump(msgDict, outFile)
        outFile.close()

mail = imaplib.IMAP4_SSL('imap.gmail.com')
mail.login(account, password)

#countCSV()
#convertCSVtoDict()
#gatherAddresses()
#cacheFixedHeaders()
#uploadFixedHeaders()
#downloadMsgs()
