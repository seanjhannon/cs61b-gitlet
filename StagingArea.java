package gitlet;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

/** Staging area object for storing all files staged for addition or removal.
 * @author SEANJHANNON */
public class StagingArea implements Serializable {
    /** ADD is a hashmap mapping filenames of staged files to sha1s of blobs. */
    private HashMap<String, String> _add;
    /** REMOVE is an ArrayList of Strings storing files staged for removal. */
    private ArrayList<String> _remove;

    /** The constructor - initializes data structures for staged files. */
    public StagingArea() {
        _add = new HashMap<>();
        _remove = new ArrayList<>();
    }

    /** CLEAR method - removes everything currently in the staging area. */
    public void clear() {
        _add = new HashMap<>();
        _remove = new ArrayList<>();
    }

    /** ADD method - stages a file for addition.
     * @param filename - name of file to stage
     * @param sha1 - sha1 of the blob holding the file's contents */
    public void add(String filename, String sha1) {
        _add.put(filename, sha1);
    }

    /** REMOVE method - stages a file for removal.
     * @param filename - name of file to stage for removal */
    public void remove(String filename) {
        _remove.add(filename);
    }

    /** Getter method that returns all files staged for addition. */
    public HashMap<String, String> getAdd() {
        return _add;
    }

    /** Getter method that returns all files staged for removal. */
    public ArrayList<String> getRemove() {
        return _remove;
    }

    /** Unstages file that is staged for addition.
     * @param filename - name of the file to unstage */
    public void unstage(String filename) {
        _add.remove(filename);
    }
}
