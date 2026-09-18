package hack.model;

/**
 * MonsterPrototype.java – Immutable species definition for a monster type.
 *
 * Ports {@code struct permonst} from {@code def.permonst.h}:
 * <pre>
 *   struct permonst {
 *       char *mname;   // species name
 *       char  mlet;    // display letter
 *       schar mlevel;  // difficulty level
 *       schar mmove;   // movement rate
 *       schar ac;      // armour class (lower = harder to hit)
 *       schar damn;    // attack dice count
 *       schar damd;    // attack die faces
 *       unsigned pxlth; // size of per-instance extra data (eshk, edog…)
 *   };
 * </pre>
 *
 * The full 57-entry monster table (from {@code hack.monst.c}) plus the
 * three special monsters (ghost, eel, wizard) are defined as static
 * constants at the bottom of this class.
 *
 * @see Monster
 */
public final class MonsterPrototype {

    // -----------------------------------------------------------------------
    // Fields (all final — species data never changes)
    // -----------------------------------------------------------------------

    /** Display name, e.g. "giant ant" */
    public final String name;
    /** ASCII display letter used on the map */
    public final char   letter;
    /** Difficulty level (mlevel) — used in experience and to-hit calculations */
    public final int    level;
    /** Movement rate — compared against rnd(6) each turn */
    public final int    moveRate;
    /** Armour class — negative = very hard to hit */
    public final int    ac;
    /** Number of attack dice (damn) */
    public final int    attackDice;
    /** Faces per attack die (damd) */
    public final int    attackDie;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Creates a fully-defined monster prototype.
     *
     * @param name       species name
     * @param letter     display character
     * @param level      monster level
     * @param moveRate   movement speed
     * @param ac         armour class
     * @param attackDice number of attack dice
     * @param attackDie  die faces per attack
     */
    public MonsterPrototype(String name, char letter, int level,
                            int moveRate, int ac, int attackDice, int attackDie) {
        this.name       = name;
        this.letter     = letter;
        this.level      = level;
        this.moveRate   = moveRate;
        this.ac         = ac;
        this.attackDice = attackDice;
        this.attackDie  = attackDie;
    }

    @Override
    public String toString() {
        return name + " (" + letter + ")";
    }

    // -----------------------------------------------------------------------
    // Monster table  (mirrors hack.monst.c exactly, same order)
    // -----------------------------------------------------------------------
    // Index comments refer to the CORPSE_I_TO_C mapping used in eat.c.

