import java.io.*;
import java.nio.file.*;
import java.util.*;
/** Templates create independent sessions; saved maps automatically join the pool. */
final class MapStorage {
    static final String DEFAULT_ID="practice";
    static final Path DIRECTORY=Paths.get(System.getProperty("camera.maps","server-storage/maps")),ASSETS=Paths.get("server-storage","assets");
    static final class MapData {
        final String id;
        final double edge;final MapDefinition definition;
        MapData(String id){this(id,practice());}
        MapData(String id,MapDefinition definition){this.id=id;this.definition=definition;edge=Math.max(definition.width,definition.depth)/2;}
        void populate(CombatSystem combat){
            combat.map=definition;combat.clearDummies();
            for(MapDefinition.Spawn s:definition.targets){CombatSystem.Dummy d=new CombatSystem.Dummy(s.x,s.z);d.lift=d.spawnY=s.y;combat.dummies.add(d);}
            combat.setDummyHealth(combat.dummyMaxHealth);
        }
        double spawnX(int player){return player==0?0:definition.spawn(player-1).x;}
        double spawnZ(int player){return player==0?0:definition.spawn(player-1).z;}
    }
    static MapDefinition practice(){MapDefinition m=new MapDefinition();m.name="Practice";for(int i=0;i<8;i++)m.spawns.add(new MapDefinition.Spawn(-6+(i%4)*4,0,5+(i/4)*2,1));for(double[] p:new double[][]{{-4,-3},{0,-3},{4,-3},{0,4}})m.targets.add(new MapDefinition.Spawn(p[0],0,p[1],1));return m;}
    static MapData load(String id){
        if(!DEFAULT_ID.equals(id))throw new IllegalArgumentException("Unknown map: "+id);
        return new MapData(id);
    }
    static final class Choice {
        final String label;final Path file;final boolean random;
        Choice(String label,Path file,boolean random){this.label=label;this.file=file;this.random=random;}
        MapDefinition load() throws IOException {return random?choose():file==null?practice():MapDefinition.read(file);}
        public String toString(){return label;}
    }
    static java.util.List<Choice> choices(Path directory) throws IOException {
        java.util.List<Choice> result=new ArrayList<>();result.add(new Choice("Random",null,true));result.add(new Choice("Practice (built-in)",null,false));
        if(Files.isDirectory(directory))try(java.util.stream.Stream<Path> files=Files.list(directory)){
            files.filter(p->Files.isRegularFile(p)&&p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".map")).sorted().forEach(p->result.add(new Choice(p.getFileName().toString(),p,false)));
        }return result;
    }
    static MapDefinition choose(){return choose(DIRECTORY,new Random());}
    static MapDefinition choose(Path directory,Random random){
        java.util.List<MapDefinition> maps=new ArrayList<>();maps.add(practice());
        if(Files.isDirectory(directory))try(java.util.stream.Stream<Path> files=Files.list(directory)){
            files.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".map")).sorted().forEach(p->{try{maps.add(MapDefinition.read(p));}catch(IOException e){System.err.println("Skipping map "+p.getFileName()+": "+e.getMessage());}});
        }catch(IOException e){System.err.println("Cannot list maps: "+e.getMessage());}
        return maps.get(random.nextInt(maps.size()));
    }
    private MapStorage(){}
}
