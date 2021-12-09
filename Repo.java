package gitlet;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.List;
import java.util.TreeSet;
import java.util.Arrays;
import java.util.Set;
import java.util.Collections;

/** Repo object for a .gitlet repository. Stores references to all of the files
 * and classes needed to make the repo work.
 * @author SEANJHANNON */
public class Repo {

    /** The Current Working Directory on the user's computer. */
    private File _CWD;
    /** The hidden .gitlet repository created inside the CWD - this is where
     * the magic happens. */
    private File _GITLETREPO;
    /** Stores the current state of the repo's staging area. */
    private File _STAGING;
    /** The StagingArea object for this repo. */
    private StagingArea _stage;
    /** Stores the current state of the _commits TreeMap, works similarly to
     * _STAGING. */
    private File _COMMITS;
    /** Maps sha1 IDs to Commit objects. */
    private TreeMap<String, Commit> _commits;
    /** Directory storing pointers to the head commits of each branch. */
    private File _BRANCHES;
    /** Stores the sha1 of the commit at the end of the active branch. */
    private File _HEAD;
    /** Stores the head commit of the "master" branch. */
    private File _MASTER;
    /** Directory for storing Blobs in files. */
    private File _BLOBS;

    /** Constructor for a Repo object - creates filepaths for all necessary
     * files and directories. */
    public Repo() {
        _CWD = new File(System.getProperty("user.dir"));
        _GITLETREPO  = Utils.join(_CWD, ".gitlet");
        _COMMITS = Utils.join(_GITLETREPO, "commits");
        _BRANCHES = Utils.join(_GITLETREPO, "branches");
        _HEAD = Utils.join(_BRANCHES, "HEAD");
        _MASTER = Utils.join(_BRANCHES, "master");
        _BLOBS = Utils.join(_GITLETREPO, "blobs");
        _STAGING = Utils.join(_GITLETREPO, "staging" + ".txt");
        _stage = new StagingArea();
        _commits = new TreeMap<>();
    }

    /** The INIT command - creates files and directories from filepaths in
     * contructor, creates initial commit. */
    public void init() {
        if (Utils.join(System.getProperty("user.dir"),
                ".gitlet").exists()) {
            String msg = "A Gitlet version-control system already "
                    + "exists in the current directory.";
            Utils.message(msg);
            return;
        }
        _GITLETREPO.mkdir();
        _BRANCHES.mkdir();
        _BLOBS.mkdir();
        Utils.writeObject(_STAGING, _stage);
        Commit initialCommit = new Commit("initial commit",
                null, null);
        _commits.put(initialCommit.getSHA(), initialCommit);
        Utils.writeObject(_COMMITS, _commits);
        Utils.writeContents(_MASTER, initialCommit.getSHA());
        Utils.writeContents(_HEAD, "master");
    }

    /** The ADD command - creates blob from specified file and stores it in
     * _BLOBS, updates hashmap in ADD.
     * @param filename - name of the file to be staged for addition */
    public void add(String filename) {
        if (!Utils.join(_CWD, filename).exists()) {
            throw new GitletException("File does not exist.");
        }
        File target = Utils.join(_CWD, filename);
        Blob toAdd = new Blob(target);
        String toAddName = toAdd.getFname();
        File toAddFile = Utils.join(_BLOBS, toAdd.getSHA());
        _stage = getStage();
        Utils.writeContents(toAddFile, toAdd.getContents());
        Commit currhead = getHead();

        if (_stage.getRemove().contains(filename)) {
            _stage.getRemove().remove(filename);
            Utils.writeObject(_STAGING, _stage);
        }
        if (currhead.getBlobs().containsValue(toAdd.getSHA())
                && currhead.getBlobs().get(filename).equals(toAdd.getSHA())) {
            _stage = getStage();
            _stage.unstage(filename);
            Utils.writeObject(_STAGING, _stage);
            return;
        }
        _stage.add(filename, toAdd.getSHA());
        Utils.writeObject(_STAGING, _stage);
    }

