package csse2002.block.world;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class to store a world map
 */
public class WorldMap {

    /**
     * Enum mapping block type strings (used in the world map file format) to
     * their Java classes.
     */
    @SuppressWarnings("WeakerAccess")
    private enum BlockTypes {
        wood(WoodBlock.class),
        grass(GrassBlock.class),
        soil(SoilBlock.class),
        stone(StoneBlock.class);

        private final Class<? extends Block> blockClass;

        BlockTypes(Class<? extends Block> blockClass) {
            this.blockClass = blockClass;
        }

        /**
         * Instantiates a new object of the block's type.
         * @return New block object.
         */
        public Block newInstance() {
            try {
                return blockClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                // The enum values we define should never result in these
                // exceptions.
                throw new AssertionError(
                        "Exception while instantiating block class.", e);
            }
        }

        /**
         * Accepts a list of block types as strings and returns a list of
         * block instances given by the input strings.
         * @param blockTypes List of block type strings.
         * @return List of block instances.
         * @throws WorldMapFormatException If there is no matching block type
         * for some input string.
         */
        public static List<Block> makeBlockList(List<String> blockTypes)
                throws WorldMapFormatException {
            List<Block> blocks = new ArrayList<>();
            for (String blockType : blockTypes) {
                try {
                    blocks.add(BlockTypes.valueOf(blockType).newInstance());
                } catch (IllegalArgumentException e) {
                    throw new WorldMapFormatException();
                }
            }
            return blocks;
        }

        /**
         * Accepts a list of block types in an array and returns a list of
         * instances of those blocks.
         * @param blockTypesArray Block types as array list.
         * @return List of block instances.
         * @throws WorldMapFormatException If there is no matching block type
         * for an input string.
         */
        public static List<Block> makeBlockList(String[] blockTypesArray)
                throws WorldMapFormatException {
            return makeBlockList(Arrays.asList(blockTypesArray));
        }
    }

    /**
     * Class representing a pair of related objects.
     * @param <L> Left type.
     * @param <R> Right type.
     */
    private static class Pair<L, R> {
        public final L left;
        public final R right;

        /**
         * Constructs a new Pair with the given left and right values.
         * @param left Left parameter.
         * @param right Right parameter.
         */
        public Pair(L left, R right) {
            this.left = left;
            this.right = right;
        }
    }

    /** The world's builder. */
    private Builder builder;

    /** Starting position. */
    private Position startPosition;

    /** Sparse tile array storing map data. */
    private final SparseTileArray sparseArray = new SparseTileArray();

    /**
     * Constructs a new block world map from a startingTile, position and
     * builder.
     * 
     * @param startingTile the tile which the builder starts on.
     * @param startPosition the position of the starting tile.
     * @param builder the builder who will traverse the block world.
     * @throws WorldMapInconsistentException if there are inconsistencies
     *          in the positions of tiles (such as two tiles at a single
     *          position).
     * @require startingTile != null, startPosition != null, builder != null
     */
    public WorldMap(Tile startingTile,
                    Position startPosition,
                    Builder builder)
             throws WorldMapInconsistentException {
        sparseArray.addLinkedTiles(
                startingTile, startPosition.getX(), startPosition.getY());
        this.startPosition = startPosition;
        this.builder = builder;
    }

