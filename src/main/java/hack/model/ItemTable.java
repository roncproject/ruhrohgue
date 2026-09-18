package hack.model;

/**
 * ItemTable.java – The complete 215-entry object prototype table.
 *
 * Ports {@code objects[]} from {@code def.objects.h} and the generated
 * {@code hack.onames.h}.  Each entry is an {@link ItemClass}.
 *
 * <p>Named index constants match the {@code #define} names in
 * {@code hack.onames.h} so existing logic is easy to translate.
 *
 * @see ItemClass
 * @see Item
 */
public final class ItemTable {

    private ItemTable() {}

    // -----------------------------------------------------------------------
    // Symbol constants  (match def.objclass.h)
    // -----------------------------------------------------------------------

    public static final char ILLOBJ_SYM  = '\\';
    public static final char AMULET_SYM  = '"';
    public static final char FOOD_SYM    = '%';
    public static final char WEAPON_SYM  = ')';
    public static final char TOOL_SYM    = '(';
    public static final char BALL_SYM    = '0';
    public static final char CHAIN_SYM   = '_';
    public static final char ROCK_SYM    = '`';
    public static final char ARMOR_SYM   = '[';
    public static final char POTION_SYM  = '!';
    public static final char SCROLL_SYM  = '?';
    public static final char WAND_SYM    = '/';
    public static final char RING_SYM    = '=';
    public static final char GEM_SYM     = '*';
    public static final char GOLD_SYM    = '$';

    // -----------------------------------------------------------------------
    // Named index constants  (from hack.onames.h)
    // -----------------------------------------------------------------------

    public static final int STRANGE_OBJECT        = 0;
    public static final int AMULET_OF_YENDOR      = 1;
    public static final int FOOD_RATION            = 2;
    public static final int TRIPE_RATION           = 3;
    public static final int PANCAKE                = 4;
    public static final int DEAD_LIZARD            = 5;
    public static final int FORTUNE_COOKIE         = 6;
    public static final int CARROT                 = 7;
    public static final int TIN                    = 8;
    public static final int ORANGE                 = 9;
    public static final int APPLE                  = 10;
    public static final int PEAR                   = 11;
    public static final int MELON                  = 12;
    public static final int BANANA                 = 13;
    public static final int CANDY_BAR              = 14;
    public static final int EGG                    = 15;
    public static final int CLOVE_OF_GARLIC        = 16;
    public static final int LUMP_OF_ROYAL_JELLY   = 17;
    public static final int DEAD_HUMAN             = 18;
    /** Highest ItemTable index that is a corpse ('%') entry. */
    public static final int LAST_CORPSE            = 70;
    // Corpse indices 19–70 follow the same pattern; abbreviated here
    public static final int DEAD_ACID_BLOB         = 45;
    // Rotterdam street-food additions
    public static final int BARA           = 212;
    public static final int BLOOD_SAUSAGE  = 213;
    public static final int LOEMPIA        = 214;
    public static final int TELO           = 215;
    public static final int BAKA_BANA      = 216;
    public static final int KRUPUK         = 217;
    public static final int LAHMACUN       = 218;
    public static final int KAPSALON       = 219;
    public static final int SAUSAGE_ROLL   = 220;
    public static final int SAMOSA         = 221;

