import java.awt.*;
import java.awt.image.*;
import java.util.*;

/** Software depth buffer: inverse depth is linear across projected triangles. */
final class MapDepthRenderer {
    BufferedImage image;double[] depth;Object[] owners;int[] pixels;int width,height;
    void render(int w,int h,java.util.List<MapEditor.Face> faces,Set<Object> selected){
        if(image==null||width!=w||height!=h){width=w;height=h;image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);pixels=((DataBufferInt)image.getRaster().getDataBuffer()).getData();depth=new double[w*h];owners=new Object[w*h];}
        Arrays.fill(pixels,0);Arrays.fill(depth,0);Arrays.fill(owners,null);
        for(MapEditor.Face f:faces)for(int i=1;i+1<f.screen.length;i++)triangle(f,f.screen[0],f.screen[i],f.screen[i+1]);
        for(MapEditor.Face f:faces){boolean highlight=f.owner!=null&&selected.contains(f.owner);for(int i=0;i<f.screen.length;i++)edge(f,f.screen[i],f.screen[(i+1)%f.screen.length],highlight);}
    }
    static double cross(double[] a,double[] b,double x,double y){return (b[0]-a[0])*(y-a[1])-(b[1]-a[1])*(x-a[0]);}
    static double at(double[] a,double[] b,double[] c,double x,double y){double area=cross(a,b,c[0],c[1]);if(Math.abs(area)<1e-10)return 0;double u=cross(b,c,x,y)/area,v=cross(c,a,x,y)/area,t=1-u-v;if(u< -1e-9||v< -1e-9||t< -1e-9)return 0;return u*a[2]+v*b[2]+t*c[2];}
    static double at(MapEditor.Face f,double x,double y){double q=0;for(int i=1;i+1<f.screen.length;i++)q=Math.max(q,at(f.screen[0],f.screen[i],f.screen[i+1],x,y));return q;}
    void triangle(MapEditor.Face f,double[] a,double[] b,double[] c){
        int minX=(int)Math.max(0,Math.floor(Math.min(a[0],Math.min(b[0],c[0])))),maxX=(int)Math.min(width-1,Math.ceil(Math.max(a[0],Math.max(b[0],c[0]))));
        int minY=(int)Math.max(0,Math.floor(Math.min(a[1],Math.min(b[1],c[1])))),maxY=(int)Math.min(height-1,Math.ceil(Math.max(a[1],Math.max(b[1],c[1]))));
        for(int y=minY;y<=maxY;y++)for(int x=minX;x<=maxX;x++){double q=at(a,b,c,x+.5,y+.5);int n=y*width+x;if(q>depth[n]+1e-12){depth[n]=q;pixels[n]=f.color.getRGB();owners[n]=f.owner;}}
    }
    void edge(MapEditor.Face f,double[] a,double[] b,boolean selected){
        double dx=b[0]-a[0],dy=b[1]-a[1],lo=0,hi=1;
        double[] starts={a[0],a[1]},directions={dx,dy},limits={width-1,height-1};
        for(int axis=0;axis<2;axis++){double d=directions[axis],s=starts[axis];if(Math.abs(d)<1e-10){if(s<0||s>limits[axis])return;}else{double t0=-s/d,t1=(limits[axis]-s)/d;lo=Math.max(lo,Math.min(t0,t1));hi=Math.min(hi,Math.max(t0,t1));}}
        if(lo>hi)return;int steps=Math.max(1,(int)Math.ceil(Math.max(Math.abs(dx),Math.abs(dy))*(hi-lo)));int radius=selected?1:0;
        for(int i=0;i<=steps;i++){double t=lo+(hi-lo)*i/steps;int x=(int)Math.round(a[0]+dx*t),y=(int)Math.round(a[1]+dy*t);double q=a[2]+(b[2]-a[2])*t;
            for(int oy=-radius;oy<=radius;oy++)for(int ox=-radius;ox<=radius;ox++){int px=x+ox,py=y+oy;if(px<0||px>=width||py<0||py>=height)continue;int n=py*width+px;
                // Never draw an outline over a different, nearer object.
                if(owners[n]!=f.owner)continue;
                double surface=at(f,px+.5,py+.5);
                if(surface<=0||surface+1e-9<depth[n])continue;
                if(selected)pixels[n]=Color.YELLOW.getRGB();else{int rgb=pixels[n];pixels[n]=0xff000000|((rgb>>16&255)*3/4)<<16|((rgb>>8&255)*3/4)<<8|(rgb&255)*3/4;}
            }
        }
    }
}