    /**
     * Construct a block world map from the given filename.
     *
     * The file's format must exactly be the following:
     * <ol>
     *     <li>Two lines with one integer per line. These will be the
     *     starting x and y coordinates, respectively.</li>
     *     <li>One line for the builder's name.</li>
     *     <li>One line of comma-separated (no space) strings of block types
     *     (see below) for the builder's inventory. Can be blank for an empty
     *     inventory.</li>
     *     <li>One blank line.</li>
     *     <li>One line of "total:N" where N is an integer > 0. N represents
     *     the number of tiles.</li>
     *     <li>The next N lines is the tiles section and must contain
     *     "[n] [blocks]" where n is in [0,N) and [blocks] is a comma-separated
     *     (no spaces) list of blocks on the tile. Each n must appear on exactly
     *     one line.</li>
     *     <li>One blank line.</li>
     *     <li>One line of exactly "exits". This starts the exits section.</li>
     *     <li>N lines of "n [exits]" where n is in [0,N), each n appearing
     *     on exactly one line. [exits] is a comma-separated list of
     *     "[direction]:[tileID]" where [direction] is one of "north", "east",
     *     "south" or "west" (case-sensitive) and [tileID] is in [0,N). Each
     *     direction can appear at most once. A line of "n" or "n " with no
     *     [exits] is allowed if a tile has no exits.</li>
     * </ol>
     *
     * Valid block types are "grass", "wood", "stone" and "soil".
     *
     * If the file is not in the above format, a {@link WorldMapFormatException}
     * will be thrown. Also, this will be thrown if:
     * <ul>
     *     <li>The builder's inventory contains blocks which can't be carried.
     *     </li>
     *     <li>A tile contains a ground block with 3 or more blocks before it,
     *     or any block with 8 or more blocks before it.</li>
     *     <li>Any given integer is not in [-2^31, 2^31-1].</li>
     * </ul>
     *
     * Additionally, a {@link WorldMapInconsistentException} will be thrown if
     * the format is valid but the described tiles and exits result in a
     * geometrically impossible layout.
     *
     * @param filename the file path to load from.
     * @throws WorldMapFormatException if the file is incorrectly formatted.
     * @throws WorldMapInconsistentException if the file is correctly
     *          formatted, but has geometric inconsistencies.
     * @throws FileNotFoundException if the file does not exist.
     * @require filename != null
     * @ensure the loaded map is geometrically consistent.
     */
    public WorldMap(String filename) throws WorldMapFormatException,
                                            WorldMapInconsistentException,
                                            FileNotFoundException {
        try (FileReader file = new FileReader(filename)) {
            BufferedReader reader = new BufferedReader(file);
            // Offload to helper function so we don't have a massive
            // constructor.
            loadWorldMap(reader);
        } catch (FileNotFoundException e) {
            // Because FileNotFoundExc is a subclass of IOExc, we need to
            // manually propagate it here.
            throw e;
        } catch (IOException e) {
            // loadWorldMap catches IOException itself, but opening and reading
            // can throw this too...
            throw new WorldMapFormatException();
        }
    }

    /**
     * Parses the integer and replaces NumberFormatException with WMFE.
     * @param numberString string containing an integer.
     * @return integer.
     * @throws WorldMapFormatException if string is null or an invalid integer.
     */
    private static int safeParseInt(String numberString)
            throws WorldMapFormatException {
        try {
            return Integer.parseInt(numberString);
        } catch (NumberFormatException e) {
            throw new WorldMapFormatException();
        }
    }

    /**
     * Wrapper around readLine() saving us from NullPointerExceptions.
     * @param reader Reader.
     * @return Next line, <i>never null</i>.
     * @throws WorldMapFormatException If at EOF or IOException.
     */
    private static String safeReadLine(BufferedReader reader)
            throws WorldMapFormatException {
        String line;
        try {
            line = reader.readLine();
        } catch (IOException e) {
            throw new WorldMapFormatException();
        }
        if (line == null) {
            throw new WorldMapFormatException();
        } else {
            return line;
        }
    }

    /**
     * Loads data from the given buffered reader into the world map instance.
     * @param reader Reader to load from.
     * @throws WorldMapFormatException If the file format is wrong.
     * @throws WorldMapInconsistentException If the tile and exits are
     * geometrically inconsistent.
     */
    private void loadWorldMap(BufferedReader reader)
            throws WorldMapFormatException, WorldMapInconsistentException {
        // Parse the builder section, containing starting position and
        // builder information.
        Pair<Position, Builder> builderPair = parseBuilderSection(reader);

        // Sets the instance variables.
        builder = builderPair.right;
        startPosition = builderPair.left;

        Tile startingTile = builder.getCurrentTile();

        parseEmptyLine(reader);

        // Parses the tiles section.
        List<Tile> tiles = parseTilesSection(reader, startingTile);

        parseEmptyLine(reader);

        // Loads and adds the exits to the given tiles.
        parseExitsSection(reader, tiles);

        ensureAtEnd(reader);

        // At this point, the exits should be set correctly; add and cross
        // fingers.
        sparseArray.addLinkedTiles(startingTile,
                startPosition.getX(), startPosition.getY());
    }

