/** Immutable map templates shared by the local session and LAN host, not live worlds. */
final class MapStorage {
    static final String DEFAULT_ID="practice";
    static final class MapData {
        final String id;
        final double edge=10;
        private final double[][] targets={{-4,-3},{0,-3},{4,-3},{0,4}};
        MapData(String id){this.id=id;}
        void populate(CombatSystem combat){
            combat.clearDummies();
            for(double[] point:targets)combat.dummies.add(new CombatSystem.Dummy(point[0],point[1]));
            combat.setDummyHealth(combat.dummyMaxHealth);
        }
        double spawnX(int player){return player==0?0:-6+((player-1)%4)*4;}
        double spawnZ(int player){return player==0?0:5+((player-1)/4)*2;}
    }
    private static final MapData PRACTICE=new MapData(DEFAULT_ID);
    static MapData load(String id){
        if(!DEFAULT_ID.equals(id))throw new IllegalArgumentException("Unknown map: "+id);
        return PRACTICE;
    }
    private MapStorage(){}
}
