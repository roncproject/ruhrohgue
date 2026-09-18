package hack.engine;

import hack.model.*;
import java.util.*;

/**
 * LevelGenerator.java — Procedural dungeon generator v11.
 *
 * <h2>Special-room rules</h2>
 * <ul>
 *   <li><b>Odd floors (1,3,5,…)</b>: one Vault with gold, a Bank guard, no shop.</li>
 *   <li><b>Even floors (2,4,6,…)</b>: one Shop with food/merchandise, a shopkeeper, no Bank.</li>
 *   <li>Special rooms are 2–8 tiles wide and 2–8 tiles tall.</li>
 *   <li>Special rooms are always reachable via a corridor and door.</li>
 *   <li>Guards/shopkeepers are peaceful until the player takes an item.</li>
 *   <li>Guards/shopkeepers cannot leave their room (homeRoom confinement).</li>
 * </ul>
 *
 * <h2>Stair symbols</h2>
 * <ul>
 *   <li>'>' always leads DOWN to the next floor.</li>
 *   <li>'<' always leads UP to the previous floor (or exit on floor 1).</li>
 * </ul>
 */
public class LevelGenerator {

    private static final int XLIM      = 5;
    private static final int YLIM      = 3;
    private static final int MIN_ROOMS = 5;
    private static final int MAX_ROOMS = DungeonLevel.COLNO / 5;

    // Special room size limits
    private static final int SR_MIN_W = 2;
    private static final int SR_MAX_W = 8;
    private static final int SR_MIN_H = 2;
    private static final int SR_MAX_H = 8;

    // ===================================================================
    // Entry point
    // ===================================================================

    public static void makeLevel(GameState state, int dlevel) {
        // Build this floor from its own derived seed so the layout depends only
        // on (game seed, floor number) and never on how much randomness play
        // consumed on the way down. See Dice.beginLevelGeneration.
        Dice.beginLevelGeneration(dlevel);
        try {
            makeLevelInternal(state, dlevel);
        } finally {
            Dice.endLevelGeneration();
        }
    }

    private static void makeLevelInternal(GameState state, int dlevel) {
        DungeonLevel level = new DungeonLevel();
        level.resetCells();
        state.setLevel(level);

        if (dlevel >= Dice.rn1(3, 26)) {
            makeMaze(level);
        } else {
            makeRooms(level, dlevel);
        }

        // The special room (Bank on odd floors, Shop on even floors) is carved
        // BEFORE the stairs so placeStairs() can exclude it. Previously the
        // order was reversed, which allowed a staircase to end up inside the
        // Bank or Shop.
        placeSpecialRoom(level, dlevel, state);
        placeStairs(level);
        placeTraps(level, dlevel);
        placeGold(level, dlevel);
        placeFood(level, dlevel);

        // Every floor must be fully walkable from the arrival staircase: both
        // stairs and the Bank/Shop door reachable. A carved special room whose
        // connecting corridor failed to land left the Bank stranded, and an
        // isolated stair made the floor look like it had no exit.
        ensureLevelReachable(level);

        Player p = state.getPlayer();
        p.setPos(level.getXUpStair(), level.getYUpStair());

        // Initial monsters belong to the floor, so they are rolled inside the
        // deterministic generation window too. Previously every caller spawned
        // them afterwards, from the gameplay RNG stream, which made monster
        // placement on deeper floors depend on the player's history.
        spawnInitialMonsters(state);
    }

    /**
     * Populates a freshly generated floor with its starting monsters.
     *
     * <p>Called from {@link #makeLevel} inside the per-floor RNG window, so the
     * same seed always yields the same monsters in the same places.</p>
     */
    public static void spawnInitialMonsters(GameState state) {
        DungeonLevel level = state.getLevel();
        if (level == null) return;
        Player p = state.getPlayer();
        for (Room r : level.getRooms()) {
            if (r.type == RoomType.VAULT || r.type.isShop()) continue; // staffed rooms
            if (!Dice.oneIn(3)) continue;
            int x = r.randomX(), y = r.randomY();
            if (level.cellAt(x, y).getType() != CellType.ROOM) continue;
            if (state.monsterAt(x, y) != null) continue;
            if (p != null && p.getX() == x && p.getY() == y) continue;
            MonsterEngine.makemon(state, null, x, y);
        }
    }


    // ===================================================================
    // Reachability guarantee
    // ===================================================================

    /** True when the cell can be walked through. */
    private static boolean walkable(DungeonLevel level, int x, int y) {
        if (!DungeonLevel.isOk(x, y)) return false;
        CellType t = level.cellAt(x, y).getType();
        return t == CellType.ROOM || t == CellType.CORR || t == CellType.SCORR
            || t == CellType.DOOR || t == CellType.LDOOR || t == CellType.SDOOR
            || t == CellType.STAIRS;
    }

