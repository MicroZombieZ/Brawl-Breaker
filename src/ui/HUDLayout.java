import java.awt.*;
import java.awt.geom.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Resolution-relative HUD placement, shared by the game and placement editor. */
final class HUDLayout {
    static final int WIDTH=1000,HEIGHT=700;
    enum Piece {
        HEALTH(250,18),STAMINA(250,18),CLASS(240,38),ABILITIES(240,142),CROSSHAIR(24,24),LAN(600,22),DEBUG(650,48);
        final int width,height;
        Piece(int width,int height){this.width=width;this.height=height;}
        double defaultX(int w){switch(this){case CLASS:return Math.max(0,w-262);case ABILITIES:return Math.max(315,w-265);case CROSSHAIR:return w/2.0-12;default:return 22;}}
        double defaultY(int h){switch(this){case HEALTH:return h-102;case STAMINA:return h-47;case CLASS:return 4;case ABILITIES:return h-210;case CROSSHAIR:return h/2.0-12;case LAN:return 111;default:return 62;}}
        Rectangle2D bounds(){return this==HEALTH||this==STAMINA?new Rectangle2D.Double(0,-25,width,height+25):new Rectangle2D.Double(0,0,width,height);}
    }
    static final class Placement {
        double x,y,scaleX=1,scaleY=1,rotation,opacity=1,fontScale=1;
        boolean positioned,visible=true,text=true,defaults=true;
        Color color;
        Placement(Piece piece){x=piece.defaultX(WIDTH);y=piece.defaultY(HEIGHT);}
    }
    final Map<Piece,Placement> placements=new EnumMap<>(Piece.class);
    HUDLayout(){for(Piece p:Piece.values())placements.put(p,new Placement(p));}
    Placement get(Piece piece){return placements.get(piece);}
    AffineTransform transform(Piece piece,int width,int height){
        Placement p=get(piece);double x=p.positioned?p.x*width/WIDTH:piece.defaultX(width),y=p.positioned?p.y*height/HEIGHT:piece.defaultY(height);
        AffineTransform t=new AffineTransform();t.translate(x+piece.width*p.scaleX/2,y+piece.height*p.scaleY/2);
        t.rotate(Math.toRadians(p.rotation));t.scale(p.scaleX,p.scaleY);t.translate(-piece.width/2.0,-piece.height/2.0);return t;
    }
    Graphics2D graphics(Graphics2D g,Piece piece,int width,int height){
        Placement p=get(piece);if(!p.visible||p.opacity<=0)return null;
        Graphics2D copy=(Graphics2D)g.create();copy.transform(transform(piece,width,height));copy.setComposite(AlphaComposite.SrcOver.derive((float)p.opacity));return copy;
    }
    byte[] encode() throws IOException {
        Properties props=new Properties();props.setProperty("version","1");
        for(Piece piece:Piece.values()){Placement p=get(piece);String k=piece+".";
            props.setProperty(k+"x",""+p.x);props.setProperty(k+"y",""+p.y);props.setProperty(k+"scaleX",""+p.scaleX);props.setProperty(k+"scaleY",""+p.scaleY);
            props.setProperty(k+"rotation",""+p.rotation);props.setProperty(k+"opacity",""+p.opacity);props.setProperty(k+"fontScale",""+p.fontScale);
            props.setProperty(k+"positioned",""+p.positioned);props.setProperty(k+"visible",""+p.visible);props.setProperty(k+"text",""+p.text);props.setProperty(k+"defaults",""+p.defaults);
            if(p.color!=null)props.setProperty(k+"color",""+p.color.getRGB());}
        ByteArrayOutputStream out=new ByteArrayOutputStream();props.store(out,"HUD Studio layout; 1000 x 700 reference coordinates");return out.toByteArray();
    }
    static HUDLayout decode(byte[] bytes) throws IOException {
        if(bytes.length>65536)throw new IOException("HUD layout is too large.");Properties props=new Properties();props.load(new ByteArrayInputStream(bytes));
        if(!"1".equals(props.getProperty("version")))throw new IOException("Unsupported HUD layout version.");HUDLayout layout=new HUDLayout();
        try{for(Piece piece:Piece.values()){Placement p=layout.get(piece);String k=piece+".";
            p.x=number(props,k+"x",p.x,-2000,3000);p.y=number(props,k+"y",p.y,-1400,2100);
            p.scaleX=number(props,k+"scaleX",1,.1,5);p.scaleY=number(props,k+"scaleY",1,.1,5);
            p.rotation=number(props,k+"rotation",0,-180,180);p.opacity=number(props,k+"opacity",1,0,1);p.fontScale=number(props,k+"fontScale",1,.5,3);
            p.positioned=Boolean.parseBoolean(props.getProperty(k+"positioned","false"));p.visible=Boolean.parseBoolean(props.getProperty(k+"visible","true"));
            p.text=Boolean.parseBoolean(props.getProperty(k+"text","true"));p.defaults=Boolean.parseBoolean(props.getProperty(k+"defaults","true"));
            if(props.containsKey(k+"color"))p.color=new Color(Integer.parseInt(props.getProperty(k+"color")));}
        }catch(RuntimeException ex){throw new IOException("Invalid HUD layout settings.",ex);}return layout;
    }
    private static double number(Properties p,String key,double fallback,double min,double max){double value=Double.parseDouble(p.getProperty(key,""+fallback));
        if(!Double.isFinite(value)||value<min||value>max)throw new IllegalArgumentException(key);return value;}
    static HUDLayout load(Path path){try{if(!Files.exists(path))return new HUDLayout();if(Files.size(path)>65536)throw new IOException("HUD layout is too large.");return decode(Files.readAllBytes(path));}
        catch(IOException ex){System.err.println("Skipping "+path+": "+ex.getMessage());return new HUDLayout();}}
    void save(Path path) throws IOException {byte[] bytes=encode();decode(bytes);Path absolute=path.toAbsolutePath();Files.createDirectories(absolute.getParent());Path temp=Files.createTempFile(absolute.getParent(),"hud-",".tmp");
        try{Files.write(temp,bytes);Files.move(temp,absolute,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}}
}
