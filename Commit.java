package gitlet;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Objects;

/** The COMMIT class - super essential! Each unique commit contains metadata
 * about a particular version of the CWD along with a reference to its parent(s)
 * @author SEANJHANNON */
public class Commit implements Serializable {

    /** Holds the commit's message. Doesn't need to be unique. */
    private String _message;
    /** Maps filenames to the SHA-1 of the blob holding a snapshot of its
     * contents. */
    private HashMap<String, String> _blobs;
    /** Stores the SHA-1 of the COMMIT's parent. Null if initial commit. */
    private String _parent;
    /** Stores the SHA-1 of the COMMIT's merge parent, if it has one. */
    private String _mergeParent;
    /** Stores the exact moment at which a COMMIT is initialized. */
    private Date _timestamp;


    /** The constructor for a COMMIT object - does all the important setup.
     * @param message - the commit message
     * @param mergeparent - the merge parent
     * @param parent - the parent */
    public Commit(String message, String parent, String mergeparent) {
        _message = message;
        _timestamp = new Date();
        _blobs = new HashMap<>();
        _parent = parent;
        _mergeParent = mergeparent;

        if (Objects.equals(message, "initial commit")) {
            _timestamp = new Date(0);
            _parent = null;
        }

    }

    /** Generates the SHA-1 code for a commit.
     * @return the sha */
    public String getSHA() {
        return Utils.sha1(Utils.serialize(this), "commit");
    }

    /** Getter method for a COMMIT's message.
     * @return the message */
    public String getMessage() {
        return _message;
    }

    /** Getter method for a COMMIT's timestamp as a DATE object.
     * @return the date */
    public Date getTimeStampDate() {
        return _timestamp;
    }

    /** Getter method for a COMMIT's BLOB references.
     * @return the HashMap of blobs*/
    public HashMap<String, String> getBlobs() {
        return _blobs;
    }

    /** Getter method for a COMMIT's parent as a SHA-1 String.
     * @return the commit's parent */
    public String getParent() {
        return _parent;
    }

    /** Getter method for a COMMIT's mergeparent.
     * @return the commit's merge parent */
    public String getMergeParent() {
        return _mergeParent; }

}
