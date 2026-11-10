import java.awt.*;
import java.awt.image.*;
import java.util.*;

/** Software depth buffer: inverse depth is linear across projected triangles. */
class SceneDepthRenderer {
    static class Face {
        final java.awt.geom.Path2D path;final double depth;final Color color;final Object owner;final double[][] screen;
        BufferedImage texture;boolean soft,outline=true;
        Face(java.awt.geom.Path2D p,double d,Color c,Object o,double[][] vertices){path=p;depth=d;color=c;owner=o;screen=vertices;}
        boolean transparent(){return color.getAlpha()<255||texture!=null||soft;}
    }
    int[] heads,lastFace,fragmentColor=new int[65536],fragmentNext=new int[65536];float[] fragmentDepth=new float[65536];int fragments,faceId;
    BufferedImage image;double[] depth;Object[] owners;int[] pixels;int width,height;
    static Face project(double[][] vertices,double cx,double cy,double focal,Color color,Object owner){
        java.util.List<double[]> clipped=new ArrayList<>();double[] a=vertices[vertices.length-1];
        for(double[] b:vertices){if((a[2]>=.1)!=(b[2]>=.1)){double t=(.1-a[2])/(b[2]-a[2]);double[] v=new double[5];for(int i=0;i<5;i++)v[i]=a[i]+t*(b[i]-a[i]);v[2]=.1;clipped.add(v);}if(b[2]>=.1)clipped.add(b);a=b;}
        if(clipped.size()<3)return null;double[][] screen=new double[clipped.size()][5];java.awt.geom.Path2D path=new java.awt.geom.Path2D.Double();double distance=0;
        for(int i=0;i<screen.length;i++){double[] v=clipped.get(i);double q=1/v[2],x=cx+v[0]*focal*q,y=cy-v[1]*focal*q;screen[i]=new double[]{x,y,q,v[3]*q,v[4]*q};distance+=v[2];if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}path.closePath();return new Face(path,distance/screen.length,color,owner,screen);
    }
    void render(int w,int h,java.util.List<? extends Face> faces,Set<Object> selected){
        if(image==null||width!=w||height!=h){width=w;height=h;image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);pixels=((DataBufferInt)image.getRaster().getDataBuffer()).getData();depth=new double[w*h];owners=new Object[w*h];heads=new int[w*h];lastFace=new int[w*h];}
        Arrays.fill(pixels,0);Arrays.fill(depth,0);Arrays.fill(owners,null);
        for(Face f:faces)if(!f.transparent())for(int i=1;i+1<f.screen.length;i++)triangle(f,f.screen[0],f.screen[i],f.screen[i+1]);
        for(Face f:faces)if(!f.transparent()&&f.outline){boolean highlight=f.owner!=null&&selected.contains(f.owner);for(int i=0;i<f.screen.length;i++)edge(f,f.screen[i],f.screen[(i+1)%f.screen.length],highlight);}
        for(Face f:faces)if(f.transparent()){transparent(faces);break;}
    }
    void transparent(java.util.List<? extends Face> faces){
        Arrays.fill(heads,-1);Arrays.fill(lastFace,-1);fragments=0;faceId=0;
        for(Face f:faces){faceId++;if(f.transparent())for(int i=1;i+1<f.screen.length;i++)triangle(f,f.screen[0],f.screen[i],f.screen[i+1]);}
        for(int n=0;n<pixels.length;n++)for(int f=heads[n];f>=0;f=fragmentNext[f])pixels[n]=blend(pixels[n],fragmentColor[f]);
    }
    static int blend(int background,int foreground){double a=(foreground>>>24)/255.0,b=(background>>>24)/255.0,out=a+b*(1-a);if(out<=0)return 0;int result=(int)Math.round(out*255)<<24;for(int shift:new int[]{16,8,0}){int c=(int)Math.round(((foreground>>shift&255)*a+(background>>shift&255)*b*(1-a))/out);result|=c<<shift;}return result;}
    void fragment(int pixel,double q,int color){
        if((color>>>24)==0)return;
        if(fragments==fragmentColor.length){int size=fragments*2;fragmentColor=Arrays.copyOf(fragmentColor,size);fragmentNext=Arrays.copyOf(fragmentNext,size);fragmentDepth=Arrays.copyOf(fragmentDepth,size);}
        int id=fragments++;fragmentColor[id]=color;fragmentDepth[id]=(float)q;int prev=-1,next=heads[pixel];while(next>=0&&fragmentDepth[next]<q){prev=next;next=fragmentNext[next];}fragmentNext[id]=next;if(prev<0)heads[pixel]=id;else fragmentNext[prev]=id;
    }
    static double cross(double[] a,double[] b,double x,double y){return (b[0]-a[0])*(y-a[1])-(b[1]-a[1])*(x-a[0]);}
    static double at(double[] a,double[] b,double[] c,double x,double y){double area=cross(a,b,c[0],c[1]);if(Math.abs(area)<1e-10)return 0;double u=cross(b,c,x,y)/area,v=cross(c,a,x,y)/area,t=1-u-v;if(u< -1e-9||v< -1e-9||t< -1e-9)return 0;return u*a[2]+v*b[2]+t*c[2];}
    static double at(Face f,double x,double y){double q=0;for(int i=1;i+1<f.screen.length;i++)q=Math.max(q,at(f.screen[0],f.screen[i],f.screen[i+1],x,y));return q;}
    void triangle(Face f,double[] a,double[] b,double[] c){
        double area=cross(a,b,c[0],c[1]);if(Math.abs(area)<1e-10)return;double inverse=1/area;
        int minY=(int)Math.max(0,Math.ceil(Math.min(a[1],Math.min(b[1],c[1]))-.5)),maxY=(int)Math.min(height-1,Math.floor(Math.max(a[1],Math.max(b[1],c[1]))-.5));
        double du=(b[1]-c[1])*inverse,dv=(c[1]-a[1])*inverse,dq=du*(a[2]-c[2])+dv*(b[2]-c[2]);
        double[][] vertices={a,b,c};boolean transparent=f.transparent();int solid=f.color.getRGB();
        for(int y=minY;y<=maxY;y++){
            double py=y+.5,left=Double.POSITIVE_INFINITY,right=Double.NEGATIVE_INFINITY;
            for(int edge=0;edge<3;edge++){double[] v=vertices[edge],w=vertices[(edge+1)%3];if(Math.abs(w[1]-v[1])<1e-12)continue;double t=(py-v[1])/(w[1]-v[1]);if(t>=0&&t<=1){double x=v[0]+t*(w[0]-v[0]);left=Math.min(left,x);right=Math.max(right,x);}}
            if(left>right)continue;int minX=(int)Math.max(0,Math.ceil(left-.50000001)),maxX=(int)Math.min(width-1,Math.floor(right-.49999999));
            double u=cross(b,c,minX+.5,py)*inverse,v=cross(c,a,minX+.5,py)*inverse,q=u*a[2]+v*b[2]+(1-u-v)*c[2];
            for(int x=minX;x<=maxX;x++,u+=du,v+=dv,q+=dq){int n=y*width+x;if(q<=depth[n]+1e-12)continue;
                if(!transparent){depth[n]=q;pixels[n]=solid;owners[n]=f.owner;continue;}
                if(lastFace[n]==faceId)continue;lastFace[n]=faceId;double t=1-u-v,tu=a.length>3?(u*a[3]+v*b[3]+t*c[3])/q:0,tv=a.length>4?(u*a[4]+v*b[4]+t*c[4])/q:0;int color=solid;
                if(f.texture!=null){int px=(int)Math.max(0,Math.min(f.texture.getWidth()-1,tu*f.texture.getWidth())),pyTexture=(int)Math.max(0,Math.min(f.texture.getHeight()-1,tv*f.texture.getHeight()));color=f.texture.getRGB(px,pyTexture);color=(color&0xffffff)|((color>>>24)*f.color.getAlpha()/255)<<24;}
                if(f.soft){double fade=Math.max(0,1-Math.hypot(tu*2-1,tv*2-1));color=(color&0xffffff)|((int)((color>>>24)*fade))<<24;}fragment(n,q,color);
            }
        }
    }
    void edge(Face f,double[] a,double[] b,boolean selected){
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