    public static final int ARROW                  = 71;
    public static final int SLING_BULLET           = 72;
    public static final int CROSSBOW_BOLT          = 73;
    public static final int DART                   = 74;
    public static final int ROCK                   = 75;
    public static final int BOOMERANG              = 76;
    public static final int MACE                   = 77;
    public static final int AXE                    = 78;
    public static final int FLAIL                  = 79;
    public static final int LONG_SWORD             = 80;
    public static final int TWO_HANDED_SWORD       = 81;
    public static final int DAGGER                 = 82;
    public static final int WORM_TOOTH             = 83;
    public static final int CRYSKNIFE              = 84;
    public static final int SPEAR                  = 85;
    public static final int BOW                    = 86;
    public static final int SLING                  = 87;
    public static final int CROSSBOW               = 88;
    public static final int WHISTLE                = 89;
    public static final int MAGIC_WHISTLE          = 90;
    public static final int EXPENSIVE_CAMERA       = 91;
    public static final int ICE_BOX                = 92;
    public static final int PICK_AXE               = 93;
    public static final int CAN_OPENER             = 94;
    public static final int HEAVY_IRON_BALL        = 95;
    public static final int IRON_CHAIN             = 96;
    public static final int ENORMOUS_ROCK          = 97;
    public static final int HELMET                 = 98;
    public static final int PLATE_MAIL             = 99;
    public static final int SPLINT_MAIL            = 100;
    public static final int BANDED_MAIL            = 101;
    public static final int CHAIN_MAIL             = 102;
    public static final int SCALE_MAIL             = 103;
    public static final int RING_MAIL              = 104;
    public static final int STUDDED_LEATHER_ARMOR  = 105;
    public static final int LEATHER_ARMOR          = 106;
    public static final int ELVEN_CLOAK            = 107;
    public static final int SHIELD                 = 108;
    public static final int PAIR_OF_GLOVES         = 109;
    public static final int POT_RESTORE_STRENGTH   = 110;
    public static final int POT_BOOZE              = 111;
    public static final int POT_INVISIBILITY       = 112;
    public static final int POT_FRUIT_JUICE        = 113;
    public static final int POT_HEALING            = 114;
    public static final int POT_PARALYSIS          = 115;
    public static final int POT_MONSTER_DETECTION  = 116;
    public static final int POT_OBJECT_DETECTION   = 117;
    public static final int POT_SICKNESS           = 118;
    public static final int POT_CONFUSION          = 119;
    public static final int POT_GAIN_STRENGTH      = 120;
    public static final int POT_SPEED              = 121;
    public static final int POT_BLINDNESS          = 122;
    public static final int POT_GAIN_LEVEL         = 123;
    public static final int POT_EXTRA_HEALING      = 124;
    public static final int POT_LEVITATION         = 125;
    public static final int SCR_MAIL               = 130;
    public static final int SCR_ENCHANT_ARMOR      = 131;
    public static final int SCR_DESTROY_ARMOR      = 132;
    public static final int SCR_CONFUSE_MONSTER    = 133;
    public static final int SCR_SCARE_MONSTER      = 134;
    public static final int SCR_BLANK_PAPER        = 135;
    public static final int SCR_REMOVE_CURSE       = 136;
    public static final int SCR_ENCHANT_WEAPON     = 137;
    public static final int SCR_DAMAGE_WEAPON      = 138;
    public static final int SCR_CREATE_MONSTER     = 139;
    public static final int SCR_TAMING             = 140;
    public static final int SCR_GENOCIDE           = 141;
    public static final int SCR_LIGHT              = 142;
    public static final int SCR_TELEPORTATION      = 143;
    public static final int SCR_GOLD_DETECTION     = 144;
    public static final int SCR_FOOD_DETECTION     = 145;
    public static final int SCR_IDENTIFY           = 146;
    public static final int SCR_MAGIC_MAPPING      = 147;
    public static final int SCR_AMNESIA            = 148;
    public static final int SCR_FIRE               = 149;
    public static final int SCR_PUNISHMENT         = 150;
    public static final int WAN_LIGHT              = 155;
    public static final int WAN_SECRET_DOOR        = 156;
    public static final int WAN_CREATE_MONSTER     = 157;
    public static final int WAN_WISHING            = 158;
    public static final int WAN_STRIKING           = 159;
    public static final int WAN_SLOW_MONSTER       = 160;
    public static final int WAN_SPEED_MONSTER      = 161;
    public static final int WAN_UNDEAD_TURNING     = 162;
    public static final int WAN_POLYMORPH          = 163;
    public static final int WAN_CANCELLATION       = 164;
    public static final int WAN_TELEPORTATION      = 165;
    public static final int WAN_MAKE_INVISIBLE     = 166;
    public static final int WAN_DIGGING            = 167;
    public static final int WAN_MAGIC_MISSILE      = 168;
    public static final int WAN_FIRE               = 169;
    public static final int WAN_SLEEP              = 170;
    public static final int WAN_COLD               = 171;
    public static final int WAN_DEATH              = 172;
    public static final int RIN_ADORNMENT          = 176;
    public static final int RIN_TELEPORTATION      = 177;
    public static final int RIN_REGENERATION       = 178;
    public static final int RIN_SEARCHING          = 179;
    public static final int RIN_SEE_INVISIBLE      = 180;
    public static final int RIN_STEALTH            = 181;
    public static final int RIN_LEVITATION         = 182;
    public static final int RIN_POISON_RESISTANCE  = 183;
    public static final int RIN_AGGRAVATE_MONSTER  = 184;
    public static final int RIN_HUNGER             = 185;
    public static final int RIN_FIRE_RESISTANCE    = 186;
    public static final int RIN_COLD_RESISTANCE    = 187;
    public static final int RIN_PROT_FROM_SC       = 188;
    public static final int RIN_CONFLICT           = 189;
    public static final int RIN_GAIN_STRENGTH      = 190;
    public static final int RIN_INCREASE_DAMAGE    = 191;
    public static final int RIN_PROTECTION         = 192;
    public static final int RIN_WARNING            = 193;
    public static final int RIN_TELEPORT_CONTROL   = 194;
    public static final int DIAMOND                = 197;
    public static final int RUBY                   = 198;
    public static final int SAPPHIRE               = 199;
    public static final int EMERALD                = 200;
    public static final int TURQUOISE              = 201;
    public static final int AQUAMARINE             = 202;
    public static final int TOURMALINE             = 203;
    public static final int TOPAZ                  = 204;
    public static final int OPAL                   = 205;
    public static final int GARNET                 = 206;
    public static final int AMETHYST              = 207;
    public static final int AGATE                  = 208;
    public static final int ONYX                   = 209;
    public static final int JASPER                 = 210;
    public static final int JADE                   = 211;