    /** the COMMIT command - clone head commit, save "snapshot" of files in add,
     * save it all to a new file in COMMIT.
     * @param message - the commit message
     * @param mergeparent - second parent a commit can gain through a merge */
    public void commit(String message, String mergeparent) {
        if (message.length() <= 0) {
            throw new GitletException("Please enter a commit message. ");
        }
        _stage = getStage();
        Commit newCommit = new Commit(message, getHead().getSHA(), mergeparent);
        for (String key: getHead().getBlobs().keySet()) {
            if (!_stage.getRemove().contains(key)) {
                newCommit.getBlobs().put(key, getHead().getBlobs().get(key));
            }
        }
        if (_stage.getAdd().isEmpty() && _stage.getRemove().isEmpty()) {
            throw new GitletException("No changes added to the commit.");
        }
        for (String key : _stage.getAdd().keySet()) {
            String shaiD = _stage.getAdd().get(key);
            if (!newCommit.getBlobs().containsValue(shaiD)) {
                newCommit.getBlobs().put(key, shaiD);
            }
        }
        _commits = getCommits();
        _commits.put(newCommit.getSHA(), newCommit);
        Utils.writeObject(_COMMITS, _commits);
        updateActiveBranch(newCommit.getSHA());
        _stage.clear();
        Utils.writeObject(_STAGING, _stage);
    }

    /** The RM command - unstages file / stages file for removal
     * / removes file from CWD.
     * @param filename - name of file to be removed */
    public void rm(String filename) {
        _stage = getStage();
        boolean staged = false;
        boolean tracked = false;
        if (_stage.getAdd().containsKey(filename)) {
            staged = true;
            _stage.unstage(filename);
            Utils.writeObject(_STAGING, _stage);
        }
        _stage = getStage();
        if (getHead().getBlobs().containsKey(filename)) {
            tracked = true;
            _stage.remove(filename);
            _stage.unstage(filename);
            Utils.restrictedDelete(filename);
            Utils.writeObject(_STAGING, _stage);
        }
        if (!staged && !tracked) {
            throw new GitletException("No reason to remove the file.");
        }
    }

    /** The LOG command - prints out commits starting at HEAD in a tidy
     * fashion. */
    public void log() {
        _commits = getCommits();
        Commit head = getHead();
        while (head != null) {
            printLog(head);
            if (head.getParent() != null) {
                head = _commits.get(head.getParent());
            } else {
                break;
            }
        }
    }

    /** The GLOBAL-LOG command - prints all commits ever regardless of branch
     * . */
    public void globalLog() {
        List<String> keys = new ArrayList<>(getCommits().keySet());
        Collections.reverse(keys);
        for (String id : keys) {
            Commit h = getCommits().get(id);
            printLog(h);
        }
    }

    /** The FIND command - prints out the sha1 of all commits with the
     * given commit message or throws an error.
     * @param message - message of the commit we want to find */
    public void find(String message) {
        _commits = getCommits();
        boolean contains = false;
        for (String key : _commits.keySet()) {
            if (_commits.get(key).getMessage().equals(message)) {
                System.out.println(_commits.get(key).getSHA());
                contains = true;
            }
        }
        if (!contains) {
            throw new GitletException("Found no commit with that message.");
        }
    }

    /** The STATUS command - prints out all Branches, filed staged for Addition
     * and Removal, Unstaged Changes, Untracked Files. */
    public void status() {
        ArrayList<String> branches = new ArrayList<>();
        for (String branch : Utils.plainFilenamesIn(_BRANCHES)) {
            if (!branch.equals("HEAD")) {
                if (branch.equals(Utils.readContentsAsString(_HEAD))) {
                    branches.add("*" + branch);
                } else {
                    branches.add(branch);
                }
            }
        }
        ArrayList<String> staged = new ArrayList<>();
        _stage = getStage();
        for (String filename : _stage.getAdd().keySet()) {
            staged.add(filename);
        }
        ArrayList<String> removed = _stage.getRemove();
        ArrayList<String> unstaged = new ArrayList<>();
        for (String file : Utils.plainFilenamesIn(_CWD)) {
            byte[] cwdContents = Utils.readContents(Utils.join(_CWD, file));
            if (getHead().getBlobs().containsKey(file) && Utils.join(_BLOBS,
                    getHead().getBlobs().get(file)).exists()) {
                byte[] commitContents = Utils.readContents(Utils.join(_BLOBS,
                        getHead().getBlobs().get(file)));
                if (!Arrays.equals(cwdContents, commitContents)
                        && !_stage.getAdd().containsKey(file)) {
                    unstaged.add(file + " (modified)");
                }
            }
            if (_stage.getAdd().containsKey(file) && !staged.contains(file)
                    && !cwdContents.equals(Utils.readContents(Utils.join(_BLOBS,
                    _stage.getAdd().get(file))))) {
                unstaged.add(file + " (modified)");
            }
        }
        for (String file : _stage.getAdd().keySet()) {
            if (!Utils.plainFilenamesIn(_CWD).contains(file)) {
                unstaged.add(file + " (deleted)");
            }
        }
        for (String file : getHead().getBlobs().keySet()) {
            if (!Utils.plainFilenamesIn(_CWD).contains(file)
                    && !_stage.getRemove().contains(file)) {
                unstaged.add(file + " (deleted)");
            }
        }

        ArrayList<String> untracked = new ArrayList<>();
        for (String file : Utils.plainFilenamesIn(_CWD)) {
            if (!getHead().getBlobs().containsKey(file)
                    && !_stage.getAdd().containsKey(file)) {
                untracked.add(file);
            }
        }
        statusOutput(branches, staged, removed, unstaged, untracked);
    }

