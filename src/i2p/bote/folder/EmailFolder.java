/**
 * Copyright (C) 2009  HungryHobo@mail.i2p
 * 
 * The GPG fingerprint for HungryHobo@mail.i2p is:
 * 6DD3 EAA2 9990 29BC 4AD2 7486 1E2C 7B61 76DC DC12
 * 
 * This file is part of I2P-Bote.
 * I2P-Bote is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * I2P-Bote is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with I2P-Bote.  If not, see <http://www.gnu.org/licenses/>.
 */

package i2p.bote.folder;

import i2p.bote.UniqueId;
import i2p.bote.email.Email;
import i2p.bote.email.Field;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.mail.MessagingException;

import net.i2p.util.Log;

/**
 * Stores emails in a directory on the file system. Each email is stored in one file.
 * Filenames are in the format <code><N, O>_<message ID>.mail</code>, where
 * N is for new (unread or unsent) and O is for old (read or sent).
 */
public class EmailFolder extends Folder<Email> {
    protected static final String EMAIL_FILE_EXTENSION = ".mail";
    
    private Log log = new Log(EmailFolder.class);
    
    public EmailFolder(File storageDir) {
        super(storageDir, EMAIL_FILE_EXTENSION);
    }

    /**
     * Stores an email in the folder. If an email with the same
     * message ID exists already, nothing happens.
     * @param email
     * @throws IOException
     * @throws MessagingException
     */
    public void add(Email email) throws IOException, MessagingException {
        // check if an email exists already with that message id
        if (getEmailFile(email.getMessageID()) != null) {
            log.debug("Not storing email because there is an existing one with the same message ID: <" + email.getMessageID()+ ">");
            return;
        }
        
        // write out the email file
        File emailFile = getEmailFile(email);
        log.info("Mail folder <" + storageDir + ">: storing email file: <" + emailFile.getAbsolutePath() + ">");
        OutputStream emailOutputStream = null;
        try {
            emailOutputStream = new FileOutputStream(emailFile);
            email.writeTo(emailOutputStream);
        }
        finally {
            if (emailOutputStream != null)
                try {
                    emailOutputStream.close();
                }
                catch (IOException e) {
                    log.error("Can't close file: <" + emailFile + ">", e);
                }
        }
    }
    
    /**
     * Returns all emails in the folder, in the order specified by <code>sortColumn</code>.
     */
    public List<Email> getElements(Field sortColumn, boolean descending) {
        List<Email> emails = getElements();
        
        Comparator<Email> comparator = Email.getComparator(sortColumn);
        if (descending)
            comparator = Collections.reverseOrder(comparator);
        Collections.sort(emails, comparator);
        return emails;
    }
    
    /**
     * Finds an <code>Email</code> by message id. If the <code>Email</code> is
     * not found, <code>null</code> is returned.
     * The <code>messageId</code> parameter must be a 44-character base64-encoded
     * {@link UniqueId}.
     * @param messageId
     * @return
     */
    public Email getEmail(String messageId) {
        File file = getEmailFile(messageId);
        try {
            return createFolderElement(file);
        }
        catch (Exception e) {
            log.error("Can't read email from file: <" + file.getAbsolutePath() + ">", e);
            return null;
        }
    }
    
    private File getEmailFile(Email email) {
        return getEmailFile(email.getMessageID(), email.isNew());
    }

    /**
     * Returns a file in the file system for a given message ID, or <code>null</code> if
     * none exists.
     * @param messageId
     * @return
     */
    private File getEmailFile(String messageId) {
        // try new email
        File newEmailFile = getEmailFile(messageId, true);
        if (newEmailFile.exists())
            return newEmailFile;
        
        // try old email
        File oldEmailFile = getEmailFile(messageId, false);
        if (oldEmailFile.exists())
            return oldEmailFile;
        
        return null;
    }
    
    private File getEmailFile(String messageId, boolean newIndicator) {
        return new File(storageDir, (newIndicator?'N':'O') + "_" + messageId + EMAIL_FILE_EXTENSION);
    }
    
    /**
     * @see Folder.getNumElements()
     * @return
     */
    public int getNumNewEmails() {
        int numNew = 0;
        for (File file: getFilenames())
            if (isNew(file))
                numNew++;
        
        return numNew;
    }
    
    private boolean isNew(File file) {
        switch (file.getName().charAt(0)) {
        case 'N':
            return true;
        case 'O':
            return false;
        default:
            throw new IllegalArgumentException("Illegal email filename, doesn't start with N or O: <" + file.getAbsolutePath() + ">");
        }
    }
    
    /**
     * Flags an email "new" (if <code>isNew</code> is <code>true</code>) or
     * "old" (if <code>isNew</code> is <code>false</code>).
     * @param messageId
     * @param isNew
     */
    public void setNew(String messageId, boolean isNew) {
        File file = getEmailFile(messageId);
        if (file != null) {
            char newIndicator = isNew?'N':'O';   // the new start character
            String newFilename = newIndicator + file.getName().substring(1);
            File newFile = new File(file.getParentFile(), newFilename);
            boolean success = file.renameTo(newFile);
            if (!success)
                log.error("Cannot rename <" + file.getAbsolutePath() + "> to <" + newFile.getAbsolutePath() + ">");
        }
        else
            log.error("No email found for message Id: <" + messageId + ">");
    }
    
    /**
     * Deletes an email with a given message ID.
     * @param messageId
     * @return <code>true</code> if the email was deleted, <code>false</code> otherwise
     */
    public boolean delete(String messageId) {
        File emailFile = getEmailFile(messageId);
        if (emailFile != null)
            return emailFile.delete();
        else
            return false;
    }
    
    public void delete(Email email) {
        if (!getEmailFile(email).delete())
            log.error("Cannot delete file: '" + getEmailFile(email) + "'");
    }

    @Override
    protected Email createFolderElement(File file) throws Exception {
        Email email = new Email(file);
        email.setNew(isNew(file));
        
        String messageIdString = file.getName().substring(2, 46);
        email.setMessageID(messageIdString);
        
        return email;
    }
}