    // ── Additional Dutch/Rotterdam/Indonesian street foods ──────────────
    public static final int HARING          = 222;
    public static final int KIBBELING       = 223;
    public static final int BITTERBAL       = 224;
    public static final int KROKET          = 225;
    public static final int PATAT           = 226;
    public static final int FRIKANDEL       = 227;
    public static final int STROOPWAFEL     = 228;
    public static final int BAPAO           = 229;
    public static final int PISANG_GORENG   = 230;
    public static final int LEMPER          = 231;
    public static final int PASTEI          = 232;
    public static final int RISOLES         = 233;
    public static final int MARTABAK        = 234;
    public static final int SIMIT           = 235;
    public static final int KUMPIR          = 236;

    /** Total object types (NROFOBJECTS in original C) */
    public static final int NROFOBJECTS = 237;

    // -----------------------------------------------------------------------
    // Object table
    // -----------------------------------------------------------------------

    /**
     * The full object prototype array, sized to {@link #NROFOBJECTS}.
     *
     * <p>Index 0 = STRANGE_OBJECT, index 211 = JADE, indices 212-236 are the
     * Rotterdam street foods.  Entries 126-129, 151-154, 173-175 and 195-196
     * are placeholder nulls (unused slots in the original table).</p>
     *
     * <p><b>Defect history:</b> this array was previously allocated with a
     * hard-coded length of 215 while NROFOBJECTS was 237.  set() silently
     * discards any index outside the array, so every street food from index
     * 215 ("telo") upward was never registered.  createItem() then fell back
     * to index 0 and the player received a "strange object" ('\\') instead of
     * food.  The length must always track NROFOBJECTS.</p>
     */
    public static final ItemClass[] OBJECTS;

