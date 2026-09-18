package hack.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * A single entry in the persistent high score table.
 * Fields mirror the instruction: name, class, farthest floor, score, date-time.
 */
public class HighScoreEntry implements Comparable<HighScoreEntry> {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String name;
    private final String characterClass;
    private final int    maxFloor;
    private final long   score;
    private final String dateTime;

    public HighScoreEntry(String name, String characterClass,
                          int maxFloor, long score) {
        this.name           = name;
        this.characterClass = characterClass;
        this.maxFloor       = maxFloor;
        this.score          = score;
        this.dateTime       = LocalDateTime.now().format(FMT);
    }

    /** JSON-reconstruction constructor (from persisted data). */
    public HighScoreEntry(String name, String characterClass,
                          int maxFloor, long score, String dateTime) {
        this.name           = name;
        this.characterClass = characterClass;
        this.maxFloor       = maxFloor;
        this.score          = score;
        this.dateTime       = dateTime;
    }

    public String getName()           { return name; }
    public String getCharacterClass() { return characterClass; }
    public int    getMaxFloor()       { return maxFloor; }
    public long   getScore()          { return score; }
    public String getDateTime()       { return dateTime; }

    /** Highest score sorts first. */
    @Override
    public int compareTo(HighScoreEntry o) {
        return Long.compare(o.score, this.score);
    }

    /** Simple JSON serialisation (no external library needed). */
    public String toJson() {
        return String.format(
            "{\"name\":%s,\"class\":%s,\"floor\":%d,\"score\":%d,\"time\":%s}",
            jsonStr(name), jsonStr(characterClass), maxFloor, score, jsonStr(dateTime)
        );
    }

    private static String jsonStr(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\","\\\\").replace("\"","\\\"") + "\"";
    }

    /** Parse one JSON object line produced by toJson(). */
    public static HighScoreEntry fromJson(String json) {
        try {
            String name  = parseStr(json, "name");
            String cls   = parseStr(json, "class");
            int floor    = parseInt(json, "floor");
            long score   = parseLong(json, "score");
            String time  = parseStr(json, "time");
            return new HighScoreEntry(name, cls, floor, score, time);
        } catch (Exception e) {
            return null;
        }
    }

    private static String parseStr(String j, String key) {
        int i = j.indexOf("\"" + key + "\":");
        if (i < 0) return "";
        i = j.indexOf("\"", i + key.length() + 3);
        int e = j.indexOf("\"", i + 1);
        return j.substring(i+1, e);
    }
    private static int parseInt(String j, String key) {
        int i = j.indexOf("\"" + key + "\":");
        if (i < 0) return 0;
        i += key.length() + 3;
        int e = j.indexOf(',', i);
        if (e < 0) e = j.indexOf('}', i);
        return Integer.parseInt(j.substring(i, e).trim());
    }
    private static long parseLong(String j, String key) {
        int i = j.indexOf("\"" + key + "\":");
        if (i < 0) return 0;
        i += key.length() + 3;
        int e = j.indexOf(',', i);
        if (e < 0) e = j.indexOf('}', i);
        return Long.parseLong(j.substring(i, e).trim());
    }
}
