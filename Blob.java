package gitlet;

import java.io.File;
import java.io.Serializable;

/** The BLOB class - another building block of a good ditlet repo. Blobs are
 * unique and each store snapshots of a particular file from a particular
 * moment in time. They are kept in files where each filename is the SHA-1 of
 * the BLOB object it contains.
 * @author SEANJHANNON */
public class Blob implements Serializable {
    /** The contents of the file the BLOB was created from. */
    private byte[] _contents;
    /** The file's name! */
    private String _fname;

    /** The BLOB constructor! Pretty simple, just reads the contents of a file
     * into itself in a deepcopy-esque fashion.
     * @param file - the file to read from */
    public Blob(File file) {
        _contents = Utils.readContents(file);
        _fname = file.getName();
    }

    /** Getter method for the contents of a blob, which are in turn the
     * contents of a file in the CWD at a particular moment in time.
     * @return exactly what you think it would */
    public byte[] getContents() {
        return _contents;
    }

    /** Similar to Commit's getSHA method, simplifies a lot of code.
     * @return the SHA-1 */
    public String getSHA() {
        return Utils.sha1(Utils.serialize(this), "blob");
    }
    /** Getter method for the file's name.
     * @return the filename */
    public String getFname() {
        return _fname;
    }
}