    /** Flood-fills walkable cells from (sx,sy). */
    private static boolean[][] reachableFrom(DungeonLevel level, int sx, int sy) {
        boolean[][] seen = new boolean[DungeonLevel.COLNO][DungeonLevel.ROWNO];
        if (!walkable(level, sx, sy)) return seen;
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{sx, sy});
        seen[sx][sy] = true;
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] d : dirs) {
                int nx = c[0] + d[0], ny = c[1] + d[1];
                if (!DungeonLevel.isOk(nx, ny) || seen[nx][ny]) continue;
                if (!walkable(level, nx, ny)) continue;
                seen[nx][ny] = true;
                queue.add(new int[]{nx, ny});
            }
        }
        return seen;
    }

    /**
     * Guarantees both staircases and every room are reachable from the
     * up staircase, digging a corridor to anything stranded.
     */
    private static void ensureLevelReachable(DungeonLevel level) {
        for (int attempt = 0; attempt < 12; attempt++) {
            boolean[][] seen = reachableFrom(level, level.getXUpStair(), level.getYUpStair());
            int[] stranded = firstStranded(level, seen);
            if (stranded == null) return;                       // all connected
            int[] anchor = nearestReachable(level, seen, stranded[0], stranded[1]);
            if (anchor == null) return;                         // nothing to join to
            digPath(level, anchor[0], anchor[1], stranded[0], stranded[1]);
        }
    }

    /** Finds a stair or room cell that the flood fill did not reach. */
    private static int[] firstStranded(DungeonLevel level, boolean[][] seen) {
        if (!seen[level.getXDnStair()][level.getYDnStair()])
            return new int[]{level.getXDnStair(), level.getYDnStair()};
        for (Room r : level.getRooms()) {
            for (int x = r.lx; x <= r.hx; x++)
                for (int y = r.ly; y <= r.hy; y++)
                    if (level.cellAt(x, y).getType() == CellType.ROOM && !seen[x][y])
                        return new int[]{x, y};
        }
        return null;
    }

    /** Closest already-reachable walkable cell to (tx,ty). */
    private static int[] nearestReachable(DungeonLevel level, boolean[][] seen, int tx, int ty) {
        int[] best = null; int bestDist = Integer.MAX_VALUE;
        for (int x = 0; x < DungeonLevel.COLNO; x++) {
            for (int y = 0; y < DungeonLevel.ROWNO; y++) {
                if (!seen[x][y]) continue;
                int d = Math.abs(x - tx) + Math.abs(y - ty);
                if (d < bestDist) { bestDist = d; best = new int[]{x, y}; }
            }
        }
        return best;
    }

    /**
     * Digs an L-shaped corridor between two points, turning any room wall it
     * crosses into a door and leaving existing floor, stairs and doors intact.
     */
    private static void digPath(DungeonLevel level, int x0, int y0, int x1, int y1) {
        int x = x0, y = y0;
        int guard = 0;
        while ((x != x1 || y != y1) && guard++ < 400) {
            if (x != x1)      x += Integer.signum(x1 - x);
            else if (y != y1) y += Integer.signum(y1 - y);
            carvePassage(level, x, y);
        }
    }

    /** Turns one cell into walkable space without destroying what matters. */
    private static void carvePassage(DungeonLevel level, int x, int y) {
        if (!DungeonLevel.isOk(x, y)) return;
        if (x <= 0 || y <= 0 || x >= DungeonLevel.COLNO - 1 || y >= DungeonLevel.ROWNO - 1) return;
        Cell c = level.cellAt(x, y);
        CellType t = c.getType();
        if (t == CellType.ROOM || t == CellType.CORR || t == CellType.SCORR
                || t == CellType.DOOR || t == CellType.LDOOR || t == CellType.SDOOR
                || t == CellType.STAIRS) {
            return;                                   // already passable
        }
        if (t == CellType.HWALL || t == CellType.VWALL) {
            // A wall breached by the reachability repair becomes a closed door.
            c.setType(CellType.DOOR); c.setDoorOpen(false);
        } else {
            c.setType(CellType.CORR); c.setScrsym('#');
        }
        c.markDirty();
    }

    // ===================================================================
    // Room-based generation
    // ===================================================================

    static void makeRooms(DungeonLevel level, int dlevel) {
        List<Room> rooms = level.getRooms();
        int target   = MIN_ROOMS + Dice.rn2(Math.max(1, MAX_ROOMS - MIN_ROOMS));
        int attempts = 0;

        while (rooms.size() < target && attempts < 600) {
            attempts++;
            int dx = 3 + Dice.rn2(7);
            int dy = 2 + Dice.rn2(4);
            int lx = Math.max(2, XLIM + Dice.rn2(
                    Math.max(1, DungeonLevel.COLNO - dx - 2 * XLIM - 3)));
            int ly = Math.max(2, YLIM + Dice.rn2(
                    Math.max(1, DungeonLevel.ROWNO - dy - 2 * YLIM - 2)));
            int hx = lx + dx, hy = ly + dy;
            if (hx + 2 >= DungeonLevel.COLNO - 1) continue;
            if (hy + 2 >= DungeonLevel.ROWNO - 1) continue;
            if (overlapCheck(level, lx, ly, hx, hy)) continue;

            boolean lit = (dlevel < 10 || Dice.rnd(dlevel) < 10);
            Room room = new Room(lx, hx, ly, hy, RoomType.ORDINARY, lit);
            carveRoom(level, room);
            rooms.add(room);
        }
        if (rooms.size() < MIN_ROOMS) forceMinRooms(level, rooms);
        connectAllRooms(level, rooms);
        ensureAllRoomsConnected(level, rooms);
    }

    private static void forceMinRooms(DungeonLevel level, List<Room> rooms) {
        int[][] fb = {
            {3,10,2,6},{22,35,2,7},{50,65,2,6},
            {3,12,11,16},{30,50,11,17},{55,72,11,17},
            {3,10,15,19},{40,60,14,19},
        };
        for (int[] f : fb) {
            if (rooms.size() >= MIN_ROOMS) break;
            int lx=f[0],hx=f[1],ly=f[2],hy=f[3];
            if (hx+2>=DungeonLevel.COLNO-1||hy+2>=DungeonLevel.ROWNO-1) continue;
            if (overlapCheck(level,lx,ly,hx,hy)) continue;
            Room r = new Room(lx,hx,ly,hy,RoomType.ORDINARY,true);
            carveRoom(level,r); rooms.add(r);
        }
    }

    private static boolean overlapCheck(DungeonLevel level,
                                         int lx,int ly,int hx,int hy) {
        int x0=Math.max(0,lx-XLIM),x1=Math.min(DungeonLevel.COLNO-1,hx+XLIM);
        int y0=Math.max(0,ly-YLIM),y1=Math.min(DungeonLevel.ROWNO-1,hy+YLIM);
        for (int x=x0;x<=x1;x++)
            for (int y=y0;y<=y1;y++)
                if (level.cellAt(x,y).getType()!=CellType.EMPTY) return true;
        return false;
    }

    static void carveRoom(DungeonLevel level, Room room) {
        for (int x=room.lx-1;x<=room.hx+1;x++) {
            setCell(level,x,room.ly-1,CellType.HWALL,'-');
            setCell(level,x,room.hy+1,CellType.HWALL,'-');
        }
        for (int y=room.ly;y<=room.hy;y++) {
            setCell(level,room.lx-1,y,CellType.VWALL,'|');
            setCell(level,room.hx+1,y,CellType.VWALL,'|');
        }
        for (int x=room.lx;x<=room.hx;x++)
            for (int y=room.ly;y<=room.hy;y++) {
                setCell(level,x,y,CellType.ROOM,'.');
                if (room.lit) level.cellAt(x,y).setLit(true);
            }
    }

    // ===================================================================
    // Connectivity
    // ===================================================================

    private static void connectAllRooms(DungeonLevel level, List<Room> rooms) {
        int n = rooms.size();
        List<Integer> order = new ArrayList<>();
        for (int i=0;i<n;i++) order.add(i);
        Collections.shuffle(order, new java.util.Random(Dice.rnd(100000)));
        for (int i=0;i<n-1;i++)
            joinRoomsAligned(level,rooms.get(order.get(i)),rooms.get(order.get(i+1)));
        for (int i=0;i<n-2;i++)
            if (Dice.oneIn(2))
                joinRoomsAligned(level,rooms.get(order.get(i)),rooms.get(order.get(i+2)));
    }

    private static void ensureAllRoomsConnected(DungeonLevel level, List<Room> rooms) {
        if (rooms.isEmpty()) return;
        Set<Integer> reached = floodReachable(level,rooms,0);
        for (int i=1;i<rooms.size();i++) {
            if (!reached.contains(i)) {
                joinRoomsAligned(level,rooms.get(i),rooms.get(0));
                reached = floodReachable(level,rooms,0);
            }
        }
    }

    private static Set<Integer> floodReachable(DungeonLevel level,
                                                List<Room> rooms, int startIdx) {
        boolean[][] visited = new boolean[DungeonLevel.COLNO][DungeonLevel.ROWNO];
        Set<Integer> reached = new HashSet<>();
        reached.add(startIdx);
        Queue<int[]> q = new LinkedList<>();
        Room r0=rooms.get(startIdx);
        for (int x=r0.lx;x<=r0.hx;x++)
            for (int y=r0.ly;y<=r0.hy;y++) { q.add(new int[]{x,y}); visited[x][y]=true; }
        int[][] dirs={{1,0},{-1,0},{0,1},{0,-1}};
        while (!q.isEmpty()) {
            int[] cur=q.poll();
            for (int[] d:dirs) {
                int nx=cur[0]+d[0],ny=cur[1]+d[1];
                if (!DungeonLevel.isOk(nx,ny)||visited[nx][ny]) continue;
                CellType t=level.cellAt(nx,ny).getType();
                if (t==CellType.CORR||t==CellType.DOOR||
                    t==CellType.ROOM||t==CellType.STAIRS) {
                    visited[nx][ny]=true; q.add(new int[]{nx,ny});
                    for (int i=0;i<rooms.size();i++) {
                        Room rm=rooms.get(i);
                        if (nx>=rm.lx&&nx<=rm.hx&&ny>=rm.ly&&ny<=rm.hy)
                            reached.add(i);
                    }
                }
            }
        }
        return reached;
    }

    // ===================================================================
    // Corridor routing — 1-tile-wide, mid-wall, door-aligned
    // ===================================================================

    private static void joinRoomsAligned(DungeonLevel level, Room a, Room b) {
        int[] exit  = chooseMidWallExit(a, b);
        int[] entry = chooseMidWallExit(b, a);
        int ex=exit[0],ey=exit[1],nx=exit[2],ny=exit[3];
        int dx=entry[0],dy=entry[1],fx=entry[2],fy=entry[3];
        placeDoorOnWall(level,ex,ey,nx,ny);
        placeDoorOnWall(level,dx,dy,fx,fy);
        int bx,by;
        if (Math.abs(nx-fx)<=Math.abs(ny-fy)) { bx=fx; by=ny; }
        else                                   { bx=nx; by=fy; }
        int xMin=Math.min(nx,bx),xMax=Math.max(nx,bx);
        for (int x=xMin;x<=xMax;x++) digCorridorSingle(level,x,ny);
        int yMin=Math.min(ny,fy),yMax=Math.max(ny,fy);
        for (int y=yMin;y<=yMax;y++) digCorridorSingle(level,bx,y);
        xMin=Math.min(bx,fx); xMax=Math.max(bx,fx);
        for (int x=xMin;x<=xMax;x++) digCorridorSingle(level,x,fy);
    }

    private static int[] chooseMidWallExit(Room from, Room to) {
        boolean toBelow = to.ly > from.hy+1;
        boolean toAbove = to.hy < from.ly-1;
        boolean toRight = to.lx > from.hx+1;
        boolean toLeft  = to.hx < from.lx-1;
        // midX / midY helpers — always picks a position STRICTLY between the corners
        // (lx+1 .. hx-1) horizontally, (ly+1 .. hy-1) vertically.
        // This prevents corridors from connecting to room corners.
        int midX = from.lx + 1 + Dice.rn2(Math.max(1, from.width() - 1));
        midX = clamp(midX, from.lx + 1, from.hx - 1);
        int midY = from.ly + 1 + Dice.rn2(Math.max(1, from.height() - 1));
        midY = clamp(midY, from.ly + 1, from.hy - 1);

        if (toBelow&&!toRight&&!toLeft) {
            return new int[]{midX,from.hy+1,midX,from.hy+2};
        }
        if (toAbove&&!toRight&&!toLeft) {
            return new int[]{midX,from.ly-1,midX,from.ly-2};
        }
        if (toRight&&!toBelow&&!toAbove) {
            return new int[]{from.hx+1,midY,from.hx+2,midY};
        }
        if (toLeft&&!toBelow&&!toAbove) {
            return new int[]{from.lx-1,midY,from.lx-2,midY};
        }
        int dy2=Math.abs((from.ly+from.height()/2)-(to.ly+to.height()/2));
        int dx2=Math.abs((from.lx+from.width()/2)-(to.lx+to.width()/2));
        if (dy2>=dx2) {
            if (to.ly>from.ly) { return new int[]{midX,from.hy+1,midX,from.hy+2}; }
            else               { return new int[]{midX,from.ly-1,midX,from.ly-2}; }
        } else {
            if (to.lx>from.lx) { return new int[]{from.hx+1,midY,from.hx+2,midY}; }
            else               { return new int[]{from.lx-1,midY,from.lx-2,midY}; }
        }
    }

    private static int clamp(int v,int lo,int hi) { return Math.max(lo,Math.min(hi,v)); }

    private static void placeDoorOnWall(DungeonLevel level,int wx,int wy,int cx,int cy) {
        if (!DungeonLevel.isOk(wx,wy)||!DungeonLevel.isOk(cx,cy)) return;
        Cell wall=level.cellAt(wx,wy);
        if (wall.getType()!=CellType.HWALL&&wall.getType()!=CellType.VWALL) return;
        if (level.cellAt(cx,cy).getType()==CellType.ROOM) return;
        boolean secret=Dice.oneIn(12);
        wall.setType(secret?CellType.SDOOR:CellType.DOOR);
        if (!secret) {
            // Half the doors start closed ('+'), half open (' '). The state is
            // recorded on the cell, not inferred from its symbol — scrsym is
            // overwritten by anything that stands on the door.
            wall.setDoorOpen(Dice.oneIn(2));
        }
    }

    private static void digCorridorSingle(DungeonLevel level,int x,int y) {
        if (x<1||x>=DungeonLevel.COLNO-1) return;
        if (y<1||y>=DungeonLevel.ROWNO-1) return;
        Cell c=level.cellAt(x,y);
        CellType t=c.getType();
        if (t==CellType.ROOM||t==CellType.HWALL||t==CellType.VWALL||
            t==CellType.DOOR||t==CellType.SDOOR||t==CellType.STAIRS) return;
        if (t==CellType.EMPTY||t==CellType.CORR||t==CellType.SCORR) {
            c.setType(CellType.CORR); c.setScrsym('#');
        }
    }

    // ===================================================================
    // Maze generation
    // ===================================================================

    static void makeMaze(DungeonLevel level) {
        for (int x=0;x<DungeonLevel.COLNO;x++)
            for (int y=0;y<DungeonLevel.ROWNO;y++) {
                level.cellAt(x,y).setType(CellType.HWALL);
                level.cellAt(x,y).setScrsym('-');
            }
        int sx=1+2*Dice.rn2((DungeonLevel.COLNO-2)/2);
        int sy=1+2*Dice.rn2((DungeonLevel.ROWNO-2)/2);
        openMazeCell(level,sx,sy);
        Deque<int[]> stack=new ArrayDeque<>(); stack.push(new int[]{sx,sy});
        int[][] dirs={{0,-2},{2,0},{0,2},{-2,0}};
        while (!stack.isEmpty()) {
            int[] cur=stack.peek(); List<int[]> nb=new ArrayList<>();
            for (int[] d:dirs) { int nx=cur[0]+d[0],ny=cur[1]+d[1];
                if (DungeonLevel.isOk(nx,ny)&&level.cellAt(nx,ny).getType()==CellType.HWALL)
                    nb.add(new int[]{nx,ny,cur[0]+d[0]/2,cur[1]+d[1]/2}); }
            if (nb.isEmpty()) { stack.pop(); }
            else { int[] ch=nb.get(Dice.rn2(nb.size())); openMazeCell(level,ch[2],ch[3]); openMazeCell(level,ch[0],ch[1]); stack.push(new int[]{ch[0],ch[1]}); }
        }
    }

    private static void openMazeCell(DungeonLevel level,int x,int y) {
        if (!DungeonLevel.isOk(x,y)) return;
        level.cellAt(x,y).setType(CellType.CORR); level.cellAt(x,y).setScrsym('#');
    }

    // ===================================================================
    // Stair placement — '>' always DOWN, '<' always UP
    // ===================================================================

    private static void placeStairs(DungeonLevel level) {
        List<Room> rooms=level.getRooms();
        if (rooms.isEmpty()) { placeStairsInMaze(level); return; }

        // Stairs belong in ordinary rooms. A staircase inside the Bank or the
        // Shop is unreachable without provoking the guard, so build a
        // candidate list that excludes special rooms and fall back to the full
        // list only if the floor somehow has nothing else.
        List<Integer> pick = new ArrayList<>();
        for (int i = 0; i < rooms.size(); i++) {
            RoomType t = rooms.get(i).type;
            if (t != RoomType.VAULT && !t.isShop()) pick.add(i);
        }
        if (pick.size() < 2) {
            pick.clear();
            for (int i = 0; i < rooms.size(); i++) pick.add(i);
        }
        int n = pick.size();
        int dIdx = pick.get(Dice.rn2(n)), uIdx = dIdx;
        if (n > 1) { do { uIdx = pick.get(Dice.rn2(n)); } while (uIdx == dIdx); }
        int[] dn=findFreeFloor(level,rooms.get(dIdx));
        int[] up=findFreeFloor(level,rooms.get(uIdx));
        // Final fallback: scan ALL rooms for any ROOM-type cell
        if (dn == null) {
            outer_dn:
            for (Room r2 : rooms) {
                for (int x = r2.lx; x <= r2.hx; x++)
                    for (int y = r2.ly; y <= r2.hy; y++)
                        if (level.cellAt(x,y).getType() == CellType.ROOM) { dn=new int[]{x,y}; break outer_dn; }
            }
        }
        if (up == null) {
            outer_up:
            for (Room r2 : rooms) {
                for (int x = r2.lx; x <= r2.hx; x++)
                    for (int y = r2.ly; y <= r2.hy; y++)
                        if (level.cellAt(x,y).getType() == CellType.ROOM
                                && (dn==null || x!=dn[0] || y!=dn[1])) { up=new int[]{x,y}; break outer_up; }
            }
        }
        // '>' = DOWN stair (go to deeper floor)
        setCell(level,dn[0],dn[1],CellType.STAIRS,'>'); level.setDnStair(dn[0],dn[1]);
        // '<' = UP stair  (go to shallower floor / exit)
        setCell(level,up[0],up[1],CellType.STAIRS,'<'); level.setUpStair(up[0],up[1]);
    }

    /**
     * Finds a free ROOM-type floor cell in the given room.
     * Returns null only if the room contains no ROOM-type cells at all
     * (extremely rare — means every cell was overwritten by corridors).
     * Never returns a corridor or wall cell.
     */
    private static int[] findFreeFloor(DungeonLevel level, Room r) {
        List<int[]> candidates = new ArrayList<>();
        for (int x = r.lx; x <= r.hx; x++) {
            for (int y = r.ly; y <= r.hy; y++) {
                CellType t = level.cellAt(x, y).getType();
                if (t == CellType.ROOM) candidates.add(new int[]{x, y});
            }
        }
        if (!candidates.isEmpty()) {
            return candidates.get(Dice.rn2(candidates.size()));
        }
        // Room has no floor cells — search a wider area around the room
        for (int x = r.lx - 1; x <= r.hx + 1; x++) {
            for (int y = r.ly - 1; y <= r.hy + 1; y++) {
                if (!DungeonLevel.isOk(x, y)) continue;
                CellType t = level.cellAt(x, y).getType();
                if (t == CellType.ROOM) return new int[]{x, y};
            }
        }
        return null; // truly no floor found — caller will use stair-free fallback
    }

    private static void placeStairsInMaze(DungeonLevel level) {
        List<int[]> open=new ArrayList<>();
        for (int x=1;x<DungeonLevel.COLNO-1;x++)
            for (int y=1;y<DungeonLevel.ROWNO-1;y++)
                if (level.cellAt(x,y).getType()==CellType.CORR) open.add(new int[]{x,y});
        if (open.size()<2) return;
        int[] dn=open.remove(Dice.rn2(open.size())); int[] up=open.get(Dice.rn2(open.size()));
        setCell(level,dn[0],dn[1],CellType.STAIRS,'>'); level.setDnStair(dn[0],dn[1]);
        setCell(level,up[0],up[1],CellType.STAIRS,'<'); level.setUpStair(up[0],up[1]);
    }

    // ===================================================================
    // Trap placement — rooms only, max 1 trap per 2 rooms
    // ===================================================================

    private static void placeTraps(DungeonLevel level,int dlevel) {
        List<Room> rooms=level.getRooms();
        if (rooms.isEmpty()) return;
        int maxRoomsWithTraps=Math.max(1,rooms.size()/2),roomsWithTraps=0;
        for (Room room:rooms) {
            if (room.type==RoomType.VAULT||room.type.isShop()) continue; // no traps in special rooms
            if (roomsWithTraps>=maxRoomsWithTraps) break;
            if (!Dice.oneIn(3)) continue;
            int floorTiles=room.width()*room.height();
            int maxTraps=Math.max(1,floorTiles/20);
            int placed=0;
            for (int t=0;t<maxTraps&&t<2;t++) {
                int x=room.randomX(),y=room.randomY();
                if (level.trapAt(x,y)!=null) continue;
                if (level.cellAt(x,y).getType()!=CellType.ROOM) continue;
                TrapType kind=TrapType.fromValue(Dice.rn2(TrapType.TRAPNUM));
                level.getTraps().add(new Trap(x,y,kind)); placed++;
            }
            if (placed>0) roomsWithTraps++;
        }
    }

    // ===================================================================
    // Gold placement — skip special rooms (they get their own gold)
    // ===================================================================

    private static void placeGold(DungeonLevel level,int dlevel) {
        for (Room room:level.getRooms()) {
            if (room.type==RoomType.VAULT||room.type.isShop()) continue;
            if (Dice.oneIn(3)) {
                int x=room.randomX(),y=room.randomY();
                if (level.cellAt(x,y).getType()!=CellType.ROOM) continue;
                long amount=Dice.rn1(dlevel*50,25); // halved gold amounts
                level.getGold().add(new GoldPile(x,y,amount));
                level.cellAt(x,y).setScrsym('$');
            }
        }
    }

    // ===================================================================
    // Food placement — 1 to 2 food items per level in ordinary rooms
    // ===================================================================

    private static void placeFood(DungeonLevel level, int dlevel) {
        List<Room> rooms = level.getRooms();
        if (rooms.isEmpty()) return;

        int foodCount = 1 + Dice.rn2(2);
        int placed = 0;

        for (int attempt = 0; attempt < foodCount * 15 && placed < foodCount; attempt++) {
            Room r = rooms.get(Dice.rn2(rooms.size()));
            if (r.type == RoomType.VAULT || r.type.isShop()) continue;
            int x = r.randomX(), y = r.randomY();
            if (level.cellAt(x, y).getType() != CellType.ROOM) continue;
            if (level.goldAt(x, y) != null) continue;
            if (level.trapAt(x, y) != null) continue;

            // Random food: 2/3 chance of a classic food, 1/3 chance of Rotterdam street food
            int foodType;
            if (Dice.oneIn(3)) {
                // Street food (212-221)
                foodType = ItemTable.BARA + Dice.rn2(25);
            } else {
                foodType = ItemTable.FOOD_RATION + Dice.rn2(3);
            }
            level.cellAt(x, y).setScrsym(ItemTable.FOOD_SYM);
            long foodMarker = -(long)(foodType);
            level.getGold().add(new GoldPile(x, y, foodMarker));
            placed++;
        }
    }

    // ===================================================================
    // Special room placement
    // Odd floors  → Vault  (gold, Bank guard)
    // Even floors → Shop   (food + merchandise, shopkeeper)
    // ===================================================================

    /**
     * Adds one special room to the level:
     * <ul>
     *   <li>Floor 1, 3, 5, … (odd)  → Vault containing gold and a Bank guard.</li>
     *   <li>Floor 2, 4, 6, … (even) → Shop containing food, merchandise, and a shopkeeper.</li>
     * </ul>
     * The room is always 2–8 tiles wide and 2–8 tiles tall, always connected by
     * a corridor and door to the rest of the dungeon, and always lit.
     */
    private static void placeSpecialRoom(DungeonLevel level, int dlevel, GameState state) {
        boolean isVault = (dlevel % 2 == 1); // odd floor = Bank, even floor = shop

        // Try to carve a special room that doesn't overlap existing rooms
        Room sr = carveSpecialRoom(level);

        if (sr == null) {
            // Nothing could be carved in free space. Previously this returned
            // silently, so the floor simply had no Bank (or no Shop) at all —
            // the reported "NoBank" defect. Instead, promote an existing
            // ordinary room: every qualifying floor now always has its special
            // room, even on a densely packed map.
            sr = adoptOrdinaryRoom(level);
            if (sr == null) return;   // pathological: no ordinary room exists
            sr.type = isVault ? RoomType.VAULT : RoomType.GENERAL;
            for (int x = sr.lx; x <= sr.hx; x++)
                for (int y = sr.ly; y <= sr.hy; y++)
                    level.cellAt(x, y).setLit(true);
            sr.lit = true;
            if (isVault) fillVault(level, sr, dlevel, state);
            else         fillShop(level, sr, dlevel, state);
            return;   // already in level.getRooms() and already connected
        }

        sr.type = isVault ? RoomType.VAULT : RoomType.GENERAL;

        // Connect to the nearest existing room via a corridor and door
        List<Room> existing = level.getRooms();
        // Add special room to list so flood-fill sees it
        existing.add(sr);

        // Connect to nearest existing room (excluding the just-added sr)
        Room nearest = findNearestRoom(sr, existing, existing.size() - 1);
        if (nearest != null) {
            joinRoomsAligned(level, sr, nearest);
        }

        // Fill the special room
        if (isVault) {
            fillVault(level, sr, dlevel, state);
        } else {
            fillShop(level, sr, dlevel, state);
        }
    }

    /**
     * Picks an existing ordinary room to be converted into the special room.
     *
     * <p>Used only when no free space remains to carve a dedicated Bank or
     * Shop. Prefers the smallest ordinary room so the player loses as little
     * ordinary floor space as possible, and never adopts a room that already
     * holds a staircase — although with the corrected generation order the
     * stairs have not been placed yet when this runs.</p>
     *
     * @return the adopted room, or {@code null} if none is suitable
     */
    private static Room adoptOrdinaryRoom(DungeonLevel level) {
        Room best = null;
        int bestArea = Integer.MAX_VALUE;
        for (Room r : level.getRooms()) {
            if (r.type == RoomType.VAULT || r.type.isShop()) continue;
            if (containsStair(level, r)) continue;
            int area = r.width() * r.height();
            if (area < bestArea) { bestArea = area; best = r; }
        }
        return best;
    }

    /** True when the room contains either staircase. */
    private static boolean containsStair(DungeonLevel level, Room r) {
        int dx = level.getXDnStair(), dy = level.getYDnStair();
        int ux = level.getXUpStair(), uy = level.getYUpStair();
        return (dx >= r.lx && dx <= r.hx && dy >= r.ly && dy <= r.hy)
            || (ux >= r.lx && ux <= r.hx && uy >= r.ly && uy <= r.hy);
    }

    /**
     * Attempts to carve a special room (2–8 × 2–8) in empty space.
     * Tries multiple positions and returns the first successful room, or null.
     */
    /**
     * Carves a special room (2–8 × 2–8).
     * Tries 400 positions.  If nothing fits with the normal overlap margin,
     * falls back to a tighter 1-cell margin so the room ALWAYS appears on
     * every qualifying floor.
     */
    private static Room carveSpecialRoom(DungeonLevel level) {
        // Pass 1: normal XLIM/YLIM margin
        for (int attempt = 0; attempt < 400; attempt++) {
            int w = SR_MIN_W + Dice.rn2(SR_MAX_W - SR_MIN_W + 1);
            int h = SR_MIN_H + Dice.rn2(SR_MAX_H - SR_MIN_H + 1);
            int lx = 2 + Dice.rn2(Math.max(1, DungeonLevel.COLNO - w - 6));
            int ly = 2 + Dice.rn2(Math.max(1, DungeonLevel.ROWNO - h - 4));
            int hx = lx + w - 1, hy = ly + h - 1;
            if (hx + 2 >= DungeonLevel.COLNO - 1) continue;
            if (hy + 2 >= DungeonLevel.ROWNO - 1) continue;
            if (overlapCheck(level, lx, ly, hx, hy)) continue;
            return buildSpecialRoom(level, lx, ly, hx, hy);
        }
        // Pass 2: tight 1-cell margin — guarantees we always place the room
        for (int attempt = 0; attempt < 400; attempt++) {
            int w = 2 + Dice.rn2(3); // smaller room (2–4) to fit
            int h = 2 + Dice.rn2(3);
            int lx = 3 + Dice.rn2(Math.max(1, DungeonLevel.COLNO - w - 8));
            int ly = 3 + Dice.rn2(Math.max(1, DungeonLevel.ROWNO - h - 6));
            int hx = lx + w - 1, hy = ly + h - 1;
            if (hx + 2 >= DungeonLevel.COLNO - 1) continue;
            if (hy + 2 >= DungeonLevel.ROWNO - 1) continue;
            // Tight check: only 1-cell margin
            boolean clear = true;
            outer:
            for (int x = lx-1; x <= hx+1 && clear; x++)
                for (int y = ly-1; y <= hy+1 && clear; y++)
                    if (DungeonLevel.isOk(x,y) &&
                            level.cellAt(x,y).getType() != CellType.EMPTY) clear = false;
            if (!clear) continue;
            return buildSpecialRoom(level, lx, ly, hx, hy);
        }
        return null; // extremely unlikely — only if level is 100% packed
    }

    private static Room buildSpecialRoom(DungeonLevel level,
                                          int lx, int ly, int hx, int hy) {
        Room sr = new Room(lx, hx, ly, hy, RoomType.ORDINARY, true);
        carveRoom(level, sr);
        for (int x = lx; x <= hx; x++)
            for (int y = ly; y <= hy; y++)
                level.cellAt(x, y).setLit(true);
        return sr;
    }

    /** Finds the nearest room to {@code sr} from the list, skipping index {@code skipIdx}. */
    private static Room findNearestRoom(Room sr, List<Room> rooms, int skipIdx) {
        int srCx = sr.lx + sr.width() / 2, srCy = sr.ly + sr.height() / 2;
        Room nearest = null;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < rooms.size(); i++) {
            if (i == skipIdx) continue;
            Room r = rooms.get(i);
            int cx = r.lx + r.width() / 2, cy = r.ly + r.height() / 2;
            int dist = Math.abs(srCx - cx) + Math.abs(srCy - cy);
            if (dist < bestDist) { bestDist = dist; nearest = r; }
        }
        return nearest;
    }

    // ===================================================================
    // Vault — gold piles + Bank guard
    // ===================================================================

    /**
     * Fills a Bank room with gold piles and a Bank guard.
     *
     * <ul>
     *   <li>3–6 gold piles scattered across the floor.</li>
     *   <li>One Bank guard (Shopkeeper prototype, relabelled as guard)
     *       placed in the centre, peaceful until player takes gold.</li>
     *   <li>Guard is confined to the room bounds.</li>
     * </ul>
     */
    private static void fillVault(DungeonLevel level, Room r, int dlevel, GameState state) {
        // Gold piles — generous since this is a Bank
        int goldPiles = 3 + Dice.rn2(4);
        for (int i = 0; i < goldPiles; i++) {
            for (int attempt = 0; attempt < 20; attempt++) {
                int x = r.randomX(), y = r.randomY();
                if (level.cellAt(x, y).getType() != CellType.ROOM) continue;
                if (level.goldAt(x, y) != null) continue;
                long amount = Dice.rn1(dlevel * 200 + 100, 100);
                level.getGold().add(new GoldPile(x, y, amount));
                level.cellAt(x, y).setScrsym('$');
                break;
            }
        }

        // Vault guard — use SHOPKEEPER prototype (same stats), mark as guard
        if (state != null && state.getLevel() == level) {
            int gx = r.lx + r.width() / 2;
            int gy = r.ly + r.height() / 2;
            // Find free floor cell near centre
            gx = findFreeCellInRoom(level, r, gx, gy);
            if (gx >= 0) {
                int[] pos = decodeFreePos(level, r, gx);
                Monster guard = state.createMonster(MonsterPrototype.VAULTGUARD, pos[0], pos[1]);
                if (guard != null) {
                    guard.setPeaceful(true);
                    guard.setSleeping(false);
                    guard.setShopkeeper(false);
                    guard.setHomeRoom(r.lx, r.ly, r.hx, r.hy);
                }
            }
        }
    }

    // ===================================================================
    // Shop — food + merchandise + shopkeeper
    // ===================================================================

    /**
     * Fills a shop room with food, merchandise, and a shopkeeper.
     *
     * <ul>
     *   <li>2–3 food items (food rations or corpse food, displayed as '%').</li>
     *   <li>2–4 merchandise gold piles representing price-tagged items.</li>
     *   <li>One shopkeeper placed in the room, peaceful until player takes goods.</li>
     *   <li>Shopkeeper is confined to the room bounds.</li>
     * </ul>
     */
    private static void fillShop(DungeonLevel level, Room r, int dlevel, GameState state) {
        // Food items — always available in the shop
        int foodItems = 2 + Dice.rn2(2);
        for (int i = 0; i < foodItems; i++) {
            for (int attempt = 0; attempt < 25; attempt++) {
                int x = r.randomX(), y = r.randomY();
                if (level.cellAt(x, y).getType() != CellType.ROOM) continue;
                if (level.goldAt(x, y) != null) continue;
                // Alternate food ration and tripe ration
                int foodType = (i % 2 == 0) ? ItemTable.FOOD_RATION : ItemTable.FOOD_RATION + 1;
                long foodMarker = -(long)(foodType);
                level.getGold().add(new GoldPile(x, y, foodMarker));
                level.cellAt(x, y).setScrsym(ItemTable.FOOD_SYM);
                break;
            }
        }

        // Extra food items instead of merchandise gold — shops sell food ONLY
        // Add dead-monster corpse food items (displayed as '%') as extra stock
        int extraFood = 1 + Dice.rn2(3);
        for (int i = 0; i < extraFood; i++) {
            for (int attempt = 0; attempt < 25; attempt++) {
                int x = r.randomX(), y = r.randomY();
                if (level.cellAt(x, y).getType() != CellType.ROOM) continue;
                if (level.goldAt(x, y) != null) continue;
                // Use corpse food types: DEAD_HUMAN + small index for variety
                int corpseType = ItemTable.DEAD_HUMAN + Dice.rn2(5);
                if (corpseType >= ItemTable.NROFOBJECTS) corpseType = ItemTable.FOOD_RATION;
                long foodMarker = -(long)(corpseType);
                level.getGold().add(new GoldPile(x, y, foodMarker));
                level.cellAt(x, y).setScrsym(ItemTable.FOOD_SYM);
                break;
            }
        }

        // Shopkeeper
        if (state != null && state.getLevel() == level) {
            int sx = r.lx + r.width() / 2, sy = r.ly + r.height() / 2;
            int enc = findFreeCellInRoom(level, r, sx, sy);
            if (enc >= 0) {
                int[] pos = decodeFreePos(level, r, enc);
                Monster sk = state.createMonster(MonsterPrototype.SHOPKEEPER, pos[0], pos[1]);
                if (sk != null) {
                    sk.setPeaceful(true);
                    sk.setSleeping(false);
                    sk.setShopkeeper(true);
                    sk.setHomeRoom(r.lx, r.ly, r.hx, r.hy);
                }
            }
        }
    }

    /**
     * Finds a free (ROOM type, empty scrsym '.') cell in the room,
     * searching outward from (prefX, prefY).
     * Returns an encoded index into the room's cells, or -1 if none found.
     */
    private static int findFreeCellInRoom(DungeonLevel level, Room r, int prefX, int prefY) {
        // Accept any ROOM cell that isn't a wall/door — floor ('.') or gold ('$') are fine
        java.util.function.Predicate<hack.model.Cell> isOk =
            c -> c.getType() == CellType.ROOM;
        // Try preferred position first
        if (DungeonLevel.isOk(prefX, prefY) && isOk.test(level.cellAt(prefX, prefY))) {
            return (prefX - r.lx) * r.height() + (prefY - r.ly);
        }
        // Scan all cells
        for (int x = r.lx; x <= r.hx; x++)
            for (int y = r.ly; y <= r.hy; y++)
                if (DungeonLevel.isOk(x, y) && isOk.test(level.cellAt(x, y)))
                    return (x - r.lx) * r.height() + (y - r.ly);
        return -1;
    }

    private static int[] decodeFreePos(DungeonLevel level, Room r, int enc) {
        int h = r.height();
        int x = r.lx + enc / h;
        int y = r.ly + enc % h;
        return new int[]{x, y};
    }

    // ===================================================================
    // Dog companion
    // ===================================================================

    /**
     * Spawns a little dog near the player — called on new game (level 1)
     * and when the dog follows through stairs.
     */
    public static void spawnDogNearPlayer(GameState state) {
        Player p = state.getPlayer();
        DungeonLevel level = state.getLevel();
        if (level == null) return;
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
        for (int[] d : dirs) {
            int nx = p.getX() + d[0], ny = p.getY() + d[1];
            if (!DungeonLevel.isOk(nx, ny)) continue;
            CellType t = level.cellAt(nx, ny).getType();
            if (t == CellType.ROOM || t == CellType.CORR) {
                if (state.monsterAt(nx, ny) == null) {
                    Monster dog = state.createMonster(MonsterPrototype.LITTLE_DOG, nx, ny);
                    if (dog != null) {
                        dog.setPeaceful(true);
                        dog.setSleeping(false);
                    }
                    return;
                }
            }
        }
    }

    // ===================================================================
    // Utility
    // ===================================================================

    static void setCell(DungeonLevel level,int x,int y,CellType type,char symbol) {
        if (!DungeonLevel.isOk(x,y)) return;
        level.cellAt(x,y).setType(type);
        level.cellAt(x,y).setScrsym(symbol);
    }
}