    /** Handles printing the output for a call to STATUS.
     * @param branches - ArrayList of branches in the current repo
     * @param staged - Files currently staged for addition
     * @param removed - Files currently staged for removal
     * @param unstaged - Files whose modifications are not staged for commit
     * @param untracked - Files in _CWD not tracked or staged for addition */
    public void statusOutput(ArrayList<String> branches,
                             ArrayList<String> staged,
                             ArrayList<String> removed,
                             ArrayList<String> unstaged,
                             ArrayList<String> untracked) {
        System.out.println("=== Branches ===");
        for (String branch : branches) {
            System.out.println(branch);
        }
        System.out.print("\n");
        System.out.println("=== Staged Files ===");
        for (String file : staged) {
            System.out.println(file);
        }
        System.out.print("\n");
        System.out.println("=== Removed Files ===");
        for (String file : removed) {
            System.out.println(file);
        }
        System.out.print("\n");
        System.out.println("=== Modifications Not Staged For Commit ===");
        for (String file : unstaged) {
            System.out.println(file);
        }
        System.out.print("\n");
        System.out.println("=== Untracked Files ===");
        for (String file : untracked) {
            System.out.println(file);
        }
        System.out.print("\n");
    }

    /** CHECK this OUT! 3 methods for the price of 1! Overwrites CWD
     * with specified files.
     * @param args - user input specifying the type of checkout */
    public void checkout(String... args) {
        if (args.length == 3) {
            checkout1(args);
        }
        if (args.length == 4) {
            checkout2(args);
        }
        if (args.length == 2) {
            checkout3(args);
        }
    }

    /** CHECKOUT CASE 1: args in format "checkout -- [file name]", checks out
     * specified file from the head commit.
     * @param args - the arguments passed into checkout
     */
    public void checkout1(String... args) {
        String filename = args[2];
        Commit headCommit = getHead();
        if (!headCommit.getBlobs().containsKey(filename)) {
            throw new GitletException("File does not "
                    + "exist in that commit.");
        }
        if (headCommit.getBlobs().containsKey(filename)) {
            blobOverwrite(filename, headCommit);
        }
    }

    /** CHECKOUT CASE 2: args in format "checkout [commit ID] -- [file name],
     * checks out specified file from specified commit.
     * @param args - the arguments passed into checkout
     */
    public void checkout2(String... args) {
        String commitID = abbrevSHASearch(args[1]);
        String filename = args[3];
        if (!args[2].equals("--")) {
            throw new GitletException("Incorrect operands.");
        }
        _commits = getCommits();
        Commit c = _commits.get(commitID);
        if (c == null) {
            throw new GitletException("No commit with that id exists.");
        }
        if (c.getBlobs().containsKey(filename)) {
            blobOverwrite(filename, c);
        } else {
            throw new GitletException("File does not "
                    + "exist in that commit.");
        }
    }

    /** CHECKOUT CASE 3: args in format "checkout [branch name]", checks out
     * all files tracked in the head of specified branch.
     * @param args - the arguments passed into checkout
     */
    public void checkout3(String... args) {
        String branchName = args[1];
        File branch = Utils.join(_BRANCHES, branchName);
        _commits = getCommits();
        if (!Utils.join(_BRANCHES, branchName).exists()) {
            throw new GitletException("No such branch exists.");
        }
        Commit branchHead = _commits.get(Utils.readContentsAsString(branch));
        Commit currentHead = getHead();
        for (String file : Utils.plainFilenamesIn(_CWD)) {
            if (!currentHead.getBlobs().containsKey(file)) {
                if (branchHead.getBlobs().containsKey(file)) {
                    byte[] cwdFileContents =
                            Utils.readContents(Utils.join(_CWD, file));
                    byte[] overWriteContents =
                            Utils.readContents(
                                    Utils.join(_BLOBS, branchHead.getBlobs()
                                            .get(file)));
                    if (!cwdFileContents.equals(overWriteContents)) {
                        throw new GitletException("There is an "
                                + "untracked file in the way; delete it, "
                                + "or add and commit it first.");
                    }
                }
            }
        }
        if (branchName.equals(Utils.readContentsAsString(_HEAD))) {
            throw new GitletException("No need to checkout "
                    + "the current branch.");
        }
        for (String file : branchHead.getBlobs().keySet()) {
            blobOverwrite(file, branchHead);
        }
        for (String file : getHead().getBlobs().keySet()) {
            if (!branchHead.getBlobs().keySet().contains(file)) {
                Utils.restrictedDelete(file);
            }
        }
        _stage.clear();
        Utils.writeContents(_HEAD, branchName);
    }

