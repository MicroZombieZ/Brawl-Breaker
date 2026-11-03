import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Versioned, self-contained map/asset data. Objects use bottom-center coordinates. */
final class MapDefinition {
    enum Shape { BOX, SPHERE, CYLINDER, WEDGE }
    enum Plane { RECTANGLE, ELLIPSE }
    static final int MAX_OBJECTS=128;
    String name="New map";double width=20,depth=20;Plane plane=Plane.RECTANGLE;int color=0x404c56;
    final List<Part> objects=new ArrayList<>();final List<Spawn> spawns=new ArrayList<>(),targets=new ArrayList<>();
    static final class Part {
        String name="Box";Shape shape=Shape.BOX;double x,y,z,w=2,h=2,d=2,yaw,vx,vy,vz;
        boolean anchored=true,collision=true;int color=0x779db4;
        Part copy(){Part p=new Part();p.name=name;p.shape=shape;p.x=x;p.y=y;p.z=z;p.w=w;p.h=h;p.d=d;p.yaw=yaw;p.vx=vx;p.vy=vy;p.vz=vz;p.anchored=anchored;p.collision=collision;p.color=color;return p;}
        double[] bounds(){double c=Math.abs(Math.cos(Math.toRadians(yaw))),s=Math.abs(Math.sin(Math.toRadians(yaw)));double a=(c*w+s*d)/2,b=(s*w+c*d)/2;return new double[]{x-a,y,z-b,x+a,y+h,z+b};}
        public String toString(){return name+" ["+shape+(anchored?"":", loose")+"]";}
    }
    static final class Spawn {
        double x,y,z;int team;
        Spawn(double x,double y,double z,int team){this.x=x;this.y=y;this.z=z;this.team=team;}
        public String toString(){return "Team "+team+" spawn ("+x+", "+y+", "+z+")";}
    }
    MapDefinition copy(){try{return decode(encode());}catch(IOException e){throw new IllegalStateException(e);}}
    Spawn spawn(int index){List<Spawn> team=new ArrayList<>();for(Spawn s:spawns)if(s.team==1)team.add(s);return team.isEmpty()?new Spawn(0,0,0,1):team.get(Math.floorMod(index,team.size()));}
    void validate()throws IOException{
        if(name==null||name.isBlank()||name.length()>80)throw new IOException("Map name must contain 1–80 characters.");range(width,4,200);range(depth,4,200);
        if(objects.size()>128||spawns.size()>64||targets.size()>64)throw new IOException("Limit: 128 objects, 64 spawns, 64 targets.");
        for(Part p:objects){if(p.name==null||p.name.length()>80)throw new IOException("Object name too long.");range(p.x,-200,200);range(p.y,0,200);range(p.z,-200,200);range(p.w,.1,100);range(p.h,.1,100);range(p.d,.1,100);range(p.yaw,-360,360);range(p.vx,-50,50);range(p.vy,-50,50);range(p.vz,-50,50);}
        boolean teamOne=false;for(Spawn s:spawns){range(s.x,-width/2+.71,width/2-.71);range(s.z,-depth/2+.71,depth/2-.71);range(s.y,0,200);range(s.team,1,8);
            if(plane==Plane.ELLIPSE&&Math.pow(s.x/(width/2-.71),2)+Math.pow(s.z/(depth/2-.71),2)>1.000001)throw new IOException("Spawn outside ellipse.");if(s.team==1)teamOne=true;
            for(Part p:objects)if(p.collision){double[] b=p.bounds();if(s.x+.5>b[0]&&s.x-.5<b[3]&&s.y+1>b[1]+.001&&s.y<b[4]-.001&&s.z+.5>b[2]&&s.z-.5<b[5])throw new IOException("Spawn overlaps solid object: "+p.name);}}
        if(!teamOne)throw new IOException("Add at least one Team 1 spawn.");for(Spawn s:targets){range(s.x,-200,200);range(s.z,-200,200);range(s.y,0,200);}
    }
    private static void range(double n,double min,double max)throws IOException{if(!Double.isFinite(n)||n<min||n>max)throw new IOException("Value outside "+min+" to "+max+".");}
    byte[] encode()throws IOException{
        validate();Properties p=new Properties();p.setProperty("format","1");p.setProperty("name",name);p.setProperty("plane",plane.name());p.setProperty("width",""+width);p.setProperty("depth",""+depth);p.setProperty("color",""+color);p.setProperty("objects",""+objects.size());
        for(int i=0;i<objects.size();i++){Part o=objects.get(i);String k="object."+i+".";p.setProperty(k+"name",o.name);p.setProperty(k+"shape",o.shape.name());p.setProperty(k+"values",o.x+","+o.y+","+o.z+","+o.w+","+o.h+","+o.d+","+o.yaw+","+o.vx+","+o.vy+","+o.vz);p.setProperty(k+"anchored",""+o.anchored);p.setProperty(k+"collision",""+o.collision);p.setProperty(k+"color",""+o.color);}
        writePoints(p,"spawn",spawns);writePoints(p,"target",targets);ByteArrayOutputStream bytes=new ByteArrayOutputStream();p.store(bytes,"Map Studio document");return bytes.toByteArray();
    }
    private static void writePoints(Properties p,String key,List<Spawn> list){p.setProperty(key+"s",""+list.size());for(int i=0;i<list.size();i++){Spawn s=list.get(i);p.setProperty(key+"."+i,s.x+","+s.y+","+s.z+","+s.team);}}
    static MapDefinition decode(byte[] bytes)throws IOException{
        if(bytes.length>180000)throw new IOException("Map document too large.");
        try{Properties p=new Properties();p.load(new ByteArrayInputStream(bytes));if(!"1".equals(p.getProperty("format")))throw new IOException("Unsupported map format.");MapDefinition m=new MapDefinition();m.name=p.getProperty("name");m.plane=Plane.valueOf(p.getProperty("plane"));m.width=Double.parseDouble(p.getProperty("width"));m.depth=Double.parseDouble(p.getProperty("depth"));m.color=Integer.parseInt(p.getProperty("color"));int count=count(p,"objects",128);
            for(int i=0;i<count;i++){String k="object."+i+".";Part o=new Part();o.name=p.getProperty(k+"name");o.shape=Shape.valueOf(p.getProperty(k+"shape"));double[] v=values(p.getProperty(k+"values"),10);o.x=v[0];o.y=v[1];o.z=v[2];o.w=v[3];o.h=v[4];o.d=v[5];o.yaw=v[6];o.vx=v[7];o.vy=v[8];o.vz=v[9];o.anchored=Boolean.parseBoolean(p.getProperty(k+"anchored"));o.collision=Boolean.parseBoolean(p.getProperty(k+"collision"));o.color=Integer.parseInt(p.getProperty(k+"color"));m.objects.add(o);}
            readPoints(p,"spawn",m.spawns);readPoints(p,"target",m.targets);m.validate();return m;
        }catch(RuntimeException e){throw new IOException("Invalid map document: "+e.getMessage(),e);}
    }
    private static int count(Properties p,String key,int max)throws IOException{int n=Integer.parseInt(p.getProperty(key));if(n<0||n>max)throw new IOException("Invalid "+key+" count.");return n;}
    private static double[] values(String value,int count)throws IOException{String[] a=value.split(",");if(a.length!=count)throw new IOException("Invalid coordinate list.");double[] v=new double[count];for(int i=0;i<count;i++)v[i]=Double.parseDouble(a[i]);return v;}
    private static void readPoints(Properties p,String key,List<Spawn> list)throws IOException{int n=count(p,key+"s",64);for(int i=0;i<n;i++){double[] v=values(p.getProperty(key+"."+i),4);if(v[3]!=(int)v[3])throw new IOException("Team must be an integer.");list.add(new Spawn(v[0],v[1],v[2],(int)v[3]));}}
    static MapDefinition read(Path path)throws IOException{if(Files.size(path)>180000)throw new IOException("Map too large.");return decode(Files.readAllBytes(path));}
    void save(Path path)throws IOException{byte[] bytes=encode();Files.createDirectories(path.toAbsolutePath().getParent());Path temp=Files.createTempFile(path.toAbsolutePath().getParent(),"map-",".tmp");try{Files.write(temp,bytes);try{Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}}
}
