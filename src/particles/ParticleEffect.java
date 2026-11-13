import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;

/** Portable cosmetic effect: settings and optional sprite in a single .pfx archive. */
final class ParticleEffect {
    enum Pattern { BURST, SPARKLE, DUST_EXPLOSION, CONE, RING, FOUNTAIN }
    Pattern pattern = Pattern.SPARKLE;
    ParticleSystem.Shape shape = ParticleSystem.Shape.SPARK;
    Color color = new Color(255, 198, 91);
    BufferedImage image;
    double aggression=1, speed=3, range=.15, spread=65, heading=0, elevation=45,
            size=.1, lifetime=.8, gravity=2, growth=0, spin=3, opacity=1, interval=.8;
    double speedVariation=.3, sizeVariation=.25, lifetimeVariation=.25,
            drag=0, windX=0, windY=0, windZ=0, emissionDuration=0,
            fadeIn=0, fadeOut=1, bounce=.35;
    boolean groundCollision=true;
    int count=40;
    private static final String[] EXTRA={"speedVariation","sizeVariation","lifetimeVariation","drag","windX","windY","windZ","emissionDuration","fadeIn","fadeOut","bounce"};
    double[] extraValues(){return new double[]{speedVariation,sizeVariation,lifetimeVariation,drag,windX,windY,windZ,emissionDuration,fadeIn,fadeOut,bounce};}
    private void extraValues(double[] v){speedVariation=v[0];sizeVariation=v[1];lifetimeVariation=v[2];drag=v[3];windX=v[4];windY=v[5];windZ=v[6];emissionDuration=v[7];fadeIn=v[8];fadeOut=v[9];bounce=v[10];}

    static ParticleEffect preset(Pattern pattern) {
        ParticleEffect e=new ParticleEffect();e.pattern=pattern;
        switch(pattern) {
            case DUST_EXPLOSION:
                e.shape=ParticleSystem.Shape.DUST;e.color=ParticleSystem.DUST;
                e.size=.22;e.speed=2;e.gravity=.3;e.growth=.3;e.lifetime=1.4;e.spread=85;break;
            case BURST: e.shape=ParticleSystem.Shape.SHARD;e.spread=180;e.gravity=5;break;
            case CONE: e.spread=25;e.elevation=20;e.shape=ParticleSystem.Shape.SHARD;break;
            case RING: e.elevation=0;e.range=.5;e.gravity=0;e.shape=ParticleSystem.Shape.DUST;break;
            case FOUNTAIN: e.elevation=90;e.spread=18;e.speed=5;e.gravity=6;e.lifetime=2;break;
            default: e.spread=180;e.gravity=.5;e.color=new Color(167,219,255);break;
        }
        return e;
    }