    /** The BRANCH command - creates a new branch file that points at
     * the current head commit.
     * @param branchName - name of new branch to be created */
    public void branch(String branchName) {
        for (String branch : Utils.plainFilenamesIn(_BRANCHES)) {
            if (branch.equals(branchName)) {
                throw new GitletException("A branch with "
                        + "that name already exists.");
            }
        }
        File newBranch = Utils.join(_BRANCHES, branchName);
        Utils.writeContents(newBranch, getHead().getSHA());
    }

    /** The RM-BRANCH command - deletes the specified branch pointer.
     * @param branchName - name of branch to remove */
    public void rmBranch(String branchName) {
        if (branchName.equalsIgnoreCase(Utils.readContentsAsString(_HEAD))) {
            throw new GitletException("Cannot remove the current branch.");
        } else if (Utils.join(_BRANCHES, branchName).exists()) {
            Utils.join(_BRANCHES, branchName).delete();
        } else {
            throw new GitletException("A branch with that "
                    + "name does not exist.");
        }
    }

    /** The RESET command - checks out a specific commit.
     * @param commitID - sha1 of the commit being reset to */
    public void reset(String commitID) {
        String currentBranch = Utils.readContentsAsString(_HEAD);
        _commits = getCommits();
        _stage = getStage();
        if (!_commits.containsKey(commitID)) {
            throw new GitletException("No commit with that id exists.");
        }
        Commit newHead = _commits.get(commitID);
        for (String file : Utils.plainFilenamesIn(_CWD)) {
            if (!getHead().getBlobs().containsKey(file)
                    && newHead.getBlobs().containsKey(file)) {
                throw new GitletException("There is an untracked "
                        + "file in the way; delete it, or "
                        + "add and commit it first.");
            }
        }
        for (String file : newHead.getBlobs().keySet()) {
            blobOverwrite(file, newHead);
        }
        for (String file : Utils.plainFilenamesIn(_CWD)) {
            if (!newHead.getBlobs().containsKey(file)) {
                Utils.restrictedDelete(file);
            }
        }
        _stage.clear();
        Utils.writeObject(_STAGING, _stage);
        Utils.writeContents(Utils.join(_BRANCHES, currentBranch),
                newHead.getSHA());
        Utils.writeContents(_HEAD, currentBranch);
    }

    /** The MERGE command - Merges files from the given branch
     * into the current branch.
     * @param branchName - name of the branch to merge with the active branch */
    public void merge(String branchName) {
        if (!Utils.join(_BRANCHES, branchName).exists()) {
            throw new GitletException("A branch with that "
                    + "name does not exist.");
        }
        if (branchName.equals(Utils.readContentsAsString(_HEAD))) {
            throw new GitletException("Cannot merge a branch with itself.");
        }
        _stage = getStage();
        if (!_stage.getAdd().keySet().isEmpty()
                || !_stage.getRemove().isEmpty()) {
            throw new GitletException("You have uncommitted changes.");
        }
        boolean conflict = false;
        _commits = getCommits();
        Commit head = getHead();
        Commit mergeHead = _commits.get(Utils.readContentsAsString
                (Utils.join(_BRANCHES, branchName)));
        Commit commonAncestor = _commits.get(findSplit(head, mergeHead));
        if (commonAncestor.getSHA().equals(mergeHead.getSHA())) {
            throw new GitletException("Given branch is an "
                    + "ancestor of the current branch.");
        }
        if (whosYourDaddy(head).contains(mergeHead.getSHA())
                || whosYourDaddy(mergeHead).contains(head.getSHA())) {
            checkout("checkout", branchName);
            System.out.println("Current branch fast-forwarded.");
            return;
        }
        for (String file : Utils.plainFilenamesIn(_CWD)) {
            if (!getHead().getBlobs().containsKey(file)
                    && mergeHead.getBlobs().containsKey(file)) {
                throw new GitletException("There is an untracked file in "
                        + "the way; delete it, or add and commit it first.");
            }
        }
        Set<String> allFiles = new TreeSet<>(head.getBlobs().keySet());
        allFiles.addAll(mergeHead.getBlobs().keySet());
        allFiles.addAll(commonAncestor.getBlobs().keySet());
        int i = 0;
        for (String file : allFiles) {
            boolean occured = mergeLogic(commonAncestor, head, mergeHead, file);
            if (occured) {
                i++;
            }
        }
        if (i > 0) {
            conflict = true;
        }
        if (_stage.getAdd().isEmpty() && _stage.getRemove().isEmpty()) {
            throw new GitletException("No changes added to the commit.");
        }
        commit("Merged " + branchName + " into "
                + Utils.readContentsAsString(_HEAD) + ".", mergeHead.getSHA());
        if (conflict) {
            System.out.println("Encountered a merge conflict.");
        }
    }

