package csse2002.block.world;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * Handles top-level interaction of performing actions on a world map.
 */
public class Main {

    /**
     * The entry point of the application.
     * 
     * Takes 3 parameters an input map file (args[0]), actions (args[1]),
     * and an output map file (args[2]).
     * 
     * The actions parameter can be either a filename, or the string "System.in". 
     * 
     * This function does the following:
     * <ol>
     * <li> If there are not 3 parameters, (i.e. args.length != 3),
     *     print "Usage: program inputMap actions outputMap"
     *     using System.err.println() and then exit with status 1) </li>
     * <li> Create a new WorldMap using the input map file. If an
     *     exception is thrown, print the exception to the console using
     *     System.err.println(), and then exit with status 2. </li>
     * <li> Create a BufferedReader to read actions. If parameter 2 is
     *     a filename, the BufferedReader should be initialised using a
     *     new FileReader. If parameter 2 is the string "System.in", the
     *     buffered reader should be initialised using System.in and
     *     a new InputStreamReader. If an exception is thrown, print
     *     the exception to the console using System.err.println, and
     *     then exit with status 3. </li>
     * <li> Call Action.processActions() using the created BufferedReader
     *     and WorldMap. If an exception is thrown, print the exception to the
     *     console using System.err.println, and then exit with
     *     status 4. </li>
     * <li> Call WorldMap.saveMap() using the 3rd parameter to save the map
     *     to an output file. If an exception is thrown, print the exception
     *     to the console using System.err.println() and then exit with status 5.
     *     </li>
     * </ol>
     * 
     * To print an exception to System.err, use System.err.println(e), where
     * e is the caught exception.
     * @param args the input arguments to the program
     */
    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: program inputMap actions outputMap");
            System.exit(1);
        }

        // Exit code if the code throws at each point.
        int exitCode = -1;

        try {
            exitCode = 2;
            WorldMap map = new WorldMap(args[0]);

            exitCode = 3;
            Reader internalReader;
            if (args[1].equals("System.in")) {
                internalReader = new InputStreamReader(System.in);
            } else {
                internalReader = new FileReader(args[1]);
            }
            BufferedReader reader = new BufferedReader(internalReader);

            exitCode = 4;
            Action.processActions(reader, map);

            exitCode = 5;
            map.saveMap(args[2]);

        } catch (BlockWorldException | ActionFormatException
                | IOException e) {
            // Print and exit with the appropriate exit code.
            System.err.println(e);
            System.exit(exitCode);
        }
    }
}