    /** bat */                public static final MonsterPrototype BAT           = new MonsterPrototype("bat",              'B',  1, 22,  8,  1,  4);
    /** gnome */              public static final MonsterPrototype GNOME         = new MonsterPrototype("gnome",            'G',  1,  6,  5,  1,  6);
    /** hobgoblin */          public static final MonsterPrototype HOBGOBLIN     = new MonsterPrototype("hobgoblin",        'H',  1,  9,  5,  1,  8);
    /** jackal */             public static final MonsterPrototype JACKAL        = new MonsterPrototype("jackal",           'J',  0, 12,  7,  1,  2);
    /** kobold */             public static final MonsterPrototype KOBOLD        = new MonsterPrototype("kobold",           'K',  1,  6,  7,  1,  4);
    /** leprechaun */         public static final MonsterPrototype LEPRECHAUN    = new MonsterPrototype("leprechaun",       'L',  5, 15,  8,  1,  2);
    /** giant rat */          public static final MonsterPrototype GIANT_RAT     = new MonsterPrototype("giant rat",        'r',  0, 12,  7,  1,  3);
    /** acid blob */          public static final MonsterPrototype ACID_BLOB     = new MonsterPrototype("acid blob",        'a',  2,  3,  8,  0,  0);
    /** floating eye */       public static final MonsterPrototype FLOATING_EYE  = new MonsterPrototype("floating eye",     'E',  2,  1,  9,  0,  0);
    /** homunculus */         public static final MonsterPrototype HOMUNCULUS    = new MonsterPrototype("homunculus",       'h',  2,  6,  6,  1,  3);
    /** imp */                public static final MonsterPrototype IMP           = new MonsterPrototype("imp",              'i',  2,  6,  2,  1,  4);
    /** orc */                public static final MonsterPrototype ORC           = new MonsterPrototype("orc",              'O',  2,  9,  6,  1,  8);
    /** yellow light */       public static final MonsterPrototype YELLOW_LIGHT  = new MonsterPrototype("yellow light",     'y',  3, 15,  0,  0,  0);
    /** zombie */             public static final MonsterPrototype ZOMBIE        = new MonsterPrototype("zombie",           'Z',  2,  6,  8,  1,  8);
    /** giant ant */          public static final MonsterPrototype GIANT_ANT     = new MonsterPrototype("giant ant",        'A',  3, 18,  3,  1,  6);
    /** fog cloud */          public static final MonsterPrototype FOG_CLOUD     = new MonsterPrototype("fog cloud",        'f',  3,  1,  0,  1,  6);
    /** nymph */              public static final MonsterPrototype NYMPH         = new MonsterPrototype("nymph",            'N',  6, 12,  9,  1,  2);
    /** piercer */            public static final MonsterPrototype PIERCER       = new MonsterPrototype("piercer",          'p',  3,  1,  3,  2,  6);
    /** quasit */             public static final MonsterPrototype QUASIT        = new MonsterPrototype("quasit",           'Q',  3, 15,  3,  1,  4);
    /** quivering blob */     public static final MonsterPrototype QUIVERING_BLOB= new MonsterPrototype("quivering blob",   'q',  3,  1,  8,  1,  8);
    /** violet fungi */       public static final MonsterPrototype VIOLET_FUNGI  = new MonsterPrototype("violet fungi",     'v',  3,  1,  7,  1,  4);
    /** giant beetle */       public static final MonsterPrototype GIANT_BEETLE  = new MonsterPrototype("giant beetle",     'b',  4,  6,  4,  3,  4);
    /** centaur */            public static final MonsterPrototype CENTAUR       = new MonsterPrototype("centaur",          'C',  4, 18,  4,  1,  6);
    /** cockatrice */         public static final MonsterPrototype COCKATRICE    = new MonsterPrototype("cockatrice",       'c',  4,  6,  6,  1,  3);
    /** gelatinous cube */    public static final MonsterPrototype GEL_CUBE      = new MonsterPrototype("gelatinous cube",  'g',  4,  6,  8,  2,  4);
    /** jaguar */             public static final MonsterPrototype JAGUAR        = new MonsterPrototype("jaguar",           'j',  4, 15,  6,  1,  8);
    /** killer bee */         public static final MonsterPrototype KILLER_BEE    = new MonsterPrototype("killer bee",       'k',  4, 14,  4,  2,  4);
    /** snake */              public static final MonsterPrototype SNAKE         = new MonsterPrototype("snake",            'S',  4, 15,  3,  1,  6);
    /** freezing sphere */    public static final MonsterPrototype FREEZING_SPHERE=new MonsterPrototype("freezing sphere",  'F',  2, 13,  4,  0,  0);
    /** owlbear */            public static final MonsterPrototype OWLBEAR       = new MonsterPrototype("owlbear",          'o',  5, 12,  5,  2,  6);
    /** rust monster */       public static final MonsterPrototype RUST_MONSTER  = new MonsterPrototype("rust monster",     'R', 10, 18,  3,  0,  0);
    /** scorpion */           public static final MonsterPrototype SCORPION      = new MonsterPrototype("scorpion",         's',  5, 15,  3,  1,  4);
    /** tengu */              public static final MonsterPrototype TENGU         = new MonsterPrototype("tengu",            't',  5, 13,  5,  1,  7);
    /** wraith */             public static final MonsterPrototype WRAITH        = new MonsterPrototype("wraith",           'W',  5, 12,  5,  1,  6);
    /** long worm */          public static final MonsterPrototype LONG_WORM     = new MonsterPrototype("long worm",        'w',  8,  3,  5,  1,  4);
    /** large dog */          public static final MonsterPrototype LARGE_DOG     = new MonsterPrototype("large dog",        'd',  6, 15,  4,  2,  4);
    /** leocrotta */          public static final MonsterPrototype LEOCROTTA     = new MonsterPrototype("leocrotta",        'l',  6, 18,  4,  3,  6);
    /** mimic */              public static final MonsterPrototype MIMIC         = new MonsterPrototype("mimic",            'M',  7,  3,  7,  3,  4);
    /** troll */              public static final MonsterPrototype TROLL         = new MonsterPrototype("troll",            'T',  7, 12,  4,  2,  7);
    /** unicorn */            public static final MonsterPrototype UNICORN       = new MonsterPrototype("unicorn",          'u',  8, 24,  5,  1, 10);
    /** yeti */               public static final MonsterPrototype YETI          = new MonsterPrototype("yeti",             'Y',  5, 15,  6,  1,  6);
    /** stalker */            public static final MonsterPrototype STALKER       = new MonsterPrototype("stalker",          'I',  8, 12,  3,  4,  4);
    /** umber hulk */         public static final MonsterPrototype UMBER_HULK    = new MonsterPrototype("umber hulk",       'U',  9,  6,  2,  2, 10);
    /** vampire */            public static final MonsterPrototype VAMPIRE       = new MonsterPrototype("vampire",          'V',  8, 12,  1,  1,  6);
    /** xorn */               public static final MonsterPrototype XORN          = new MonsterPrototype("xorn",             'X',  8,  9, -2,  4,  6);
    /** xan */                public static final MonsterPrototype XAN           = new MonsterPrototype("xan",              'x',  7, 18, -2,  2,  4);
    /** zruty */              public static final MonsterPrototype ZRUTY         = new MonsterPrototype("zruty",            'z',  9,  8,  3,  3,  6);
    /** chameleon */          public static final MonsterPrototype CHAMELEON     = new MonsterPrototype("chameleon",        ':',  6,  5,  6,  4,  2);
    /** dragon */             public static final MonsterPrototype DRAGON        = new MonsterPrototype("dragon",           'D', 10,  9, -1,  3,  8);
    /** ettin */              public static final MonsterPrototype ETTIN         = new MonsterPrototype("ettin",            'e', 10, 12,  3,  2,  8);
    /** lurker above */       public static final MonsterPrototype LURKER_ABOVE  = new MonsterPrototype("lurker above",     '\'',10,  3,  3,  0,  0);
    /** nurse */              public static final MonsterPrototype NURSE         = new MonsterPrototype("nurse",            'n', 11,  6,  0,  1,  3);
    /** trapper */            public static final MonsterPrototype TRAPPER       = new MonsterPrototype("trapper",          ',', 12,  3,  3,  0,  0);
    /** purple worm */        public static final MonsterPrototype PURPLE_WORM   = new MonsterPrototype("purple worm",      'P', 15,  9,  6,  2,  8);
    /** demon */              public static final MonsterPrototype DEMON         = new MonsterPrototype("demon",            '&', 10, 12, -4,  1,  4);
    /** minotaur */           public static final MonsterPrototype MINOTAUR      = new MonsterPrototype("minotaur",         'm', 15, 15,  6,  4, 10);
    /** shopkeeper (special)*/public static final MonsterPrototype SHOPKEEPER    = new MonsterPrototype("shopkeeper",       '@', 12, 18,  0,  4,  8);
    /** vault guard (special)*/public static final MonsterPrototype VAULTGUARD    = new MonsterPrototype("bank manager",       '@', 12, 18,  0,  4,  8);
    /** little dog (companion)*/public static final MonsterPrototype LITTLE_DOG   = new MonsterPrototype("little dog",       'd',  2, 10,  5,  1,  3);
    // Special monsters (not in the main array in the original C)
    /** ghost (pm_ghost) */   public static final MonsterPrototype GHOST         = new MonsterPrototype("ghost",            ' ', 10,  3, -5,  1,  1);
    /** giant eel (pm_eel) */ public static final MonsterPrototype EEL           = new MonsterPrototype("giant eel",        ';', 15,  6, -3,  3,  6);
    /** wizard of Yendor */   public static final MonsterPrototype WIZARD        = new MonsterPrototype("wizard of Yendor", '1', 15, 12, -2,  1, 12);