    /**
     * Ensures the reader is at the end of the file, otherwise throws EOF.
     * @param reader Reader.
     * @throws WorldMapFormatException Reader is not at EOF.
     */
    private static void ensureAtEnd(BufferedReader reader)
            throws WorldMapFormatException {
        String lastLine;
        try {
            lastLine = reader.readLine();
        } catch (IOException e) {
            throw new WorldMapFormatException();
        }
        if (lastLine != null) {
            throw new WorldMapFormatException();
        }
    }

    /**
     * Parses exactly one line from the reader and ensures it is empty.
     * @param reader Reader object.
     * @throws WorldMapFormatException If the line was non-empty, an EOF or
     * IOException occurred.
     */
    private static void parseEmptyLine(BufferedReader reader)
            throws WorldMapFormatException {
        if (!safeReadLine(reader).equals("")) {
            throw new WorldMapFormatException();
        }
    }

    /**
     * Parses the first section, the builder's properties and starting
     * position.
     *
     * IMPORTANT: An empty new tile is instantiated here for the builder's
     * starting tile.
     * @param reader Reader.
     * @return Pair of starting position and builder.
     * @throws WorldMapFormatException Invalid formatted section.
     */
    private static Pair<Position, Builder> parseBuilderSection(BufferedReader reader)
            throws WorldMapFormatException {
        // Read the starting position, first 2 lines. The safe* methods will
        // throw for us.
        int startX = safeParseInt(safeReadLine(reader));
        int startY = safeParseInt(safeReadLine(reader));
        // Shadows instance field with same name.
        Position startPosition = new Position(startX, startY);

        // The next 2 lines are the builder's name and inventory.
        String builderName = safeReadLine(reader);
        String[] inventoryStrings = safeReadLine(reader).split(",");

        // Convert the block strings to class instances.
        List<Block> builderInventory;
        if (inventoryStrings.length == 1 && inventoryStrings[0].equals("")) {
            // makeBlockList will throw with a single empty string type, but
            // we know that indicates an empty inventory.
            builderInventory = new ArrayList<>();
        } else {
            builderInventory = BlockTypes.makeBlockList(inventoryStrings);
        }

        // Shadows instance's builder field.
        Builder builder;
        try {
            builder = new Builder(builderName,
                    new Tile(new ArrayList<>()), builderInventory);
        } catch (TooHighException e) {
            throw new AssertionError("No blocks but too high was thrown.", e);
        } catch (InvalidBlockException e) {
            // An inventory block is not carryable.
            throw new WorldMapFormatException();
        }
        return new Pair<>(startPosition, builder);
   }

    /**
     * Parses a string of the format "label1:N,label2:M,label3:P" into a
     * map.
     *
     * <p>Format (if the string does not match, an exception will be thrown):
     * <ul>
     *     <li>Comma-delimited fields (no spaces)</li>
     *     <li>Lowercase field names</li>
     *     <li>No repeated field names</li>
     *     <li>Non-negative integers</li>
     * </ul>
     * </p>
     *
     * @param string String to parse.
     * @param exactlyOneField If true, an exception will be thrown unless there
     * is exactly one field.
     * @return Map of label names to their number.
     * @throws WorldMapFormatException If exactlyOneField==true and there is
     * not exactly one field, or the format is invalid (see above).
     */
    private static Map<String, Integer> parseColonStrings(
            String string, boolean exactlyOneField)
            throws WorldMapFormatException {
        Pattern fieldRegex = Pattern.compile("^([a-z]+):(\\d+)$");

        String[] fields = string.split(",");
        if (exactlyOneField && fields.length != 1) {
            throw new WorldMapFormatException();
        }

        Map<String, Integer> outputMap = new HashMap<>();
        if (string.equals("")) {
            // If the string is empty, return an empty map.
            return outputMap;
        }

        for (String field : fields) {
            Matcher matcher = fieldRegex.matcher(field);
            // Throw on repeated names too.
            if (matcher.matches() && !outputMap.containsKey(matcher.group(1))) {
                // If it matches, we know the integer will be valid.
                outputMap.put(matcher.group(1),
                        safeParseInt(matcher.group(2)));
            } else {
                throw new WorldMapFormatException();
            }
        }

        return outputMap;
    }