    static {
        OBJECTS = new ItemClass[NROFOBJECTS];
        // Helper: mk(name, descr, sym, prob, delay, wt, oc1, oc2, multiVal, alwaysKnown, merge)
        // 0 – strange object
        set(0,  "strange object",   null,      ILLOBJ_SYM,  0, 0,   0, 0, 0,   0, true,  false);
        // 1 – amulet
        set(1,  "Amulet of Yendor", null,      AMULET_SYM,  0, 0,  20, 0, 0,   0, true,  false);
        // 2-17 – food
        set(2,  "food ration",      null,      FOOD_SYM,   45, 5,  20, 0, 0, 800, true,  false);
        set(3,  "tripe ration",     null,      FOOD_SYM,   33, 1,   5, 0, 0, 200, true,  false);
        set(4,  "pancake",          null,      FOOD_SYM,   20, 2,   2, 0, 0, 200, true,  false);
        set(5,  "dead lizard",      null,      FOOD_SYM,    5, 1,   1, 0, 0,  40, true,  false);
        set(6,  "fortune cookie",   null,      FOOD_SYM,   10, 1,   1, 0, 0,  40, true,  false);
        set(7,  "carrot",           null,      FOOD_SYM,   15, 1,   2, 0, 0,  50, true,  false);
        set(8,  "tin",              null,      FOOD_SYM,   20, 0,  10, 0, 0,   0, true,  false);
        set(9,  "orange",           null,      FOOD_SYM,   10, 1,   2, 0, 0,  80, true,  false);
        set(10, "apple",            null,      FOOD_SYM,   15, 1,   2, 0, 0,  50, true,  false);
        set(11, "pear",             null,      FOOD_SYM,   10, 1,   2, 0, 0,  50, true,  false);
        set(12, "melon",            null,      FOOD_SYM,   10, 1,   5, 0, 0, 100, true,  false);
        set(13, "banana",           null,      FOOD_SYM,   10, 1,   2, 0, 0,  80, true,  false);
        set(14, "candy bar",        null,      FOOD_SYM,   10, 1,   2, 0, 0, 100, true,  false);
        set(15, "egg",              null,      FOOD_SYM,   15, 1,   1, 0, 0,  80, true,  false);
        set(16, "clove of garlic",  null,      FOOD_SYM,   10, 1,   1, 0, 0,  40, true,  false);
        set(17, "lump of royal jelly",null,    FOOD_SYM,    5, 1,   5, 0, 0, 200, true,  false);
        // 18-70: corpses (food items)
        for (int i = 18; i <= 70; i++) {
            set(i, corpseName(i), null, FOOD_SYM, 0, 5, 5, 0, 0, 250, true, false);
        }
        // 71-88: weapons
        set(71, "arrow",             null, WEAPON_SYM, 60,0, 1, 6, 6, 0, true, true);
        set(72, "sling bullet",      null, WEAPON_SYM, 55,0, 1, 4, 4, 0, true, true);
        set(73, "crossbow bolt",     null, WEAPON_SYM, 55,0, 1, 4, 6, 0, true, true);
        set(74, "dart",              null, WEAPON_SYM, 60,0, 1, 3, 3, 0, true, true);
        set(75, "rock",              null, WEAPON_SYM, 60,0,10, 3, 3, 0, true, true);
        set(76, "boomerang",         null, WEAPON_SYM, 15,0, 5, 9, 9, 0, true, false);
        set(77, "mace",              null, WEAPON_SYM, 55,0,30, 6, 6, 0, true, false);
        set(78, "axe",               null, WEAPON_SYM, 40,0,60, 6, 4, 0, true, false);
        set(79, "flail",             null, WEAPON_SYM, 40,0,15, 6, 6, 0, true, false);
        set(80, "long sword",        null, WEAPON_SYM, 40,0,40, 8, 6, 0, true, false);
        set(81, "two-handed sword",  null, WEAPON_SYM, 15,0,100,12,6, 0, true, false);
        set(82, "dagger",            null, WEAPON_SYM, 55,0,10, 4, 3, 0, true, false);
        set(83, "worm tooth",        null, WEAPON_SYM,  5,0, 2, 2, 2, 0, true, false);
        set(84, "crysknife",         null, WEAPON_SYM,  5,0, 5,10,10, 0, true, false);
        set(85, "spear",             null, WEAPON_SYM, 50,0,30, 6, 8, 0, true, false);
        set(86, "bow",               null, WEAPON_SYM, 30,0,30, 1, 1, 0, true, false);
        set(87, "sling",             null, WEAPON_SYM, 20,0, 3, 1, 1, 0, true, false);
        set(88, "crossbow",          null, WEAPON_SYM, 25,0,50, 1, 1, 0, true, false);
        // 89-94: tools
        set(89, "whistle",           null, TOOL_SYM,  30,0, 3,0,0,0,  true,  false);
        set(90, "magic whistle",     null, TOOL_SYM,   5,0, 3,0,0,0,  false, false);
        set(91, "expensive camera",  null, TOOL_SYM,  10,0,12,0,0,0,  true,  false);
        set(92, "ice box",           null, TOOL_SYM,   5,0,100,0,0,0, true,  false);
        set(93, "pick-axe",          null, TOOL_SYM,  20,0,100,6,3,0, true,  false);
        set(94, "can opener",        null, TOOL_SYM,  30,0, 4,0,0,0,  true,  false);
        // 95-97: balls/chains/rocks
        set(95, "heavy iron ball",   null, BALL_SYM,  10,0,480,0,0,0, true, false);
        set(96, "iron chain",        null, CHAIN_SYM, 10,0,120,0,0,0, true, false);
        set(97, "enormous rock",     null, ROCK_SYM,  10,0,1000,0,0,0,true, false);
        // 98-109: armour
        set(98,  "helmet",                   null, ARMOR_SYM,30,1, 30, 1,0,0, true, false);
        set(99,  "plate mail",               null, ARMOR_SYM,20,5,450, 7,0,0, true, false);
        set(100, "splint mail",              null, ARMOR_SYM,15,5,400, 6,0,0, true, false);
        set(101, "banded mail",              null, ARMOR_SYM,15,5,350, 6,0,0, true, false);
        set(102, "chain mail",               null, ARMOR_SYM,15,5,300, 5,0,0, true, false);
        set(103, "scale mail",               null, ARMOR_SYM,15,5,250, 4,0,0, true, false);
        set(104, "ring mail",                null, ARMOR_SYM,15,5,250, 3,0,0, true, false);
        set(105, "studded leather armor",    null, ARMOR_SYM,15,3,200, 3,0,0, true, false);
        set(106, "leather armor",            null, ARMOR_SYM,20,3,150, 2,0,0, true, false);
        set(107, "elven cloak",              null, ARMOR_SYM,15,0, 10, 1,2,0, true, false);
        set(108, "shield",                   null, ARMOR_SYM,30,0, 50, 1,0,0, true, false);
        set(109, "pair of gloves",           null, ARMOR_SYM,30,0, 10, 1,0,0, true, false);
        // 110-125: potions (descriptions randomised at game start)
        setPot(110, "restore strength");
        setPot(111, "booze");
        setPot(112, "invisibility");
        setPot(113, "fruit juice");
        setPot(114, "healing");
        setPot(115, "paralysis");
        setPot(116, "monster detection");
        setPot(117, "object detection");
        setPot(118, "sickness");
        setPot(119, "confusion");
        setPot(120, "gain strength");
        setPot(121, "speed");
        setPot(122, "blindness");
        setPot(123, "gain level");
        setPot(124, "extra healing");
        setPot(125, "levitation");
        // 126-129: gaps
        // 130-150: scrolls
        setScr(130, "mail");
        setScr(131, "enchant armor");
        setScr(132, "destroy armor");
        setScr(133, "confuse monster");
        setScr(134, "scare monster");
        setScr(135, "blank paper");
        setScr(136, "remove curse");
        setScr(137, "enchant weapon");
        setScr(138, "damage weapon");
        setScr(139, "create monster");
        setScr(140, "taming");
        setScr(141, "genocide");
        setScr(142, "light");
        setScr(143, "teleportation");
        setScr(144, "gold detection");
        setScr(145, "food detection");
        setScr(146, "identify");
        setScr(147, "magic mapping");
        setScr(148, "amnesia");
        setScr(149, "fire");
        setScr(150, "punishment");
        // 151-154: gaps
        // 155-172: wands
        setWand(155, "light",                1);
        setWand(156, "secret door detection",1);
        setWand(157, "create monster",       2);
        setWand(158, "wishing",              2);
        setWand(159, "striking",             2);
        setWand(160, "slow monster",         2);
        setWand(161, "speed monster",        2);
        setWand(162, "undead turning",       2);
        setWand(163, "polymorph",            2);
        setWand(164, "cancellation",         2);
        setWand(165, "teleportation",        2);
        setWand(166, "make invisible",       2);
        setWand(167, "digging",              4);
        setWand(168, "magic missile",        4);
        setWand(169, "fire",                 4);
        setWand(170, "sleep",                4);
        setWand(171, "cold",                 4);
        setWand(172, "death",                4);
        // 173-175: gaps
        // 176-194: rings
        setRing(176, "adornment",                    false);
        setRing(177, "teleportation",                true);
        setRing(178, "regeneration",                 true);
        setRing(179, "searching",                    true);
        setRing(180, "see invisible",                false);
        setRing(181, "stealth",                      false);
        setRing(182, "levitation",                   true);
        setRing(183, "poison resistance",            false);
        setRing(184, "aggravate monster",            false);
        setRing(185, "hunger",                       false);
        setRing(186, "fire resistance",              false);
        setRing(187, "cold resistance",              false);
        setRing(188, "protection from shape changers",false);
        setRing(189, "conflict",                     false);
        setRing(190, "gain strength",                true);
        setRing(191, "increase damage",              true);
        setRing(192, "protection",                   true);
        setRing(193, "warning",                      false);
        setRing(194, "teleport control",             false);
        // 195-196: gaps
        // 197-211: gems
        setGem(197, "diamond",    4000);
        setGem(198, "ruby",       3500);
        setGem(199, "sapphire",   3000);
        setGem(200, "emerald",    2500);
        setGem(201, "turquoise",   900);
        setGem(202, "aquamarine", 1500);
        setGem(203, "tourmaline",  700);
        setGem(204, "topaz",       500);
        setGem(205, "opal",       1000);
        setGem(206, "garnet",      700);
        setGem(207, "amethyst",    600);
        setGem(208, "agate",       500);
        setGem(209, "onyx",        400);
        setGem(210, "jasper",      400);
        setGem(211, "jade",        300);

        // ── Rotterdam street food (indices 212-221) ─────────────────────────
        set(212, "bara",          null, FOOD_SYM, 33, 1, 5, 0, 0, 200, true, false);
        set(213, "blood sausage", null, FOOD_SYM, 33, 1, 5, 0, 0, 200, true, false);
        set(214, "loempia",       null, FOOD_SYM, 33, 1, 5, 0, 0, 200, true, false);
        set(215, "telo",          null, FOOD_SYM, 33, 1, 5, 0, 0, 200, true, false);
        set(216, "baka bana",     null, FOOD_SYM, 33, 1, 5, 0, 0, 200, true, false);
        set(217, "krupuk",        null, FOOD_SYM, 33, 1, 5, 0, 0, 200, true, false);
        set(218, "lahmacun",      null, FOOD_SYM, 33, 1, 5, 0, 0, 200, true, false);
        set(219, "kapsalon",      null, FOOD_SYM, 33, 1, 5, 0, 0, 200, true, false);
        set(220, "sausage roll",  null, FOOD_SYM, 33, 1, 5, 0, 0, 200, true, false);
        set(221, "samosa",        null, FOOD_SYM, 33, 1, 5, 0, 0, 200, true, false);

        // ── Additional Dutch/Rotterdam/Indonesian street foods ──────────────
        set(222, "haring",        null, FOOD_SYM, 33, 1, 5, 0, 0, 200, true, false);
        set(223, "kibbeling",     null, FOOD_SYM, 33, 1, 5, 0, 0, 200, true, false);
        set(224, "bitterbal",     null, FOOD_SYM, 33, 1, 5, 0, 0, 200, true, false);
        set(225, "kroket",        null, FOOD_SYM, 33, 1, 5, 0, 0, 200, true, false);
        set(226, "patat",         null, FOOD_SYM, 33, 1, 5, 0, 0, 200, true, false);
        set(227, "frikandel",     null, FOOD_SYM, 33, 1, 5, 0, 0, 200, true, false);
        set(228, "stroopwafel",   null, FOOD_SYM, 33, 1, 5, 0, 0, 200, true, false);
        set(229, "bapao",         null, FOOD_SYM, 33, 1, 5, 0, 0, 200, true, false);
        set(230, "pisang goreng", null, FOOD_SYM, 33, 1, 5, 0, 0, 200, true, false);
        set(231, "lemper",        null, FOOD_SYM, 33, 1, 5, 0, 0, 200, true, false);
        set(232, "pastei",        null, FOOD_SYM, 33, 1, 5, 0, 0, 200, true, false);
        set(233, "risoles",       null, FOOD_SYM, 33, 1, 5, 0, 0, 200, true, false);
        set(234, "martabak",      null, FOOD_SYM, 33, 1, 5, 0, 0, 200, true, false);
        set(235, "simit",         null, FOOD_SYM, 33, 1, 5, 0, 0, 200, true, false);
        set(236, "kumpir",        null, FOOD_SYM, 33, 1, 5, 0, 0, 200, true, false);
    }

