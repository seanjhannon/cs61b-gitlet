package gitlet;


/** Driver class for Gitlet, the tiny super rad version-control system.
 *  @author SEANJHANNON */
public class Main {

    /** This is where the magic happens!
     * @param args - user input */
    public static void main(String... args) {
        try {
            Main.oOoOoO(args);
            return;
        } catch (GitletException e) {
            System.err.printf("%s%n", e.getMessage());
        }
        System.exit(0);

    }

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND>. */
    public static void oOoOoO(String... args) {
        Repo gitletRepo = new Repo();
        if (args.length < 1) {
            throw new GitletException("Please enter a command.");
        }
        if (!args[0].equals("init") && !gitletRepo.getGitRepo().exists()) {
            throw new
                    GitletException("Not in an initialized Gitlet directory.");
        }

        switch (args[0]) {
        case "init": gitletRepo.init();
        break;

        case "add": gitletRepo.add(args[1]);
        break;

        case "commit": gitletRepo.commit(args[1], null);
        break;

        case "log": gitletRepo.log();
        break;

        case "global-log": gitletRepo.globalLog();
        break;

        case "find": gitletRepo.find(args[1]);
        break;

        case "status": gitletRepo.status();
        break;

        case "checkout": if (args.length != 3
                && args.length != 4 && args.length != 2) {
                throw new GitletException("Incorrect operands.");
            }
            gitletRepo.checkout(args);
        break;

        case "branch": gitletRepo.branch(args[1]);
        break;

        case "rm-branch": gitletRepo.rmBranch(args[1]);
        break;

        case "rm" : gitletRepo.rm(args[1]);
        break;

        case "reset" : gitletRepo.reset(args[1]);
        break;

        case "merge" : gitletRepo.merge(args[1]);
        break;

        default: throw new GitletException("No command with that name exists.");

        }
    }
}
