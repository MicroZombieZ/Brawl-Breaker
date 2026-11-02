import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;

/** Layered raster document with bounded undo/redo and a portable .uidraw format. */
final class UIDrawing {
    static final int MAX_LAYERS=16,MAX_SIZE=1024,HISTORY_BYTES=32*1024*1024;
    static final class Layer {
        String name;boolean visible=true;float opacity=1;int x,y;
        BufferedImage image;
        Layer(String name,int w,int h){this.name=name;image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);}
        @Override public String toString(){return (visible?"[on] ":"[off] ")+name;}
    }
    int width,height,selected;
    final java.util.List<Layer> layers=new ArrayList<>(); // bottom to top
    private final Deque<byte[]> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    UIDrawing(int width,int height){
        if(width<16||height<16||width>MAX_SIZE||height>MAX_SIZE)throw new IllegalArgumentException("Canvas dimensions must be 16-1024.");
        this.width=width;this.height=height;layers.add(new Layer("Layer 1",width,height));
    }
    Layer active(){return layers.get(selected);}
    /** Four-connected, exact-color bucket fill clipped to the visible canvas. */
    int floodFill(int canvasX,int canvasY,Color color,float opacity) throws IOException {
        Layer layer=active();int x=canvasX-layer.x,y=canvasY-layer.y;
        int minX=Math.max(0,-layer.x),minY=Math.max(0,-layer.y);
        int maxX=Math.min(width,width-layer.x),maxY=Math.min(height,height-layer.y);
        if(!layer.visible||opacity<=0||x<minX||y<minY||x>=maxX||y>=maxY)return 0;
        int[] pixels=layer.image.getRGB(0,0,width,height,null,0,width);int seed=y*width+x,target=fillColor(pixels[seed]);
        BufferedImage sample=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);sample.setRGB(0,0,pixels[seed]);
        Graphics2D g=sample.createGraphics();g.setComposite(AlphaComposite.SrcOver.derive(opacity));g.setColor(color);g.fillRect(0,0,1,1);g.dispose();
        int replacement=sample.getRGB(0,0);if(fillColor(replacement)==target)return 0;
        int[] queue=new int[width*height];int head=0,tail=0;queue[tail++]=seed;pixels[seed]=replacement;
        while(head<tail){int index=queue[head++],px=index%width,py=index/width;
            if(px>minX&&fillColor(pixels[index-1])==target){pixels[index-1]=replacement;queue[tail++]=index-1;}
            if(px+1<maxX&&fillColor(pixels[index+1])==target){pixels[index+1]=replacement;queue[tail++]=index+1;}
            if(py>minY&&fillColor(pixels[index-width])==target){pixels[index-width]=replacement;queue[tail++]=index-width;}
            if(py+1<maxY&&fillColor(pixels[index+width])==target){pixels[index+width]=replacement;queue[tail++]=index+width;}
        }
        checkpoint();layer.image.setRGB(0,0,width,height,pixels,0,width);return tail;
    }
    private static int fillColor(int pixel){return (pixel>>>24)==0?0:pixel;}
    BufferedImage flatten(){
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        for(Layer layer:layers)if(layer.visible){g.setComposite(AlphaComposite.SrcOver.derive(layer.opacity));g.drawImage(layer.image,layer.x,layer.y,null);}
        g.dispose();return image;
    }
    void checkpoint() throws IOException {undo.addLast(encode());redo.clear();trim();}
    private void trim(){
        while(undo.size()+redo.size()>20 || historyBytes()>HISTORY_BYTES){
            if(undo.size()>1)undo.removeFirst();else if(redo.size()>1)redo.removeFirst();else break;
        }
    }
    private long historyBytes(){long size=0;for(byte[] b:undo)size+=b.length;for(byte[] b:redo)size+=b.length;return size;}
    boolean canUndo(){return !undo.isEmpty();}boolean canRedo(){return !redo.isEmpty();}
    void undo() throws IOException {if(canUndo()){byte[] current=encode();replace(decode(undo.removeLast()));redo.addLast(current);trim();}}
    void redo() throws IOException {if(canRedo()){byte[] current=encode();replace(decode(redo.removeLast()));undo.addLast(current);trim();}}
    private void replace(UIDrawing drawing){width=drawing.width;height=drawing.height;selected=drawing.selected;layers.clear();layers.addAll(drawing.layers);}
    void addLayer(String name) throws IOException {
        if(layers.size()>=MAX_LAYERS)throw new IOException("Maximum 16 layers.");
        checkpoint();layers.add(new Layer(name,width,height));selected=layers.size()-1;
    }
    void duplicate() throws IOException {
        if(layers.size()>=MAX_LAYERS)throw new IOException("Maximum 16 layers.");
        checkpoint();Layer old=active(),copy=new Layer(old.name+" copy",width,height);
        Graphics2D g=copy.image.createGraphics();g.drawImage(old.image,0,0,null);g.dispose();
        copy.x=old.x;copy.y=old.y;copy.visible=old.visible;copy.opacity=old.opacity;layers.add(++selected,copy);
    }
    void importImage(BufferedImage source) throws IOException {
        addLayer("Imported image");double scale=Math.min(1,Math.min((double)width/source.getWidth(),(double)height/source.getHeight()));
        int w=Math.max(1,(int)(source.getWidth()*scale)),h=Math.max(1,(int)(source.getHeight()*scale));
        Graphics2D g=active().image.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source,(width-w)/2,(height-h)/2,w,h,null);g.dispose();
    }
    byte[] encode() throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream out=new ZipOutputStream(bytes)){
            Properties p=new Properties();p.setProperty("version","1");p.setProperty("width",""+width);p.setProperty("height",""+height);
            p.setProperty("count",""+layers.size());p.setProperty("selected",""+selected);
            for(int i=0;i<layers.size();i++){Layer l=layers.get(i);String prefix=i+".";
                p.setProperty(prefix+"name",l.name);p.setProperty(prefix+"visible",""+l.visible);p.setProperty(prefix+"opacity",""+l.opacity);
                p.setProperty(prefix+"x",""+l.x);p.setProperty(prefix+"y",""+l.y);}
            out.putNextEntry(new ZipEntry("drawing.properties"));p.store(out,"UI Studio drawing");out.closeEntry();
            for(int i=0;i<layers.size();i++){out.putNextEntry(new ZipEntry("layer"+i+".png"));ImageIO.write(layers.get(i).image,"png",out);out.closeEntry();}
        }
        return bytes.toByteArray();
    }
    static UIDrawing decode(byte[] bytes) throws IOException {
        Map<String,byte[]> entries=new HashMap<>();long total=0;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry entry;
            while((entry=zip.getNextEntry())!=null){byte[] data=zip.readNBytes(5*1024*1024+1);total+=data.length;
                if(data.length>5*1024*1024||total>80*1024*1024||entries.size()>MAX_LAYERS)throw new IOException("Drawing archive is too large.");
                if(entries.put(entry.getName(),data)!=null)throw new IOException("Duplicate drawing entry.");}}
        try{
            Properties p=new Properties();p.load(new ByteArrayInputStream(Objects.requireNonNull(entries.get("drawing.properties"))));
            if(!"1".equals(p.getProperty("version")))throw new IOException("Unsupported drawing version.");
            UIDrawing d=new UIDrawing(Integer.parseInt(p.getProperty("width")),Integer.parseInt(p.getProperty("height")));
            int count=Integer.parseInt(p.getProperty("count"));if(count<1||count>MAX_LAYERS)throw new IOException("Invalid layer count.");d.layers.clear();
            for(int i=0;i<count;i++){String prefix=i+".";Layer l=new Layer(p.getProperty(prefix+"name","Layer"),d.width,d.height);
                if(l.name.length()>100)throw new IOException("Layer name is too long.");
                l.visible=Boolean.parseBoolean(p.getProperty(prefix+"visible"));l.opacity=Float.parseFloat(p.getProperty(prefix+"opacity"));
                l.x=Integer.parseInt(p.getProperty(prefix+"x"));l.y=Integer.parseInt(p.getProperty(prefix+"y"));
                if(!Float.isFinite(l.opacity)||l.opacity<0||l.opacity>1||Math.abs((long)l.x)>4096||Math.abs((long)l.y)>4096)throw new IOException("Invalid layer settings.");
                byte[] png=Objects.requireNonNull(entries.get("layer"+i+".png"));
                // Verify dimensions before decoding image pixels.
                try(javax.imageio.stream.ImageInputStream in=ImageIO.createImageInputStream(new ByteArrayInputStream(png))){
                    Iterator<javax.imageio.ImageReader> readers=ImageIO.getImageReaders(in);if(!readers.hasNext())throw new IOException("Invalid layer image.");
                    javax.imageio.ImageReader reader=readers.next();try{reader.setInput(in);
                        if(reader.getWidth(0)!=d.width||reader.getHeight(0)!=d.height)throw new IOException("Layer dimensions do not match canvas.");
                        BufferedImage source=reader.read(0);Graphics2D g=l.image.createGraphics();g.drawImage(source,0,0,null);g.dispose();
                    }finally{reader.dispose();}}
                d.layers.add(l);}
            d.selected=Integer.parseInt(p.getProperty("selected"));if(d.selected<0||d.selected>=count)throw new IOException("Invalid selected layer.");return d;
        }catch(RuntimeException ex){throw new IOException("Invalid drawing file.",ex);}
    }
    void save(Path path) throws IOException {write(path,encode());}
    static UIDrawing load(Path path) throws IOException {if(Files.size(path)>80*1024*1024)throw new IOException("Drawing file is too large.");return decode(Files.readAllBytes(path));}
    void exportPNG(Path path) throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(flatten(),"png",out);write(path,out.toByteArray());}
    private static void write(Path path,byte[] data) throws IOException {
        Path absolute=path.toAbsolutePath();Files.createDirectories(absolute.getParent());Path temp=Files.createTempFile(absolute.getParent(),"ui-",".tmp");
        try{Files.write(temp,data);Files.move(temp,absolute,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}
    }
}
