package i2p.bote.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import com.tokenautocomplete.FilteredArrayAdapter;

import net.i2p.data.DataFormatException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;

import i2p.bote.I2PBote;
import i2p.bote.android.util.BoteHelper;
import i2p.bote.android.util.ContactsCompletionView;
import i2p.bote.android.util.Person;
import i2p.bote.email.Attachment;
import i2p.bote.email.Email;
import i2p.bote.email.EmailIdentity;
import i2p.bote.fileencryption.PasswordException;
import i2p.bote.packet.dht.Contact;

public class NewEmailFragment extends Fragment {
    private Callbacks mCallbacks = sDummyCallbacks;

    public interface Callbacks {
        public void onTaskFinished();

        public void onBackPressAllowed();
    }

    private static Callbacks sDummyCallbacks = new Callbacks() {
        public void onTaskFinished() {
        }

        public void onBackPressAllowed() {
        }
    };

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (!(activity instanceof Callbacks))
            throw new IllegalStateException("Activity must implement fragment's callbacks.");
        mCallbacks = (Callbacks) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallbacks = sDummyCallbacks;
    }

    public static final String QUOTE_MSG_FOLDER = "sender";
    public static final String QUOTE_MSG_ID = "recipient";

    public static enum QuoteMsgType {
        REPLY,
        REPLY_ALL,
        FORWARD
    }

    public static final String QUOTE_MSG_TYPE = "type";

    private String mSenderKey;

    Spinner mSpinner;
    int mDefaultPos;
    ArrayAdapter<Person> mAdapter;
    ContactsCompletionView mRecipients;
    EditText mSubject;
    EditText mContent;
    boolean mDirty;

    public static NewEmailFragment newInstance(String quoteMsgFolder, String quoteMsgId,
                                               QuoteMsgType quoteMsgType) {
        NewEmailFragment f = new NewEmailFragment();
        Bundle args = new Bundle();
        args.putString(QUOTE_MSG_FOLDER, quoteMsgFolder);
        args.putString(QUOTE_MSG_ID, quoteMsgId);
        args.putSerializable(QUOTE_MSG_TYPE, quoteMsgType);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_new_email, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mSpinner = (Spinner) view.findViewById(R.id.sender_spinner);
        mRecipients = (ContactsCompletionView) view.findViewById(R.id.recipients);
        mSubject = (EditText) view.findViewById(R.id.subject);
        mContent = (EditText) view.findViewById(R.id.message);

        String quoteMsgFolder = getArguments().getString(QUOTE_MSG_FOLDER);
        String quoteMsgId = getArguments().getString(QUOTE_MSG_ID);
        QuoteMsgType quoteMsgType = (QuoteMsgType) getArguments().getSerializable(QUOTE_MSG_TYPE);
        boolean hide = I2PBote.getInstance().getConfiguration().getHideLocale();

        List<Person> recipients = new ArrayList<Person>();
        String origSubject = null;
        String origContent = null;
        String origFrom = null;
        try {
            Email origEmail = BoteHelper.getEmail(quoteMsgFolder, quoteMsgId);

            if (origEmail != null) {
                mSenderKey = BoteHelper.extractEmailDestination(
                        BoteHelper.getOneLocalRecipient(origEmail).toString());

                if (quoteMsgType == QuoteMsgType.REPLY) {
                    String recipient = BoteHelper.getNameAndDestination(
                            origEmail.getReplyAddress(I2PBote.getInstance().getIdentities()));
                    recipients.add(extractPerson(recipient));
                } else if (quoteMsgType == QuoteMsgType.REPLY_ALL) {
                    // TODO don't include our address
                    // What happens if an email is received by multiple local identities?
                    for (Address address : origEmail.getAllAddresses(true)) {
                        Person person = extractPerson(address.toString());
                        if (person != null)
                            recipients.add(person);
                    }
                }

                origSubject = origEmail.getSubject();
                origContent = origEmail.getText();
                origFrom = BoteHelper.getShortSenderName(origEmail.getOneFromAddress(), 50);
            }
        } catch (PasswordException e) {
            // Should not happen, we cannot get to this page without authenticating
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (GeneralSecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (MessagingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // Set up identities spinner
        IdentityAdapter identities = new IdentityAdapter(getActivity());
        mSpinner.setAdapter(identities);
        mSpinner.setSelection(mDefaultPos);

        // Set up contacts auto-complete
        List<Person> contacts = new ArrayList<Person>();
        try {
            for (Contact contact : I2PBote.getInstance().getAddressBook().getAll()) {
                contacts.add(new Person(contact.getName(), contact.getBase64Dest(),
                        BoteHelper.decodePicture(contact.getPictureBase64())));
            }
        } catch (PasswordException e) {
            // TODO handle
            e.printStackTrace();
        }
        mAdapter = new FilteredArrayAdapter<Person>(getActivity(), android.R.layout.simple_list_item_1, contacts) {
            @Override
            protected boolean keepObject(Person obj, String mask) {
                mask = mask.toLowerCase(Locale.US);
                return obj.getName().toLowerCase(Locale.US).startsWith(mask) || obj.getAddress().toLowerCase(Locale.US).startsWith(mask);
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v;
                if (convertView == null)
                    v = ((LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE))
                            .inflate(R.layout.listitem_contact, parent, false);
                else
                    v = convertView;
                setViewContent(v, position);
                return v;
            }

            private void setViewContent(View v, int position) {
                Person person = getItem(position);
                ((TextView) v.findViewById(R.id.contact_name)).setText(person.getName());
                ImageView picView = (ImageView) v.findViewById(R.id.contact_picture);
                Bitmap picture = person.getPicture();
                if (picture == null) {
                    ViewGroup.LayoutParams lp = picView.getLayoutParams();
                    picture = BoteHelper.getIdenticonForAddress(person.getAddress(), lp.width, lp.height);
                }
                picView.setImageBitmap(picture);
            }
        };

        mRecipients.setAdapter(mAdapter);
        for (Person recipient : recipients) {
            mRecipients.addObject(recipient);
        }

        if (origSubject != null) {
            String subjectPrefix;
            if (quoteMsgType == QuoteMsgType.FORWARD) {
                subjectPrefix = getResources().getString(
                        hide ? R.string.subject_prefix_fwd_hide
                                : R.string.subject_prefix_fwd);
            } else {
                subjectPrefix = getResources().getString(
                        hide ? R.string.response_prefix_re_hide
                                : R.string.response_prefix_re);
            }
            if (!origSubject.startsWith(subjectPrefix))
                origSubject = subjectPrefix + " " + origSubject;
            mSubject.setText(origSubject);
        }
        if (origContent != null) {
            StringBuilder quotation = new StringBuilder();
            quotation.append("\n\n");
            quotation.append(getResources().getString(
                    hide ? R.string.response_quote_wrote_hide
                            : R.string.response_quote_wrote,
                    origFrom));
            String[] lines = origContent.split("\r?\n|\r");
            for (String line : lines)
                quotation = quotation.append("\n> ").append(line);
            mContent.setText(quotation);
        }

        if (savedInstanceState == null) {
            mRecipients.setPrefix(getResources().getString(R.string.email_to) + " ");
        }

        TextWatcher dirtyWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mDirty = true;
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        mSubject.addTextChangedListener(dirtyWatcher);
        mContent.addTextChangedListener(dirtyWatcher);
    }

    private Person extractPerson(String recipient) {
        if (recipient.equals("Anonymous"))
            return null;

        String recipientName = BoteHelper.extractName(recipient);
        try {
            recipientName = BoteHelper.getName(recipient);
        } catch (PasswordException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        }
        String recipientAddr = BoteHelper.extractEmailDestination(recipient);

        if (recipientAddr == null) { // Assume external address
            recipientAddr = recipient;
            if (recipientName.isEmpty())
                recipientName = recipientAddr;
            return new Person(recipientName, recipientAddr, null, true);
        } else {
            if (recipientName.isEmpty()) // Dest with no name
                recipientName = recipientAddr.substring(0, 5);
            Bitmap recipientPic = null;
            try {
                recipientPic = BoteHelper.getPictureForDestination(recipientAddr);
            } catch (PasswordException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (GeneralSecurityException e) {
                e.printStackTrace();
            }
            return new Person(recipientName, recipientAddr, recipientPic);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.new_email, menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_send_email:
                if (sendEmail())
                    mCallbacks.onTaskFinished();
                return true;

            case android.R.id.home:
                if (mDirty) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    builder.setTitle(R.string.stop_composing_email)
                            .setMessage(R.string.all_changes_will_be_discarded)
                            .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    dialogInterface.dismiss();
                                    getActivity().onNavigateUp();
                                }
                            }).setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.cancel();
                        }
                    }).show();
                    return true;
                } else
                    return super.onOptionsItemSelected(item);

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private boolean sendEmail() {
        Email email = new Email(I2PBote.getInstance().getConfiguration().getIncludeSentTime());
        try {
            // Set sender
            EmailIdentity sender = (EmailIdentity) mSpinner.getSelectedItem();
            InternetAddress ia = new InternetAddress(
                    sender == null ? "Anonymous" :
                            BoteHelper.getNameAndDestination(sender.getKey()));
            email.setFrom(ia);
            // We must continue to set "Sender:" even with only one mailbox
            // in "From:", which is against RFC 2822 but required for older
            // Bote versions to see a sender (and validate the signature).
            email.setSender(ia);

            for (Object obj : mRecipients.getObjects()) {
                Person person = (Person) obj;
                email.addRecipient(Message.RecipientType.TO, new InternetAddress(
                        person.getAddress(), person.getName()));
            }

            // Check that we have someone to send to
            Address[] rcpts = email.getAllRecipients();
            if (rcpts == null || rcpts.length == 0) {
                // No recipients
                mRecipients.setError(getActivity().getString(R.string.add_one_recipient));
                mRecipients.requestFocus();
                return false;
            } else {
                mRecipients.setError(null);
            }

            email.setSubject(mSubject.getText().toString(), "UTF-8");

            // Set the text and add attachments
            email.setContent(mContent.getText().toString(), (List<Attachment>) null);

            // Send the email
            I2PBote.getInstance().sendEmail(email);
            return true;
        } catch (PasswordException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (DataFormatException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (MessagingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (GeneralSecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return false;
    }

    private class IdentityAdapter extends ArrayAdapter<EmailIdentity> {
        private LayoutInflater mInflater;

        public IdentityAdapter(Context context) {
            super(context, android.R.layout.simple_spinner_item);
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            try {
                Collection<EmailIdentity> identities = I2PBote.getInstance().getIdentities().getAll();
                mDefaultPos = 0;
                for (EmailIdentity identity : identities) {
                    add(identity);
                    if ((mSenderKey == null && identity.isDefaultIdentity()) ||
                            (mSenderKey != null && identity.getKey().equals(mSenderKey)))
                        mDefaultPos = getPosition(identity);
                }
            } catch (PasswordException e) {
                // TODO Handle
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Handle
                e.printStackTrace();
            } catch (GeneralSecurityException e) {
                // TODO Handle
                e.printStackTrace();
            }
        }

        @Override
        public EmailIdentity getItem(int position) {
            if (position > 0)
                return super.getItem(position - 1);
            else
                return null;
        }

        @Override
        public int getPosition(EmailIdentity item) {
            if (item != null)
                return super.getPosition(item) + 1;
            else
                return 0;
        }

        @Override
        public int getCount() {
            return super.getCount() + 1;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v;
            if (convertView == null)
                v = mInflater.inflate(android.R.layout.simple_spinner_item, parent, false);
            else
                v = convertView;

            setViewText(v, position);
            return v;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            View v;
            if (convertView == null)
                v = mInflater.inflate(android.R.layout.simple_spinner_dropdown_item, parent, false);
            else
                v = convertView;

            setViewText(v, position);
            return v;
        }

        private void setViewText(View v, int position) {
            TextView text = (TextView) v.findViewById(android.R.id.text1);
            EmailIdentity identity = getItem(position);
            if (identity == null)
                text.setText("Anonymous");
            else
                text.setText(identity.getPublicName());
        }
    }

    public void onBackPressed() {
        if (mDirty) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.stop_composing_email)
                    .setMessage(R.string.all_changes_will_be_discarded)
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                            mCallbacks.onBackPressAllowed();
                        }
                    }).setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.cancel();
                }
            }).show();
        } else
            mCallbacks.onBackPressAllowed();
    }
}