    /**
     * Parses a line of the form "N [rest]", ensuring N is an integer and
     * [rest] contains no spaces. If [rest] is empty, the preceding space can
     * be omitted.
     *
     * @param line String to parse.
     * @return Pair of the integer and the rest of the string.
     */
    private static Pair<Integer, String> parseNumberedRow(String line)
            throws WorldMapFormatException {
        String[] split = line.split(" ");
        // Throw if there is more than one space in the string.
        if (split.length > 2) {
            throw new WorldMapFormatException();
        }
        // Numeric part.
        int num = safeParseInt(split[0]);

        // If there is no string part, use an empty string to avoid nulls.
        String rest;
        if (split.length < 2) {
            rest = "";
        } else {
            rest = split[1];
        }

        return new Pair<>(num, rest);
    }

    /**
     * Parses the tile section of the given reader. startingTile must be given
     * and must have no blocks. It is assumed that this tile is the builder's
     * starting tile.
     * @param reader Reader.
     * @param startingTile The starting tile with no blocks on it.
     * @return List of tiles, list index corresponds to the ID as from the file.
     * @throws WorldMapFormatException Invalid format.
     */
    private static List<Tile> parseTilesSection(BufferedReader reader,
                                                Tile startingTile)
            throws WorldMapFormatException {
        String totalLine = safeReadLine(reader);
        Map<String, Integer> totalLineMap = parseColonStrings(totalLine, true);
        if (!totalLineMap.containsKey("total")) {
            // Either it has no or the wrong string label, throw.
            throw new WorldMapFormatException();
        }

        int numLines = totalLineMap.get("total");
        if (numLines < 1) {
            // Require at least one tile. Any less will wreak havoc on the for
            // loop.
            throw new WorldMapFormatException();
        }

        // Mapping of tile ID to that tile's blocks.
        // We need a mapping because we cannot guarantee the ordering of tiles
        // is 0 to numTiles-1 in the file.
        Map<Integer, List<Block>> blocksForTiles = new HashMap<>();

        // Parse exactly the next 'numLines' rows.
        for (int i = 0; i < numLines; i++) {
            Pair<Integer, String> linePair =
                    parseNumberedRow(safeReadLine(reader));
            int num = linePair.left;
            String blocksString = linePair.right;

            // If num is out of range or already inserted, throw.
            if (num < 0 || num >= numLines || blocksForTiles.containsKey(num)) {
                throw new WorldMapFormatException();
            }

            // Add the list of block instances to the mapping.
            // Special case the empty string indicating no blocks.
            if (blocksString.equals("")) {
                blocksForTiles.put(num, new ArrayList<>());
            } else {
                blocksForTiles.put(num,
                        BlockTypes.makeBlockList(blocksString.split(",")));
            }
        }
        // Because the for loop iterates exactly 'numLines' times, if we
        // reach this point, we can be sure all tiles have one line each.

        // Mapping of tile IDs to the actual tile objects.
        List<Tile> tiles = new ArrayList<>();
        tiles.add(startingTile);

        // For the first tile, we already have a tile object; add
        // the blocks.
        for (Block block : blocksForTiles.get(0)) {
            try {
                startingTile.placeBlock(block);
            } catch (InvalidBlockException e) {
                throw new AssertionError("Inserting null block.", e);
            } catch (TooHighException e) {
                // Ground block too high.
                throw new WorldMapFormatException();
            }
        }

        // Skip first tile; already handled above. Add the other tiles' blocks,
        // correcting the order.
        for (int i = 1; i < numLines; i++) {
            Tile newTile;
            try {
                // Initialise with the correct blocks.
                newTile = new Tile(blocksForTiles.get(i));
            } catch (TooHighException e) {
                throw new WorldMapFormatException();
            }
            tiles.add(newTile);
        }
        return tiles;
    }