    void validate() throws IOException {
        bounded(aggression,.1,3,"aggressiveness");bounded(speed,0,20,"speed");
        bounded(range,0,5,"range");bounded(spread,0,180,"spread");bounded(heading,-180,180,"heading");
        bounded(elevation,-90,90,"elevation");bounded(size,.02,1,"size");
        bounded(lifetime,.1,5,"lifetime");bounded(gravity,-10,20,"gravity");
        bounded(growth,-1,2,"growth");bounded(spin,0,20,"spin");
        bounded(opacity,0,1,"opacity");bounded(interval,.1,5,"repeat interval");
        bounded(count,1,400,"count");
        double[] extra=extraValues();
        for(int i=0;i<extra.length;i++) bounded(extra[i],i>=4&&i<=6?-20:0,
                i==3?10:i>=4&&i<=6?20:i==7?5:i==2?.95:1,EXTRA[i]);
        if(image!=null && (image.getWidth()>2048 || image.getHeight()>2048))
            throw new IOException("Images must be at most 2048 x 2048 pixels.");
    }
    private static void bounded(double v,double low,double high,String name) throws IOException {
        if(!Double.isFinite(v)||v<low||v>high) throw new IOException("Invalid "+name+" ("+low+" to "+high+").");
    }
    static BufferedImage readImage(File file) throws IOException {
        if(file.length()>16*1024*1024) throw new IOException("Image file must be under 16 MB.");
        try(InputStream in=Files.newInputStream(file.toPath())) { return readImage(in); }
    }
    private static BufferedImage readImage(InputStream input) throws IOException {
        try(ImageInputStream in=ImageIO.createImageInputStream(input)) {
            if(in==null) throw new IOException("Cannot read image.");
            Iterator<ImageReader> readers=ImageIO.getImageReaders(in);
            if(!readers.hasNext()) throw new IOException("Unsupported image. Use PNG, JPEG, or GIF.");
            ImageReader reader=readers.next();
            try {
                reader.setInput(in);
                if(reader.getWidth(0)>2048 || reader.getHeight(0)>2048)
                    throw new IOException("Images must be at most 2048 x 2048 pixels.");
                return reader.read(0);
            } finally { reader.dispose(); }
        }
    }
    void save(Path path) throws IOException {
        validate();
        Properties p=new Properties();p.setProperty("version","1");
        p.setProperty("pattern",pattern.name());p.setProperty("shape",shape.name());
        p.setProperty("color",Integer.toString(color.getRGB()));p.setProperty("count",Integer.toString(count));
        String[] names={"aggression","speed","range","spread","heading","elevation","size","lifetime","gravity","growth","spin","opacity","interval"};
        double[] values={aggression,speed,range,spread,heading,elevation,size,lifetime,gravity,growth,spin,opacity,interval};
        for(int i=0;i<names.length;i++) p.setProperty(names[i],Double.toString(values[i]));
        double[] extra=extraValues();
        for(int i=0;i<EXTRA.length;i++)p.setProperty(EXTRA[i],Double.toString(extra[i]));
        p.setProperty("groundCollision",Boolean.toString(groundCollision));
        Path absolute=path.toAbsolutePath();Files.createDirectories(absolute.getParent());
        Path temporary=Files.createTempFile(absolute.getParent(),"particle-",".tmp");
        try {
            try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(temporary))) {
                out.putNextEntry(new ZipEntry("effect.properties"));p.store(out,"CameraUpdate particle effect");out.closeEntry();
                if(image!=null) { out.putNextEntry(new ZipEntry("sprite.png"));ImageIO.write(image,"png",out);out.closeEntry(); }
            }
            Files.move(temporary,absolute,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
    static ParticleEffect load(Path path) throws IOException {
        try(ZipFile zip=new ZipFile(path.toFile())) {
            ZipEntry settings=zip.getEntry("effect.properties");
            if(settings==null || settings.getSize()>16384) throw new IOException("Missing or oversized effect settings.");
            Properties p=new Properties();
            try(InputStream in=zip.getInputStream(settings)) { p.load(new ByteArrayInputStream(readLimited(in,16384))); }
            if(!"1".equals(p.getProperty("version"))) throw new IOException("Unsupported particle effect version.");
            ParticleEffect e=new ParticleEffect();
            try {
                e.pattern=Pattern.valueOf(p.getProperty("pattern"));e.shape=ParticleSystem.Shape.valueOf(p.getProperty("shape"));
                e.color=new Color(Integer.parseInt(p.getProperty("color")),true);e.count=Integer.parseInt(p.getProperty("count"));
                e.aggression=number(p,"aggression");e.speed=number(p,"speed");e.range=number(p,"range");
                e.spread=number(p,"spread");e.heading=number(p,"heading");e.elevation=number(p,"elevation");
                e.size=number(p,"size");e.lifetime=number(p,"lifetime");e.gravity=number(p,"gravity");
                e.growth=number(p,"growth");e.spin=number(p,"spin");e.opacity=number(p,"opacity");e.interval=number(p,"interval");
            } catch(RuntimeException ex) { throw new IOException("Invalid particle settings.",ex); }
            try {
                double[] extra=e.extraValues();
                for(int i=0;i<EXTRA.length;i++)if(p.containsKey(EXTRA[i]))extra[i]=number(p,EXTRA[i]);
                e.extraValues(extra);
                String collision=p.getProperty("groundCollision","true");
                if(!collision.equals("true")&&!collision.equals("false"))throw new IllegalArgumentException("groundCollision");
                e.groundCollision=Boolean.parseBoolean(collision);
            }catch(RuntimeException ex){throw new IOException("Invalid advanced particle settings.",ex);}
            ZipEntry sprite=zip.getEntry("sprite.png");
            if(sprite!=null) try(InputStream in=zip.getInputStream(sprite)) {
                e.image=readImage(new ByteArrayInputStream(readLimited(in,16*1024*1024)));
            }
            e.validate();return e;
        }
    }
    private static byte[] readLimited(InputStream in,int limit) throws IOException {
        byte[] data=in.readNBytes(limit+1);
        if(data.length>limit) throw new IOException("Effect entry is too large.");return data;
    }
    private static double number(Properties p,String key) { return Double.parseDouble(p.getProperty(key)); }
}