    /** Helper for MERGE - finds closest common ancestor given two commits.
     * @param head - head commit of current branch
     * @param other - head commit of other branch
     * @return the sha1 of the commit at the split point */
    public String findSplit(Commit head, Commit other) {
        _commits = getCommits();
        ArrayList<String> headAncestry = whosYourDaddy(head);
        ArrayList<String> otherAncestry = whosYourDaddy(other);
        for (String ancestor : headAncestry) {
            if (otherAncestry.contains(ancestor)) {
                return ancestor;
            }
        }
        return null;
    }

    /** Helper for the helper - returns the ancestry of a commit.
     * @param c - the commit whose ancestry we want */
    public ArrayList<String> whosYourDaddy(Commit c) {
        ArrayList<String> ancestry = new ArrayList<>();
        ancestry.add(c.getSHA());
        while (c != null) {
            if (c.getMergeParent() != null) {
                ancestry.add(c.getMergeParent());
            }
            ancestry.add(c.getParent());
            if (c.getParent() != null) {
                c = _commits.get(c.getParent());
            } else {
                break;
            }
        }
        return ancestry;
    }

    /** Helper for MERGE - handles the logic of comparing contents
     * of commits HEAD and OTHER.
     * @param split - the split point, or most recent common ancestor
     * @param head - the head commit of the active branch
     * @param other - the head commit of the branch being merged with
     * @param file - the name of the file
     * @return boolean indicating if a conflict occurred or not */
    public boolean mergeLogic(Commit split, Commit head,
                              Commit other, String file) {
        HashMap<String, String> s = split.getBlobs();
        HashMap<String, String> h = head.getBlobs();
        HashMap<String, String> o = other.getBlobs();
        _stage = getStage();
        if (s.containsKey(file)) {
            if (s.get(file).equals(h.get(file)) && !s.get(file)
                    .equals(o.get(file)) && o.containsKey(file)) {
                checkout("checkout", other.getSHA(), "--", file);
                _stage.add(file, o.get(file));
                Utils.writeObject(_STAGING, _stage);
                return false;
            }
            if (s.get(file).equals(o.get(file))
                    && !s.get(file).equals(h.get(file))
                    && h.containsKey(file)) {
                return false;
            }
            if (h.containsKey(file) && o.containsKey(file)
                    && h.get(file).equals(o.get(file))
                    || !h.containsKey(file) && !o.containsKey(file)) {
                return false;
            }
            if (s.get(file).equals(h.get(file)) && !o.containsKey(file)) {
                rm(file);
                Utils.writeObject(_STAGING, _stage);
                return false;
            }
            if (s.get(file).equals(o.get(file)) && !h.containsKey(file)) {
                return false;
            }
        }
        if (!s.containsKey(file)) {
            if (h.containsKey(file) && !o.containsKey(file)) {
                return false;
            }
            if (!h.containsKey(file) && o.containsKey(file)) {
                checkout("checkout", other.getSHA(), "--", file);
                _stage.add(file, o.get(file));
                Utils.writeObject(_STAGING, _stage);
                return false;
            }
        }
        if (s.containsKey(file) && !s.get(file).equals(h.get(file))
                && !s.get(file).equals(o.get(file))
                && h.containsKey(file) && o.containsKey(file)
                && !h.get(file).equals(o.get(file))
                || s.containsKey(file) && h.containsKey(file)
                && !s.get(file).equals(h.get(file)) && !o.containsKey(file)
                || s.containsKey(file) && o.containsKey(file)
                && !s.get(file).equals(o.get(file)) && !h.containsKey(file)
                || !s.containsKey(file) && !h.get(file).equals(o.get(file))) {
            return mergeConflict(h, o, file);
        }

        Utils.writeObject(_STAGING, _stage);
        return false;
    }