    // -----------------------------------------------------------------------
    // Helpers used during static initialisation
    // -----------------------------------------------------------------------

    private static void set(int i, String name, String descr, char sym,
                             int prob, int delay, int wt,
                             int oc1, int oc2, int multiVal,
                             boolean alwaysKnown, boolean merge) {
        if (i >= 0 && i < OBJECTS.length)
            OBJECTS[i] = new ItemClass(name, descr, sym, prob, delay, wt,
                                       oc1, oc2, multiVal, alwaysKnown, merge);
    }

    private static void setPot(int i, String name) {
        OBJECTS[i] = new ItemClass("potion of " + name, "potion", POTION_SYM,
                                   10, 0, 20, 0, 0, 0, false, false);
    }

    private static void setScr(int i, String name) {
        OBJECTS[i] = new ItemClass("scroll of " + name, "scroll", SCROLL_SYM,
                                   10, 0, 5, 0, 0, 0, false, false);
    }

    private static void setWand(int i, String name, int bits) {
        OBJECTS[i] = new ItemClass("wand of " + name, "wand", WAND_SYM,
                                   10, 0, 7, bits, 0, 0, false, false);
    }

    private static void setRing(int i, String name, boolean spec) {
        OBJECTS[i] = new ItemClass("ring of " + name, "ring", RING_SYM,
                                   5, 0, 3, spec ? 1 : 0, 0, 0, false, false);
    }

    private static void setGem(int i, String name, int value) {
        OBJECTS[i] = new ItemClass(name, "gem", GEM_SYM,
                                   2, 0, 1, 0, 0, value, false, false);
    }

    /** Derives a corpse name from its index (indices 18-70). */
    private static String corpseName(int i) {
        // Map back to monster name via MonsterPrototype array index
        int mIdx = i - 18;
        if (mIdx < MonsterPrototype.ALL.length) {
            return "dead " + MonsterPrototype.ALL[mIdx].name;
        }
        return "dead creature";
    }

    // -----------------------------------------------------------------------
    // Lookup
    // -----------------------------------------------------------------------

    /**
     * Returns the ItemClass for the given type index, or null.
     *
     * @param typeIndex object type index (0-214)
     * @return ItemClass or null
     */
    public static ItemClass get(int typeIndex) {
        if (typeIndex >= 0 && typeIndex < OBJECTS.length)
            return OBJECTS[typeIndex];
        return null;
    }
}