    /**
     * Ordered list of all 56 common monsters (CMNUM = 55 in original C,
     * which means indices 0..55, 56 entries total).
     * Mirrors the {@code mons[CMNUM+2]} array in {@code hack.monst.c}.
     */
    public static final MonsterPrototype[] ALL = {
        BAT, GNOME, HOBGOBLIN, JACKAL, KOBOLD, LEPRECHAUN,
        GIANT_RAT, ACID_BLOB, FLOATING_EYE, HOMUNCULUS, IMP, ORC,
        YELLOW_LIGHT, ZOMBIE, GIANT_ANT, FOG_CLOUD, NYMPH, PIERCER,
        QUASIT, QUIVERING_BLOB, VIOLET_FUNGI, GIANT_BEETLE, CENTAUR,
        COCKATRICE, GEL_CUBE, JAGUAR, KILLER_BEE, SNAKE, FREEZING_SPHERE,
        OWLBEAR, RUST_MONSTER, SCORPION, TENGU, WRAITH, LONG_WORM,
        LARGE_DOG, LEOCROTTA, MIMIC, TROLL, UNICORN, YETI,
        STALKER, UMBER_HULK, VAMPIRE, XORN, XAN, ZRUTY, CHAMELEON,
        DRAGON, ETTIN, LURKER_ABOVE, NURSE, TRAPPER, PURPLE_WORM,
        DEMON, MINOTAUR, SHOPKEEPER, VAULTGUARD, LITTLE_DOG
    };

    /** Number of common monsters (CMNUM in original C). */
    public static final int CMNUM = 55;

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Monsters that regenerate HP each turn. Mirrors {@code MREGEN} string. */
    public boolean regenerates() {
        return "TVi1".indexOf(letter) >= 0;
    }

    /** Undead monsters that do extra damage at midnight. Mirrors {@code UNDEAD}. */
    public boolean isUndead() {
        return "ZVW ".indexOf(letter) >= 0;
    }

    /**
     * Returns the prototype whose {@link #letter} matches, or null.
     * Linear search is acceptable because this is infrequently called.
     *
     * @param letter display letter to search for
     * @return matching prototype, or null
     */
    public static MonsterPrototype byLetter(char letter) {
        for (MonsterPrototype p : ALL) {
            if (p.letter == letter) return p;
        }
        if (GHOST.letter  == letter) return GHOST;
        if (EEL.letter    == letter) return EEL;
        if (WIZARD.letter == letter) return WIZARD;
        return null;
    }
}