    /** Handles the conflict situation during amerge and overwrites file
     * accordingly.
     * @param h - HashMap contained in the head commit
     * @param o - HashMap contained in the "other" commit
     * @param file - Filename
     * @return - boolean indicating if there was a conflict
     */
    public boolean mergeConflict(HashMap<String,
        String> h, HashMap<String, String> o, String file) {
        String headContents, otherContents;
        if (h.containsKey(file)) {
            headContents = Utils.readContentsAsString(Utils.join
                    (_BLOBS, h.get(file)));
        } else {
            headContents = "";
        }
        if (o.containsKey(file)) {
            otherContents = Utils.readContentsAsString(Utils.join
                    (_BLOBS, o.get(file)));
        } else {
            otherContents = "";
        }
        File iRememberYouWasConflicted = Utils.join(_CWD, file);
        String contents = "<<<<<<< HEAD" + "\n"
                + headContents
                + "=======" + "\n"
                + otherContents
                + ">>>>>>>" + "\n";
        Utils.writeContents(iRememberYouWasConflicted, contents);
        Blob newBlob = new Blob(iRememberYouWasConflicted);
        _stage.add(file, newBlob.getSHA());
        Utils.writeObject(_STAGING, _stage);
        return true;
    }



    /** Returns the most recent COMMIT in the HEAD branch. */
    @SuppressWarnings("unchecked")
    public Commit getHead() {
        String headName = Utils.readContentsAsString(_HEAD);
        File headFile = Utils.join(_BRANCHES, headName);
        String headID = Utils.readContentsAsString(headFile);
        _commits = Utils.readObject(_COMMITS, TreeMap.class);
        Commit currHead = _commits.get(headID);
        return currHead;
    }


    /** Returns the TreeMap of COMMIT objects representing
     * all commits ever made. */
    @SuppressWarnings("unchecked")
    public TreeMap<String, Commit> getCommits() {
        return Utils.readObject(_COMMITS, TreeMap.class);
    }

    /** Returns the StagingArea. */
    public StagingArea getStage() {
        return Utils.readObject(_STAGING, StagingArea.class);
    }

    /** Avoids duplicate code in log and global-log.
     * @param commit - the commit whose contents will get printed */
    public void printLog(Commit commit) {
        SimpleDateFormat formatter = new
                SimpleDateFormat("EEE MMM d HH:mm:ss Y Z");
        System.out.println("===");
        System.out.println("commit " + commit.getSHA());
        System.out.println("Date: " + formatter.format
                (commit.getTimeStampDate()));
        System.out.println(commit.getMessage());
        System.out.print("\n");
    }

    /** Helper method for switching between branches.
     * @param newSHA - ID of new head commit */
    public void updateActiveBranch(String newSHA) {
        String activeBranchName = Utils.readContentsAsString(_HEAD);
        File activeBranchFile = Utils.join(_BRANCHES, activeBranchName);
        Utils.writeContents(activeBranchFile, newSHA);
    }

    /** Helper method for writing the contents of a Blob to a specified file.
     * @param filename - name of file to overwrite
     * @param blobSource - the blob containing the contents */
    public void blobOverwrite(String filename, Commit blobSource) {
        String blobSHA = blobSource.getBlobs().get(filename);
        File blobFilePath = Utils.join(_BLOBS, blobSHA);
        byte[] writeThis = Utils.readContents(blobFilePath);
        File overwriteMe = Utils.join(_CWD, filename);
        Utils.writeContents(overwriteMe, writeThis);
    }

    /** Handles abbreviated SHA1 codes.
     * @param abbrev - the abbreviated SHA-1 code
     * @return the full length sha1 */
    public String abbrevSHASearch(String abbrev) {
        final int len = 40;
        if (abbrev.length() == len) {
            return abbrev;
        }
        _commits = getCommits();
        for (String key : _commits.keySet()) {
            if (key.startsWith(abbrev)) {
                return key;
            }
        }
        throw new GitletException("No commit with that id exists.");
    }

    /** Getter method for the Repo itself.
     * @return the repo */
    public File getGitRepo() {
        return _GITLETREPO;
    }




}