    /**
     * Parses the exit section adds them to the tiles given as a parameter.
     *
     * First line must be "exits". The next N lines (where N is the number of
     * tiles) must be "n [direction]:[tileId],..." where n and tileId are valid
     * tile IDs (0 &le; n &lt; N) and direction is a compass direction.
     *
     * The exit lines need not be ordered. One direction cannot be specified
     * more than once. Every tile must have exactly one line, even if there
     * are no exits.
     * @param reader Reader.
     * @param tiles List of tile objects. Exits will be added to these.
     * @throws WorldMapFormatException Invalid format (see above).
     */
    private static void parseExitsSection(BufferedReader reader,
                                          List<Tile> tiles)
            throws WorldMapFormatException {
        // I'd use a simple String[], but there's no .contains() for arrays...
        // On the plus side, O(1) .contains()!
        Set<String> exitDirections = new HashSet<>();
        exitDirections.add("north");
        exitDirections.add("east");
        exitDirections.add("south");
        exitDirections.add("west");

        // First line of this section must be exactly "exits".
        if (!safeReadLine(reader).equals("exits")) {
            throw new WorldMapFormatException();
        }

        // A set of tile IDs we've seen, to ensure no tile has more than on
        // exit line.
        Set<Integer> seenTiles = new HashSet<>();

        // The exits should contain exactly one line per tile, however not
        // necessarily in any order.
        for (int i = 0; i < tiles.size(); i++) {
            Pair<Integer, String> pair = parseNumberedRow(safeReadLine(reader));

            // ID of the current parent tile.
            int currentTile = pair.left;

            // .add() returns false if the key already existed in the set.
            if (!seenTiles.add(currentTile)) {
                // Exits for this tile have already been defined.
                throw new WorldMapFormatException();
            }

            // Parses the "north:2,east:1,..." part of the string into a map.
            // parseColonStrings enforces uniqueness of direction names.
            Map<String, Integer> exits = parseColonStrings(pair.right, false);
            for (Map.Entry<String, Integer> exit : exits.entrySet()) {
                int tileID = exit.getValue();
                if (tileID < 0 || tileID >= tiles.size()
                        || !exitDirections.contains(exit.getKey())) {
                    // The tile ID referred to does not exist or the exit
                    // is invalid.
                    throw new WorldMapFormatException();
                }

                try {
                    tiles.get(currentTile).addExit(
                            exit.getKey(), tiles.get(tileID));
                } catch (NoExitException e) {
                    throw new AssertionError("Null direction or tile.", e);
                }
            }
        }
    }

    /**
     * Gets the builder associated with this block world.
     * @return the builder object.
     */
    public Builder getBuilder() {
        return builder;
    }

    /**
     * Gets the starting position.
     * @return the starting position.
     */
    public Position getStartPosition() {
        return startPosition;
    }

    /**
     * Get a tile by its position.
     * @param position get the Tile at this position.
     * @return the tile at that position.
     * @require position != null
     */
    public Tile getTile(Position position) {
        return sparseArray.getTile(position);
    }

    /**
     * Gets a list of tiles in a breadth-first-search order from the starting
     * tile.
     * @return a list of ordered tiles.
     */
    public List<Tile> getTiles() {
        return sparseArray.getTiles();
    }

    /**
     * Saves the given WorldMap to a file specified by the filename. 
     * See the WorldMap(filename) constructor for the format of the map. 
     * The Tile IDs need to relate to the ordering of tiles returned by getTiles()
     * i.e. tile 0 is getTiles().get(0) 
     * The function should do the following:
     * <ol>
     * <li> Open the filename and write a map in the format
     *     given in the WorldMap constructor. </li>
     * <li> Write the current builder's (given by getBuilder()) name
     *     and inventory.</li>
     * <li> Write the starting position (given by getStartPosition())
     *     </li>
     * <li> Write the number of tiles </li>
     * <li> Write the index, and then each tile as given by
     *     getTiles() (in the same order). </li>
     * <li> Write each tiles exits, as given by
     *     getTiles().get(id).getExits() </li>
     * <li> Throw an IOException if the file cannot be opened for
     *     writing, or if writing fails. </li>
     * </ol>
     * 
     * Hint: call getTiles()
     * @param filename the filename to be written to
     * @throws java.io.IOException if the file cannot be opened or written to.
     * @require filename != null
     */
    public void saveMap(String filename)
                 throws java.io.IOException {

    }

}